#include "common.h"
#include "plugin.h"
#include "logger.h"
#include "iniparser.h"
#include <inas/inas_internal_proto.h>
#include <json/json.h>
#include "ingw.h"

using namespace std;
using namespace Json;

#define PLUGIN_NAME "ingw"
#define P_INGW_HDR_SIZE           (64)

static int ingw_on_init(t_plugin_handle * hplug);
static void ingw_on_finish(t_plugin_handle * hplug);
/* Inbound session */
static int ingw_on_iconnect(t_cli_ctx * cli);
static int ingw_on_idisconnect(t_cli_ctx * cli);
static int ingw_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int ingw_on_irecv(t_cli_ctx * cli, apr_time_t rtime, const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root);
/* Outbound session */
static int ingw_on_oconnect(t_cli_ctx * cli, char * conf);
static int ingw_on_odisconnect(t_cli_ctx * cli);
static int ingw_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int ingw_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize);
static int ingw_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics);

static t_plugin_handle g_plug = {
		PLUGIN_NAME, "",

		ingw_on_iconnect,
		ingw_on_idisconnect,
		ingw_on_iidle,
		ingw_on_irecv,
		NULL /* In */,
		NULL /* In */,

		ingw_on_oconnect,
		ingw_on_odisconnect,
		ingw_on_oidle,
		ingw_on_orecv_tokenize,
		ingw_on_orecv,
		NULL /* In */,
		NULL /* In */,
		NULL /* In */,

		ingw_on_init,
		ingw_on_finish
};

static struct {
	bool loaded;
	char sender[17];
} g_sess_conf[D_MAX_SESSION+1];

t_plugin_handle * ingw_get_handle(void) {
	return &g_plug;
}

static char * get_sender_name(apr_int32_t sig) {
    static char unknown[17] = "Unknown";
    if(sig > D_MAX_SESSION) {
        return unknown;
    }
    if(g_sess_conf[sig].loaded) {
       return g_sess_conf[sig].sender;
    }
    for(int i = 1; i <= D_MAX_SESSION; ++i) {
        if(g_sess_conf[i].loaded) {
            return g_sess_conf[i].sender;
        }
    }
    return unknown;
}
static int ingw_on_init(t_plugin_handle * hplug) {
	return IN_SUCCESS;
}

static void ingw_on_finish(t_plugin_handle * hplug) {
}

static void load_outbound_session_conf(apr_uint16_t sid, const char * conf) {
	if(!g_sess_conf[sid].loaded) {
		strncpy(g_sess_conf[sid].sender, conf, sizeof(g_sess_conf[sid].sender));
		g_sess_conf[sid].loaded = true;
	}
}

/* Inbound session */
static int ingw_on_iconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int ingw_on_idisconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int ingw_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time) {
	return IN_SUCCESS;
}

static int ingw_on_osend_rr_modify_data (t_cli_ctx * cli, char IN OUT * buf, apr_size_t IN size) {
	if(size >= 64) {
		char sender_name[16+1] = {0,};		
		snprintf(sender_name, sizeof(sender_name), "%-16s", get_sender_name(cli->sid));	
		memcpy(buf+16, sender_name, 16);
	}
	return size;
}

static int ingw_on_irecv(t_cli_ctx * cli, apr_time_t rtime, 
    const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root) {
    int rv = IN_SUCCESS;
	if(strncmp(P_HDR_OP_CMD, op, P_HDR_OP_CMD_SIZE) == 0) {
		Json::Value receiver = root.get("Receiver", "Unknown");
        if(!receiver.isString()) {
            LOGEK(tid, "[%s|%04d] JSON parsing\n", cli->name, cli->sid);
            return IN_FAIL;
        }
        const Json::Value ar_values = root["Values"];
        if(!ar_values.isArray()) {
        	LOGEK(tid, "[%s|%04d] JSON Values parsing\n", cli->name, cli->sid);
        	return IN_FAIL;
        }
        if(strncmp("NOWCALLGWN", receiver.asCString(), 10) == 0) {
        	char out[P_BUFFER_SIZE] = {0,};        	
        	if(ar_values.size() < 3) {
        		LOGEK(tid, "[%s|%04d] JSON invalid value size\n", cli->name, cli->sid);
            	return IN_FAIL;
        	}
            char ar_callings[512] = {0,}, * ar_callings_ptr = ar_callings;
        	for(apr_size_t i = 0; i < ar_values.size(); ++i) {
            	if(!ar_values.get(i, "").isString()) {
            		LOGEK(tid, "[%s|%04d] JSON invalid value type\n", cli->name, cli->sid);
                	return IN_FAIL;
            	}
                if(i != 0 && i != ar_values.size()-1) {
                    if(i != 1)
                        *ar_callings_ptr++ = ',';
                    *ar_callings_ptr++ = '\"';
                    int len = in_strncpy(ar_callings_ptr, ar_values.get(i,"").asCString(), 32);
                    ar_callings_ptr += len;
                    *ar_callings_ptr++ = '\"';
                }
        	}
            char ingw_seq[P_HDR_SEQ_SIZE+1] = {0,};
            make_outnode_sequence_uint64(ingw_seq, sizeof(ingw_seq), tid, sid);
        	int hdr_len = snprintf(out, sizeof(out), "%-16s%-16s%-16s%-16s",
        			"GW2_SND", "NOT-ASSIGN", receiver.asCString(), "0");
            int body_len = snprintf(out+hdr_len, P_BUFFER_SIZE-hdr_len, 
                "{"
                "\"Tid\":%s,"
                "\"Svc\":1,"
                    "\"Msg\":{"
                    "\"Cmd\":%s,"
                    "\"Calling\":[%s],"
                    "\"Called\":\"%s\""
                    "}"
                "}", ingw_seq, ar_values[0].asCString(), ar_callings, ar_values[ar_values.size()-1].asCString());
        	char str_body_len[17] = {0,};
        	in_itoa(str_body_len, body_len);
            char str_hdr_temp[32] = {0,};
        	snprintf(str_hdr_temp, sizeof(str_hdr_temp), "%-16s", str_body_len);
        	memcpy(out+48, str_hdr_temp, 16);
        	if(g_plug.osend_rr(out, hdr_len+body_len, 1, ingw_on_osend_rr_modify_data) == IN_SUCCESS) {
        		LOGI1K(tid, "[%s|%04d] Outbound send. [%d]\n%s\n", cli->name, cli->sid, hdr_len+body_len, out);
        	}
        	else {
        		LOGEK(tid, "[%s|%04d] Outbound send. [%d]\n%s\n", cli->name, cli->sid, hdr_len+body_len, out);
        	}
        }
	}
	return rv;
}
/* Outbound session */
static int ingw_on_oconnect(t_cli_ctx * cli, char * conf) {
	char hdr[P_INGW_HDR_SIZE+1] = {0,};
	char receiver[16+1] = {0,};
	snprintf(receiver, sizeof(receiver), "NEWMD_%-10.10s", cli->name);
	load_outbound_session_conf(cli->sid, conf);
	snprintf(hdr, sizeof(hdr), "%-16s%-16s%-16s%-16s",
			"GW2_REG", g_sess_conf[cli->sid].sender, receiver, "0");
	if(g_plug.osend(cli, hdr, P_INGW_HDR_SIZE, 0) == IN_SUCCESS) {
		LOGI1("[%s|%04d] Login request\n%s\n", cli->name, cli->sid, hdr);
		return IN_SUCCESS;
	}
	else {
		LOGE("[%s|%04d] Login request failed\n%s\n", cli->name, cli->sid, hdr);
	}
	return IN_ERR_PLUGIN_FORCE_CLOSE; /* Session closed */
}
static int ingw_on_odisconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int ingw_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time) {
	char hdr[P_INGW_HDR_SIZE+1] = {0,};
	char receiver[16+1] = {0,};
	snprintf(receiver, sizeof(receiver), "NEWMD_%-10.10s", cli->name);
	snprintf(hdr, sizeof(hdr), "%-16s%-16s%-16s%-16s",
			"GW2_HEARTBEAT", g_sess_conf[cli->sid].sender, receiver, "0");
	if(g_plug.osend(cli, hdr, P_INGW_HDR_SIZE, 0) == IN_SUCCESS) {
		LOGD("[%s|%04d] Hb request\n%s\n", cli->name, cli->sid, hdr);
		return IN_SUCCESS;
	}
	else {
		LOGE("[%s|%04d] Hb request failed\n%s\n", cli->name, cli->sid, hdr);
	}
	return IN_FAIL;
}
static int ingw_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize) {
    apr_int32_t rv = IN_SUCCESS;
	char * buf_ptr = buf;
	apr_uint32_t want = P_INGW_HDR_SIZE;
	apr_size_t readen = want;
	rv = in_socket_recv(csock, (char *)buf_ptr, &readen);
	if(rv != APR_SUCCESS || readen != want) {
		rv = IN_FAIL; goto GT_EXIT;
	}
	*psize = P_INGW_HDR_SIZE;
	*(buf_ptr+P_INGW_HDR_SIZE) = 0;
	want = in_atoi(buf_ptr+(P_INGW_HDR_SIZE-16));
	if(want > 0) {
		readen = want;
		rv = in_socket_recv(csock, buf_ptr+P_INGW_HDR_SIZE, &readen);
		if(rv != APR_SUCCESS || readen != want) {
			rv = IN_FAIL; goto GT_EXIT;
		}
		*psize += readen;
	}
GT_EXIT:
	return rv;
}

#if 0
static char * get_xml_value(char * dst, int size, const char * src, const char * key) {
	char * ptr = (char *)src;
	if(ptr) {
		ptr = strstr(ptr, key);
		if(ptr) {
			char * begin = strchr(ptr, '>');
			char * end = strchr(ptr, '<');
			if(begin && end) {
				++begin;
				const int len = (int)((apr_uint64_t)end-(apr_uint64_t)begin);
				if(len < size-1) {
					memcpy(dst, begin, len);
					dst[len] = 0;
					return end;
				}
			}
		}
	}
	return NULL;
}
#endif

static int ingw_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics) {
	int rv = IN_SUCCESS;
	if(size == P_INGW_HDR_SIZE) {
		g_plug.update_state(cli, 'A');
        LOGD("[%s|%04d] Outbound recv. [%d]\n%s\n", cli->name, cli->sid, size, buf);
		goto GT_EXIT;
	}
    if(strncmp(buf+16, "NEWMD", 5) == 0) {
        try {
            Json::Value root;
            Json::Reader reader;
            bool parsingSuccessful = reader.parse(buf+64, buf+size, root, false);
            if (!parsingSuccessful) {
                LOGE("[%s|%04d] JSON parsing\n", cli->name, cli->sid);
                goto GT_RECV_FAIL_EXIT;
            }
			char lastStatus = 0;
			Json::Value temp;
            const char * sysStatus = root["Status"].asCString();
			const char * svcStatus = (sysStatus) ? sysStatus : "D";
			temp = root["NOWCALLGWN"];
			if(temp.isString()) {
				svcStatus = temp.asCString();
			}
            if(sysStatus && svcStatus) {
				if(sysStatus[0] == 'A' || sysStatus[0] == 'N') {
					if(svcStatus[0] == 'A' || svcStatus[0] == 'N') {
						lastStatus = 'A';
					} else if(svcStatus[0] == 'D') {
						lastStatus = 'D';
					} else {
						lastStatus = 'B';
					}
				} else if(sysStatus[0] == 'D') {
					if(svcStatus[0] == 'A' || svcStatus[0] == 'D' || svcStatus[0] == 'N') {
						lastStatus = 'D';
					} else {
						lastStatus = 'B';
					}
				} else {
					lastStatus = 'B';
				}
                if(lastStatus == 'A') {
                    g_plug.update_state(cli, 'A');
                    LOGD("[%s|%04d] Hb recv with Active. [%d]\n%s\n", cli->name, cli->sid, size, buf);
                } else if(lastStatus == 'D') {
                    g_plug.update_state(cli, 'D');
                    LOGD("[%s|%04d] Hb recv with DR. [%d]\n%s\n", cli->name, cli->sid, size, buf);
                } else {
                    g_plug.update_state(cli, 'B');
                    LOGD("[%s|%04d] Hb recv with Block. [%d]\n%s\n", cli->name, cli->sid, size, buf);
                }
            }
        } catch ( const std::exception &e ) {
            LOGE("[%s|%04d] Unexpected exception caugth:%s\n", cli->name, cli->sid, e.what());
            goto GT_RECV_FAIL_EXIT;
        }	
	} else if(strncmp(buf+16, "NOWCALLGWN", 10) == 0) {
        try {
            Json::Value root;
            Json::Reader reader;
            bool parsingSuccessful = reader.parse(buf+64, buf+size, root, false);
            if (!parsingSuccessful) {
                LOGE("[%s|%04d] JSON parsing\n", cli->name, cli->sid);
                goto GT_RECV_FAIL_EXIT;
            }                        
            Json::Value arProviders = root["Msg"].get("Provider", Json::objectValue);
            if(!arProviders.isArray() || arProviders.size() < 1) {
                goto GT_RECV_FAIL_EXIT;
            }
            apr_uint64_t ingw_seq = root["Tid"].asUInt64();
            char str_ingw_seq[32] = {0,};
            int str_ingw_seq_len = in_u64toa(str_ingw_seq, ingw_seq);
            char proto_tid[P_HDR_TID_SIZE+1] = {0,};
            apr_uint16_t proto_sid = 0;
            parse_outnode_sequence_uint64(str_ingw_seq, str_ingw_seq_len, proto_tid, &proto_sid);
            LOGI1K(proto_tid, "[%s|%04d] Outbound recv. [%d]\n%s\n", cli->name, cli->sid, size, buf);
            char out[P_BUFFER_SIZE] = {0, };
            apr_int32_t sendsize = make_cmd_hdr(out, sizeof(out),
                    "Cmd" P_HDR_OP_RSP_TAG, proto_tid, proto_sid, "UNKNOWN", "INGW", 
                    "{"
                    "\"Result\":0,"
                    "\"Values\":[\"%s\"]"
                    "}", arProviders[0].asCString());
            if(sendsize <= 0) {
                rv = IN_FAIL; goto GT_SEND_FAIL_EXIT;
            }
            if(g_plug.isend_sig(proto_sid, proto_tid, out, sendsize) != IN_SUCCESS) {
                rv = IN_FAIL; goto GT_SEND_FAIL_EXIT;
            }
            return IN_SUCCESS;
        } catch ( const std::exception &e ) {
            LOGE("[%s|%04d] Unexpected exception caugth:%s\n", cli->name, cli->sid, e.what());
            goto GT_RECV_FAIL_EXIT;
        }
	}
    else {
        LOGD("[%s|%04d] Outbound recv. [%d]\n%s\n", cli->name, cli->sid, size, buf);
    }    
    goto GT_EXIT;
GT_RECV_FAIL_EXIT:
    LOGE("[%s|%04d] Outbound recv. [%d]\n%s\n", cli->name, cli->sid, size, buf);
	return rv;
GT_SEND_FAIL_EXIT:
	return rv;    
GT_EXIT:
	return rv;    
}
