#include "common.h"
#include "plugin.h"
#include "logger.h"
#include "iniparser.h"
#include <inas/inas_internal_proto.h>
#include <json/json.h>
#include "inmg.h"

using namespace std;
using namespace Json;

#define PLUGIN_NAME "inmg"
#define P_INGW_HDR_SIZE           (64)

static int inmg_on_init(t_plugin_handle * hplug);
static void inmg_on_finish(t_plugin_handle * hplug);
/* Inbound session */
static int inmg_on_iconnect(t_cli_ctx * cli);
static int inmg_on_idisconnect(t_cli_ctx * cli);
static int inmg_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int inmg_on_irecv(t_cli_ctx * cli, apr_time_t rtime, const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root);
/* Outbound session */
static int inmg_on_oconnect(t_cli_ctx * cli, char * conf);
static int inmg_on_odisconnect(t_cli_ctx * cli);
static int inmg_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int inmg_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize);
static int inmg_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics);

static t_plugin_handle g_plug = {
		PLUGIN_NAME, "",

		inmg_on_iconnect,
		inmg_on_idisconnect,
		inmg_on_iidle,
		inmg_on_irecv,
		NULL /* In */,
		NULL /* In */,

		inmg_on_oconnect,
		inmg_on_odisconnect,
		inmg_on_oidle,
		inmg_on_orecv_tokenize,
		inmg_on_orecv,
		NULL /* In */,
		NULL /* In */,
		NULL /* In */,

		inmg_on_init,
		inmg_on_finish
};

static struct {
	bool loaded;
	char sender[17];
} g_sess_conf[D_MAX_SESSION+1];

static char g_inmg_text[128+1];

t_plugin_handle * inmg_get_handle(void) {
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

static void load_ini_config(const char* conf_path) {
    LOGI("Loading common configuration: Configuration path=[%s]\n", conf_path);
    dictionary* inmg_ini_dictionary = NULL;
    if (conf_path == NULL) {
        LOGW("Configuration Path is NULL\n");
        return;
    }
    inmg_ini_dictionary = iniparser_load(conf_path);
    if (inmg_ini_dictionary == NULL) {
        fprintf((FILE*)stderr, "Can not load configuration file : %s\n", conf_path);
        LOGE("Can not load configuration file : %s\n", conf_path);
    } else {
        /* Load the g_inmg_text from the config file */
        strncpy(g_inmg_text, 
            iniparser_getstring(inmg_ini_dictionary, (char*)"inmg:text", (char*)"<![CDATA[<]]>통화가능 알림<![CDATA[>]]>%s 번호로 [통화] 버튼을 누르시면 연결됩니다."), 
            sizeof(g_inmg_text));
        iniparser_freedict(inmg_ini_dictionary);
        LOGI("Loaded Common Configuration: INMG TEXT [%s]\n", g_inmg_text);
    }
}

static int inmg_on_init(t_plugin_handle * hplug) {
    load_ini_config(hplug->conf_path);    
	return IN_SUCCESS;
}

static void inmg_on_finish(t_plugin_handle * hplug) {
}

static void load_outbound_session_conf(apr_uint16_t sid, const char * conf) {
	if(!g_sess_conf[sid].loaded) {
		strncpy(g_sess_conf[sid].sender, conf, sizeof(g_sess_conf[sid].sender));
		g_sess_conf[sid].loaded = true;
	}
}

/* Inbound session */
static int inmg_on_iconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int inmg_on_idisconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int inmg_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time) {
	return IN_SUCCESS;
}

static int inmg_on_irecv(t_cli_ctx * cli, apr_time_t rtime,
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
        if(strncmp("NOWCALL", receiver.asCString(), 7) == 0) {
        	char out[P_BUFFER_SIZE] = {0,};        	
        	if(ar_values.size() < 3) {
        		LOGEK(tid, "[%s|%04d] JSON invalid value size\n", cli->name, cli->sid);
            	return IN_FAIL;
        	}
            int ar_callings_num = 0;
            char ar_callings[32][32];
        	for(apr_size_t i = 0; i < ar_values.size() && ar_callings_num < 32; ++i) {
            	if(!ar_values.get(i, "").isString()) {
            		LOGEK(tid, "[%s|%04d] JSON invalid value type\n", cli->name, cli->sid);
                	return IN_FAIL;
            	}
                if(i != 0 && i != ar_values.size()-1) {
                    if(in_strncpy(ar_callings[ar_callings_num], ar_values.get(i,"").asCString(), 32)) {
                        ++ar_callings_num;
                    }
                }
        	}
            const char * called = ar_values[ar_values.size()-1].asCString();
            char text[256] = {0,};
            snprintf(text, sizeof(text), g_inmg_text, called);
            for(int i = 0; i < ar_callings_num; ++i) {                                
                const char * calling = (const char *)ar_callings[i];
                char inmg_seq[P_HDR_SEQ_SIZE+1] = {0,};
                make_outnode_sequence_uint64(inmg_seq, sizeof(inmg_seq), tid, sid*10+i);
                int hdr_len = snprintf(out, sizeof(out), "%-16s%-16s%-16s%-16s",
                        "GW2_SND", get_sender_name(cli->sid), receiver.asCString(), "0");
                int body_len = snprintf(out+hdr_len, P_BUFFER_SIZE-hdr_len, 
                    "{"
                    "\"Tid\":%s,"
                    "\"Svc\":1,"
                        "\"Msg\":{"
                        "\"Cmd\":1,"
                        "\"Calling\":\"%s\","
                        "\"Called\":\"%s\","
                        "\"Callback\":\"%s\","
                        "\"Text\":\"%s\","
                        "\"WantCharging\":\"0\""
                        "}"
                    "}", inmg_seq, calling, calling, called, text);
                char str_body_len[17] = {0,};
                in_itoa(str_body_len, body_len);
                char str_hdr_temp[32] = {0,};
                snprintf(str_hdr_temp, sizeof(str_hdr_temp), "%-16s", str_body_len);
                memcpy(out+48, str_hdr_temp, 16);
                if(g_plug.osend_rr(out, hdr_len+body_len, 1, NULL) == IN_SUCCESS) {
                    LOGI1K(tid, "[%s|%04d] Outbound send. [%d]\n%s\n", cli->name, cli->sid, hdr_len+body_len, out);
                }
                else {
                    LOGEK(tid, "[%s|%04d] Outbound send. [%d]\n%s\n", cli->name, cli->sid, hdr_len+body_len, out);
                }
            }
        }
	}
	return rv;
}
/* Outbound session */
static int inmg_on_oconnect(t_cli_ctx * cli, char * conf) {
	char hdr[P_INGW_HDR_SIZE+1] = {0,};
	load_outbound_session_conf(cli->sid, conf);
	snprintf(hdr, sizeof(hdr), "%-16s%-16s%-16s%-16s",
			"GW2_REG", g_sess_conf[cli->sid].sender, "NEWMD", "0");
	if(g_plug.osend(cli, hdr, P_INGW_HDR_SIZE, 0) == IN_SUCCESS) {
		LOGI1("[%s|%04d] Login request\n%s\n", cli->name, cli->sid, hdr);
		return IN_SUCCESS;
	}
	else {
		LOGE("[%s|%04d] Login request failed\n%s\n", cli->name, cli->sid, hdr);
	}
	return IN_ERR_PLUGIN_FORCE_CLOSE; /* Session closed */
}
static int inmg_on_odisconnect(t_cli_ctx * cli) {
	return IN_SUCCESS;
}
static int inmg_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time) {
	char hdr[P_INGW_HDR_SIZE+1] = {0,};
	snprintf(hdr, sizeof(hdr), "%-16s%-16s%-16s%-16s",
			"GW2_HEARTBEAT", g_sess_conf[cli->sid].sender, "NEWMD", "0");
	if(g_plug.osend(cli, hdr, P_INGW_HDR_SIZE, 0) == IN_SUCCESS) {
		LOGD("[%s|%04d] Hb request\n%s\n", cli->name, cli->sid, hdr);
		return IN_SUCCESS;
	}
	else {
		LOGE("[%s|%04d] Hb request failed\n%s\n", cli->name, cli->sid, hdr);
	}
	return IN_FAIL;
}
static int inmg_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize) {
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

static int inmg_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics) {
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
            const char * state = root["Status"].asCString();
            if(state) {
                if(state[0] == 'A') {
                    g_plug.update_state(cli, 'A');
                    LOGD("[%s|%04d] Hb recv with active. [%d]\n%s\n", cli->name, cli->sid, size, buf);
                } else {
                    g_plug.update_state(cli, 'B');
                    LOGD("[%s|%04d] Hb recv with block. [%d]\n%s\n", cli->name, cli->sid, size, buf);
                }
            }
        } catch ( const std::exception &e ) {
            LOGE("[%s|%04d] Unexpected exception caugth:%s\n", cli->name, cli->sid, e.what());
            goto GT_RECV_FAIL_EXIT;
        }
    } else if(strncmp(buf+16, "NOWCALL", 7) == 0) {
        try {
            Json::Value root;
            Json::Reader reader;
            bool parsingSuccessful = reader.parse(buf+64, buf+size, root, false);
            if (!parsingSuccessful) {
                LOGE("[%s|%04d] JSON parsing\n", cli->name, cli->sid);
                goto GT_RECV_FAIL_EXIT;
            }
            apr_uint64_t ingw_seq = root["Tid"].asUInt64();
            char str_ingw_seq[32] = {0,};
            int str_ingw_seq_len = in_u64toa(str_ingw_seq, ingw_seq);
            char proto_tid[P_HDR_TID_SIZE+1] = {0,};
            apr_uint16_t proto_sid = 0;
            parse_outnode_sequence_uint64(str_ingw_seq, str_ingw_seq_len, proto_tid, &proto_sid);
            LOGI1K(proto_tid, "[%s|%04d] Outbound recv. [%d]\n%s\n", cli->name, cli->sid, size, buf);
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
GT_EXIT:
	return rv;    
}
