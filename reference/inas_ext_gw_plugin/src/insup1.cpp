#include "common.h"
#include "util.h"
#include "version.h"
#include "iniparser.h"
#include "plugin.h"
#include "logger.h"
#include <inas/inas_internal_proto.h>
#include <json/json.h>
#include "insup1.h"
#include "insup_protocol.h"
#include <string>

#define PLUGIN_NAME "insup1"

static int insup1_on_init(t_plugin_handle * hplug);
static void insup1_on_finish(t_plugin_handle * hplug);
/* Inbound session */
static int insup1_on_iconnect(t_cli_ctx * cli);
static int insup1_on_idisconnect(t_cli_ctx * cli);
static int insup1_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int insup1_on_irecv(t_cli_ctx * cli, apr_time_t rtime, const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root);
/* Outbound session */
static int insup1_on_oconnect(t_cli_ctx * cli, char * conf);
static int insup1_on_odisconnect(t_cli_ctx * cli);
static int insup1_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time);
static int insup1_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize);
static int insup1_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics);

static t_plugin_handle g_plug = {
		PLUGIN_NAME, "",

		insup1_on_iconnect,
		insup1_on_idisconnect,
		insup1_on_iidle,
		insup1_on_irecv,
		NULL /* In */,
		NULL /* In */,

		insup1_on_oconnect,
		insup1_on_odisconnect,
		insup1_on_oidle,
		insup1_on_orecv_tokenize,
		insup1_on_orecv,
		NULL /* In */,
		NULL /* In */,
		NULL /* In */,

		insup1_on_init,
		insup1_on_finish
};

t_plugin_handle * insup1_get_handle(void) {
	return &g_plug;
}

static struct {
	bool loaded;

    apr_byte_t vca;
    apr_byte_t inas_id;
    apr_byte_t module_name;
    apr_byte_t net_connect_id;
    apr_byte_t ip[4];
} g_sess_conf[D_MAX_SESSION+1];

static struct {
    apr_pool_t * pool;
    apr_thread_mutex_t * mymutex;
    char conf_path[P_BUFFER_SIZE];
    char src_id[P_HDR_SRC_SIZE+1];
    char is_use_heartbeat;
} g_conf;



static void load_ini_config(const char* conf_path) {
    LOGI("Loading common configuration: Configuration path=[%s]\n", conf_path);
    dictionary* insup_ini_dictionary = NULL;
    if (conf_path == NULL) {
        LOGW("Configuration Path is NULL\n");
        return;
    }
    insup_ini_dictionary = iniparser_load(conf_path);
    if (insup_ini_dictionary == NULL) {
        fprintf((FILE*)stderr, "Can not load configuration file : %s\n", conf_path);
        LOGE("Can not load configuration file : %s\n", conf_path);
    } else {
        /* Load the srcid from the config file */
        char* srcid = iniparser_getstring(insup_ini_dictionary, (char*)"proc:srcid", (char*)P_HDR_UNKWON_SRC_ID);


        // Mutex lock to setup the global variable, session configurations
        LOCK(g_conf.mymutex);

        strncpy(g_conf.src_id, srcid, P_HDR_SRC_SIZE);
        strncpy(g_conf.conf_path, conf_path, sizeof(g_conf.conf_path));
        g_conf.is_use_heartbeat = IS_USE_HEARTBEAT;
        UNLOCK(g_conf.mymutex);

        iniparser_freedict(insup_ini_dictionary);

        LOGI("Loaded Common Configuration: SRCID [%s]\n", g_conf.src_id);

    }
}

/* This function will parse the information from the cfg file [outbound_plugin] 
 * The current format is
 * {INASID},{NETID},{LOGIN_IP}
 */
IN_DECLARE(void) parse_insup1_outbound_plugin_info(t_cli_ctx * cli, const char* outbound_plugin_info) {
    char inas_id[4] = {0,};         // Max 1 byte (255)
    char net_connect_id[4] = {0,};  // Max 5
    apr_byte_t ip[4] = {0,};
    char ip_char[P_BUFFER_SIZE] = {0,};

        
    
    /* The first split is {INASID} */
    char* split_ptr = (char*)strchr(outbound_plugin_info, ',');
    char* previous_ptr;
    int split_len;


    if (split_ptr != NULL ) {

        split_len = split_ptr - outbound_plugin_info;
        memcpy(inas_id, outbound_plugin_info, split_len);
    } else {
        strncpy(inas_id, INSUP_DEFAULT_INAS_ID, sizeof(inas_id));
        LOGW("WARNING: Malform Outbound plugin information, comma is expected, Current value: [%s]\n", outbound_plugin_info);
    }

    // The second split is {NET_ID} */
    previous_ptr = split_ptr;
    split_ptr = strchr(split_ptr+1, ',');
    if (previous_ptr != NULL && split_ptr != NULL) {
        split_len = split_ptr - previous_ptr - 1;

        memcpy(net_connect_id, previous_ptr+1, split_len);

    } else {
        strncpy(net_connect_id, INSUP_DEFAULT_NET_ID, sizeof(net_connect_id));
        LOGW("WARNING: Malform Outbound plugin information, comma is expected, Current value: [%s]\n", outbound_plugin_info);
    }

    // The third split is {IP} */
    if (split_ptr != NULL) {

        previous_ptr = split_ptr;
        for(int i = 0 ; i < 4; i++) {
            split_ptr = strchr(previous_ptr+1, '.');
            if (split_ptr != NULL) { // Not the last IP
                if (i < 3) {

                    strncpy(ip_char, previous_ptr + 1, split_ptr - previous_ptr);
                    ip_char[split_ptr - previous_ptr - 1] = '\0';
                    ip[i] = in_atoi(ip_char);

                    previous_ptr = split_ptr;

                } else {
                    apr_byte_t default_ip[4] = INSUP_DEFAULT_IP;
                    memcpy(ip, default_ip, sizeof(ip));
                    LOGW("WARNING: Malform Outbound plugin information, IP format is not valid. Too many dots. Current value: [%s]\n", outbound_plugin_info);
                    break;
                }
                 
            } else {    // This is the last split of IPv4
                if (i == 3) {
                    strncpy(ip_char, previous_ptr + 1, strlen(previous_ptr+1));
                    ip_char[strlen(previous_ptr+1)] = '\0';
                    ip[i] = in_atoi(ip_char);

                } else {
                    apr_byte_t default_ip[4] = INSUP_DEFAULT_IP;
                    memcpy(ip, default_ip, sizeof(ip));
                    LOGW("WARNING: Malform Outbound plugin information, IP format is not valid. Need more dots. Current value: [%s]\n", outbound_plugin_info);
                    break;
                }

                
                    

            }
        }
    } else {
        apr_byte_t default_ip[4] = INSUP_DEFAULT_IP;
        memcpy(ip, default_ip, sizeof(ip));
    }


    LOCK(g_conf.mymutex);
    g_sess_conf[cli->sid].inas_id = g_sess_conf[cli->sid].module_name = in_atoi(inas_id);
    g_sess_conf[cli->sid].net_connect_id = in_atoi(net_connect_id);
    g_sess_conf[cli->sid].vca = INSUP_DEFAULT_VCA;
    memcpy(g_sess_conf[cli->sid].ip, ip, sizeof(g_sess_conf[cli->sid].ip));
    g_sess_conf[cli->sid].loaded = true;
    UNLOCK(g_conf.mymutex);

    LOGI1("[%s|%04d] Loaded configuration from outbound plugin info [%s]\n\tVCA[0x%X]\n\tINASID[0x%X]\n\tMODULE NAME[0x%X]\n\tNET CONNECT ID[0x%X]\n\tIP[%d.%d.%d.%d]\n", cli->name, cli->sid, outbound_plugin_info, g_sess_conf[cli->sid].vca, g_sess_conf[cli->sid].inas_id, g_sess_conf[cli->sid].module_name, g_sess_conf[cli->sid].net_connect_id, g_sess_conf[cli->sid].ip[0], g_sess_conf[cli->sid].ip[1], g_sess_conf[cli->sid].ip[2], g_sess_conf[cli->sid].ip[3]);

}





static int insup1_on_init(t_plugin_handle * hplug) {
    apr_pool_t * pool;
    apr_thread_mutex_t * mutex;
    apr_status_t rv;

    LOGI("Initiating APR pool & mutex\n");
    apr_pool_create(&pool, NULL);

    rv = apr_thread_mutex_create(&mutex, APR_THREAD_MUTEX_DEFAULT, pool);
    if (rv != APR_SUCCESS) {
        LOGW("Error initating APR mutex, Can not execute thread safe\n");
    }

    // Setup the common values
    LOCK(mutex);
    g_conf.mymutex = mutex;
    g_conf.pool = pool;
    UNLOCK(mutex);


    // Load ocmmon configurations from the ini file
    load_ini_config(hplug->conf_path);


	return IN_SUCCESS;
}

static void insup1_on_finish(t_plugin_handle * hplug) {
    apr_status_t rv;

    rv = apr_thread_mutex_destroy(g_conf.mymutex);
    if (rv != APR_SUCCESS) {
        LOGW("The APR mutex failed to destroy, maybe there can be a leak\n");
    }

    apr_pool_destroy(g_conf.pool);
}

/* Inbound session */
static int insup1_on_iconnect(t_cli_ctx * cli) {
    LOGV("[%s|%04d] Inbound Connected\n", cli->name, cli->sid);
	return IN_SUCCESS;
}
static int insup1_on_idisconnect(t_cli_ctx * cli) {
    LOGV("[%s|%04d] Inbound Disconnected\n", cli->name, cli->sid);
	return IN_SUCCESS;
}
static int insup1_on_iidle(t_cli_ctx * cli, apr_time_t elasped_time) {
    LOGV("[%s|%04d] Inbound Idle\n", cli->name, cli->sid);
	return IN_SUCCESS;
}
static int insup1_on_irecv(t_cli_ctx * cli, apr_time_t rtime, const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root) {
    LOGV("[%s|%04d] Received inbound message: OP[%s], TID[%s], SID[0x%X], SVCID[%s], SRCID[%s]\n", cli->name, cli->sid, op, tid, sid, svcid, srcid);
    char out[P_BUFFER_SIZE] = {0,};
    char body[P_BUFFER_SIZE] = {0,};
    char* current_ptr;



    /*
     * JSON body specification
     * [Example]
     * {
     *   "API": "APIName",
     *   "Values": ["The parameters to invoke the API", "Param2"],
     * }
     *
     * [Specification]
     * - API: The name of the API to invoke (Mandatory)
     * - Values: The array to send as parameters to the API (Optional), default is a blank JSON array
     *
     */


    if (strcmp(op, P_HDR_OP_CMD) == 0) {


        current_ptr = body;
        const Json::Value api_name = root["Api"];

        char insup_sid[INSUP_HEADER_SESSION_ID_SIZE+1] = {0,};


        if (!api_name.isString()) {
            LOGEK(tid, "[%s|%04d] JSON API parse error (not exists or not a string)\n", cli->name, cli->sid);
            return IN_FAIL;
        }


        const char* operation_name = api_name.asCString();
        apr_uint16_t operation_name_size = strlen(operation_name);
        apr_size_t size = 0;


        *current_ptr++ = 2; //Parameter count= DB_OPERATION_NAME, SQL_INPUT


        current_ptr = generate_insup_db_operation_name_parameter(current_ptr, size, operation_name, operation_name_size);


        LOGD("[%s|%04d] Parameter 1 [DB_OPERATION_NAME]: OperationName[%s], Size[%d]\n", cli->name, cli->sid, operation_name, operation_name_size);

        const Json::Value ar_values = root.get("Values", Json::Value(Json::arrayValue));
        if (!ar_values.isArray()) {
            LOGEK(tid, "[%s|%04d] JSON Values parse error (not an array)\n", cli->name, cli->sid);
            return IN_FAIL;
        }


        /*
         * DB_INPUT protocol
         * Parameter count          (1 byte)
         * First parameter size = n (2 byte)    //MAX: 1024 bytes
         * First parameter value    (n byte)
         * second parameter size = m(2 byte)    //MAX: 1024 bytes
         * Second parameter value   (m byte)
         * ....
         */

        current_ptr = generate_insup_sql_input_parameter(current_ptr, size, &ar_values);

        LOGD("[%s|%04d] Parameter 2 [SQL_INPUT]: Size[%d]\n", cli->name, cli->sid, size);



        ext_gw_tid_to_insup_sid(insup_sid, tid, sid, svcid);
        

        // Setup INSUP2 header
        t_insup_message_header hdr;
        setup_default_insup_hdr(&hdr);

        hdr.msg_len = current_ptr - body;
        hdr.msg_len = SWAP_IF_LITTLE_ENDIAN(hdr.msg_len);
        hdr.msg_code = DB_QUERY_REQUEST;
        hdr.inas_id = g_sess_conf[cli->sid].inas_id;
        memcpy(hdr.session_id, insup_sid, INSUP_HEADER_SESSION_ID_SIZE);



        memcpy(out,                 &hdr, sizeof(hdr));
        memcpy(out+sizeof(hdr),     body, current_ptr - body);

        LOGV("[%s|%04d] Input hdr size[%d], parameter Size[%d]\n", cli->name, cli->sid, sizeof(hdr), current_ptr - body);
        if ( g_plug.osend_rr(out, sizeof(hdr) + current_ptr - body, 1, NULL) == IN_SUCCESS) {
            LOGD("[%s|%04d] Sending DB query request\n", cli->name, cli->sid);
            return IN_SUCCESS;
        } else {
            LOGE("[%s|%04d] DB query failed\n", cli->name, cli->sid);
            return IN_ERR_PLUGIN_FORCE_CLOSE;
        }
    

    } else {
    }


	return IN_SUCCESS;
}
/* Outbound session */
static int insup1_on_oconnect(t_cli_ctx * cli, char * conf) {
    LOGV("[%s|%04d] Outbound Connected\n", cli->name, cli->sid);

    parse_insup1_outbound_plugin_info(cli, conf);


    // Setup INSUP2 header
    t_insup_message_header hdr;
    setup_default_insup_hdr(&hdr);


    t_insup_body_parameter param = {1, DB_LOGON_INFO, 8};


    apr_uint16_t inas_sid = cli->sid;
    apr_byte_t inas_id = g_sess_conf[inas_sid].inas_id;

    char insup_sid[INSUP_HEADER_SESSION_ID_SIZE] = {0,};
    char request_message[P_BUFFER_SIZE] = {0,};
    
    char tid[P_HDR_TID_SIZE] = {0,};
    snprintf(tid, P_HDR_TID_SIZE, "Login[0x%X_0x%X]", inas_id, inas_sid);

    generate_insup_sid(insup_sid, tid, inas_sid);


    hdr.msg_len = INSUP_PARAMETER_COUNT_SIZE + INSUP_PARAMETER_HEADER_SIZE + param.size;
    hdr.msg_code = DB_ACCESS_REQUEST;
    hdr.inas_id = inas_id;
    memcpy(hdr.session_id, insup_sid, INSUP_HEADER_SESSION_ID_SIZE);
    hdr.msg_len = SWAP_IF_LITTLE_ENDIAN(hdr.msg_len);
    param.size = SWAP_IF_LITTLE_ENDIAN(param.size);

    

    t_insup_body_logon_info logon_info;
    logon_info.vca              = INSUP_DEFAULT_VCA;
    logon_info.inas_id          = inas_id;
    logon_info.module_name      = g_sess_conf[inas_sid].module_name;
    logon_info.net_connect_id   = g_sess_conf[inas_sid].net_connect_id;

    for (int i = 0 ; i < 4; i++) {
        logon_info.ip[i]        = g_sess_conf[inas_sid].ip[i];
    }



    memcpy(request_message,                             &hdr, sizeof(hdr));
    memcpy(request_message+sizeof(hdr),                 &param, sizeof(param));
    memcpy(request_message+sizeof(hdr)+sizeof(param),   &logon_info, sizeof(logon_info));

    if ( g_plug.osend(cli, request_message, sizeof(hdr) + sizeof(param) + sizeof(logon_info), 0) == IN_SUCCESS) {
        LOGI1("[%s|%04d] Login request\n", cli->name, cli->sid);
        return IN_SUCCESS;
    } else {
        LOGE("[%s|%04d] Login request failed\n%s\n", cli->name, cli->sid, hdr);
    }
    
	return IN_ERR_PLUGIN_FORCE_CLOSE;
}
static int insup1_on_odisconnect(t_cli_ctx * cli) {
    LOGI1("[%s|%04d] Outbound Disconnected\n", cli->name, cli->sid);
	return IN_SUCCESS;
}
static int insup1_on_oidle(t_cli_ctx * cli, apr_time_t elasped_time) {
    if (g_conf.is_use_heartbeat == USE_HEARTBEAT) {
        t_insup_message_header hdr;
        setup_default_insup_hdr(&hdr);


        apr_uint16_t inas_sid = cli->sid;
        apr_byte_t inas_id = g_sess_conf[inas_sid].inas_id;

        char insup_sid[INSUP_HEADER_SESSION_ID_SIZE] = {0,};
        char request_message[P_BUFFER_SIZE] = {0,};
 
        char tid[P_HDR_TID_SIZE] = {0,};
        snprintf(tid, P_HDR_TID_SIZE, "HBR[0x%X_%d]", inas_id, inas_sid);
        generate_insup_sid(insup_sid, tid, inas_sid);



        hdr.msg_code = DB_NETTEST_REQUEST;
        memcpy(hdr.session_id, insup_sid, INSUP_HEADER_SESSION_ID_SIZE);
        hdr.inas_id = inas_id;


 
        memcpy(request_message,                             &hdr, sizeof(hdr));
        if ( g_plug.osend(cli, request_message, sizeof(hdr), 0) == IN_SUCCESS) {
            LOGV("[%s|%04d] Heartbeat request\n", cli->name, cli->sid);
            return IN_SUCCESS;
        } else {
            LOGE("[%s|%04d] Heartbeat request failed\n%s\n", cli->name, cli->sid, hdr);
        }
        
        return IN_ERR_PLUGIN_FORCE_CLOSE;

    }

    // not using heartbeat
	return IN_SUCCESS;
}
static int insup1_on_orecv_tokenize(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize) {
    LOGV("[%s|%04d] Received Outbound message(Tokenize)\n", cli->name, cli->sid);

    apr_int32_t rv = IN_SUCCESS;
	char * buf_ptr = buf;
	apr_uint32_t want = INSUP_HEADER_SIZE;
	apr_size_t readen = want;
	rv = in_socket_recv(csock, (char *)buf_ptr, &readen);
	if(rv != APR_SUCCESS || readen != want) {
		rv = IN_FAIL; 
        return rv;
    }

    *psize = INSUP_HEADER_SIZE;
//    *(buf_ptr + INSUP_HEADER_SIZE) = 0;
    t_insup_message_header* insup_message_header = (t_insup_message_header*)buf_ptr;

    if (insup_message_header->msg_len) {
        want = SWAP_IF_LITTLE_ENDIAN(insup_message_header->msg_len);

        readen = want;
        rv = in_socket_recv(csock, (char*)buf_ptr+INSUP_HEADER_SIZE, &readen);
        if (rv != APR_SUCCESS || readen != want) {
            rv = IN_FAIL;
        } else {
            *psize += readen;
        }
    }


	return rv;
}
static int insup1_on_orecv(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics) {

    LOGV("[%s|%04d] Received Outbound Message Size: [%d]\n", cli->name, cli->sid, size);

    if (size < INSUP_HEADER_SIZE) {
        LOGE("[%s|%04d] Outbound recv. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
        return IN_FAIL;
    }


    t_insup_message_header* hdr = (t_insup_message_header*)buf;
    char* body_pointer = buf + sizeof(t_insup_message_header);  // The start of the body pointer
    char out[P_BUFFER_SIZE] = {0,};
    int result = hdr->result;


    if (result != INSUP_RESULT_SUCCESS) {
        switch(result) {
            case INSUP_RESULT_MODULE_NOT_FOUND:
                LOGE("[%s|%04d] ASDB Module not found. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;
            case INSUP_RESULT_FAIL:
                LOGE("[%s|%04d] ASDB General failure. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;
            case INSUP_RESULT_SENDDATA_FAIL:
                LOGE("[%s|%04d] ASDB Send data failure. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;
            case INSUP_RESULT_INVALID_MESSAGE:
                LOGE("[%s|%04d] ASDB Invalid message. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;

            case INSUP_RESULT_LOGIN_DENIED:
                LOGE("[%s|%04d] ASDB Login denied. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;
            case INSUP_RESULT_OVERLOAD_REJECT:
                LOGE("[%s|%04d] ASDB Rejected because overload. Error [%d]\n%s\n", cli->name, cli->sid, size, buf);
                break;
            default:
                LOGE("[%s|%04d] ASDB. Unknown error(0x%X) Size: [%d]\n%s\n", cli->name, cli->sid, result, size, buf);
                break;
        }
    } else { // if result == INSUP_RESULT_SUCCESS
    }
    

    //Parse body parameters

    if (hdr->msg_len > 0) {

        Json::Value records;        // For SQL_OUTPUT
        int sql_result_category;    // For SQL_RESULT
        int sql_result_value;       // For SQL_RESULT
        char operation_name[P_BUFFER_SIZE] = {0,};  // For DB_OPERATION_NAME

        apr_byte_t parameter_count;     // For common header
        apr_byte_t type;                // For parameter header(type)
        apr_uint16_t size = 0;              // For parameter header(size)
        char result_desc[P_BUFFER_SIZE] = {0,}; // For result description



        t_insup_body_parameter* param_body = (t_insup_body_parameter*)body_pointer;
        parameter_count = param_body->parameter_count;

        char* current_ptr = body_pointer + INSUP_PARAMETER_COUNT_SIZE; // skip the parameter count and start with the parameter type


        
        switch(hdr->msg_code) {
            case DB_QUERY_RESPONSE:
                {
                    char tid[P_HDR_TID_SIZE] = {0,};
                    apr_uint16_t sid;
                    char svcid[P_HDR_SVC_SIZE] = {0,};
                    apr_int32_t sendsize;


                    // extract 
                    parse_insup_sid(hdr->session_id, tid, sid, svcid);

                    LOGD("[%s|%04d] INSUP received DB query response. Parameter count[%d]\n", cli->name, cli->sid, parameter_count);
                    
                    for (int i = 0 ; i < parameter_count ; i++) {
                        type = (apr_byte_t)*current_ptr;
                        // Parse body by type
                        switch (type) {
                            case SQL_RESULT:
                                current_ptr = parse_sql_result_response_parameter(cli, current_ptr, sql_result_category, sql_result_value, result_desc, sizeof(result_desc));
                                break;
                            case DB_OPERATION_NAME:
                                current_ptr = parse_db_operation_name_response_parameter(cli, current_ptr, operation_name);
                                break;
                            case SQL_OUTPUT:
                                current_ptr = parse_sql_output_response_parameter(cli, current_ptr, &records, result_desc, sizeof(result_desc));
                                break;
                            default:
                                current_ptr = parse_invalid_parameter(cli, current_ptr);
                                break;
                        }
                    }



                    Json::Value json_return;
                    Json::FastWriter fastWriter;
                    fastWriter.omitEndingLineFeed(); // This will delete the new line after the FastWriter::write function

                    char route_name[64] = {0,};
                    strncpy(route_name, cli->name, sizeof(route_name));
                    json_return["Route"] = route_name;
                    if (sql_result_category == SQL_RESULT_CATEGORY_SUCCESS) {
                        json_return["Result"] = generate_inas_internal_return_value_from_insup_result(result, sql_result_category, sql_result_value);                        
                        json_return["Values"] = records;
                        if (result_desc[0] != '\0') {
                            json_return["Desc"] = result_desc;
                        }
                        const char* output_parameters = fastWriter.write(json_return).c_str();

                        sendsize = make_cmd_hdr(    out, 
                                                    P_BUFFER_SIZE, 
                                                    P_HDR_OP_CMD P_HDR_OP_RSP_TAG, 
                                                    tid,
                                                    sid,
                                                    svcid,
                                                    g_conf.src_id,
                                                    output_parameters);

                        //SQL failure
                    } else {
                        json_return["Result"] = generate_inas_internal_return_value_from_insup_result(result, sql_result_category, sql_result_value);
                        if (result_desc[0] != '\0') {
                            json_return["Desc"] = result_desc;
                        }
                        const char* output_parameters = fastWriter.write(json_return).c_str();
                        sendsize = make_cmd_hdr(    out, 
                                                    P_BUFFER_SIZE, 
                                                    P_HDR_OP_CMD P_HDR_OP_RSP_TAG, 
                                                    tid,
                                                    sid,
                                                    svcid,
                                                    g_conf.src_id,
                                                    output_parameters);
                    }



                    if(sendsize <= 0) {
                        LOGE("[%s|%04d] Error to generate INAS internal protocol. TID[%s], SID[%d], SVCID[%s], SRCID[%s].\n", cli->name, cli->sid, tid, sid, svcid, g_conf.src_id);
                        return IN_FAIL;
                    }
                    if(g_plug.isend_sig(sid, tid, out, sendsize) != IN_SUCCESS) {
                        LOGE("[%s|%04d] Failed to send INAS internal protocol. TID[%s], SID[%d], SVCID[%s], SRCID[%s].\n", cli->name, cli->sid, tid, sid, svcid, g_conf.src_id);
                        return IN_FAIL;
                    }
                    LOGD("[%s|%04d] Sent INAS internal protocol. TID[%s], SID[%d], SVCID[%s], SRCID[%s], Size[%d].\n", cli->name, cli->sid, tid, sid, svcid, g_conf.src_id, sendsize);

                }
                break;
            case DB_ACCESS_RESPONSE:
                LOGD("[%s|%04d] INSUP received DB access response. Parameter count[%d]\n", cli->name, cli->sid, parameter_count);
                for (int i = 0; i < parameter_count ; i++) {
                    type = (apr_byte_t)*current_ptr;
                    switch(type) {
                        case DB_STATUS:
                            current_ptr = parse_db_status_response_parameter(cli, current_ptr, g_plug.update_state);
                            break;
                        case DB_LOGON_INFO:
                            current_ptr = parse_db_logon_info_response_parameter(cli, current_ptr);
                            break;
                        default:
                            current_ptr = parse_invalid_parameter(cli, current_ptr);
                            break;
                    }
                }
                break;
            case DB_STATUS_RESPONSE:
                LOGV("[%s|%04d] INSUP received DB status response. Parameter count[%d]\n", cli->name, cli->sid, parameter_count);
                for (int i = 0; i < parameter_count ; i++) {
                    type = (apr_byte_t)*current_ptr;
                    switch(type) {
                        case DB_STATUS:
                            current_ptr = parse_db_status_response_parameter(cli, current_ptr, g_plug.update_state);
                            break;
                        default:
                            current_ptr = parse_invalid_parameter(cli, current_ptr);
                            break;
                    }
                }
                break;
            case DB_QUERY_REQUEST_ACK:
                LOGD("[%s|%04d] INSUP received DB query request ack. Parameter count[%d]\n", cli->name, cli->sid, parameter_count);
                break;
            case DB_NETTEST_RESPONSE:   // Heartbeat response
                LOGV("[%s|%04d] INSUP received DB nettest response. Parameter count[%d]\n", cli->name, cli->sid, parameter_count);
                for (int i = 0; i < parameter_count ; i++) {
                    type = (apr_byte_t)*current_ptr;
                    switch(type) {
                        case DB_STATUS:
                            current_ptr = parse_db_status_response_parameter(cli, current_ptr, g_plug.update_state);
                            break;
                        default:
                            current_ptr = parse_invalid_parameter(cli, current_ptr);
                            break;
                    }
                }


                break;

            default:
                LOGE("[%s|%04d] INSUP Wrong message code [0x%X]. [%d]\n", cli->name, cli->sid, hdr->msg_code, size);
                break;
        }
    }

    
	return IN_SUCCESS;
}
