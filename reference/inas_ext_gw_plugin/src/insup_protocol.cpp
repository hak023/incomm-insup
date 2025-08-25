#include "common.h"
#include "util.h"
#include "version.h"
#include "iniparser.h"
#include "plugin.h"
#include "logger.h"
#include <inas/inas_internal_proto.h>
#include <json/json.h>
#include "insup2.h"
#include <string>
#include "insup_protocol.h"



/* This function will generate an INSUP session id (30bytes) from inas_tid and inas_sid */
IN_DECLARE(void) generate_insup_sid(char* insup_sid, const char* inas_tid, apr_uint16_t inas_sid) {

    char byte_sid[P_HDR_SID_SIZE+1] = {0,};

    memcpy(insup_sid, inas_tid, P_HDR_TID_SIZE);
    sprintf(byte_sid, "%x", inas_sid);
    memcpy(insup_sid+P_HDR_TID_SIZE, byte_sid, P_HDR_SID_SIZE);
}



/* This function will generate the INSUP DB_OPERATION_ID parameter
 * Uses as a predefined module id for DB Query
 *
 * [Protocol]
 *  TYPE(1byte): 0x01 (DB_OPERATION_ID)
 *  LENGTH(2byte): 0x0002 (Length = 2)
 *  VALUE(2byte): 0xXXXX {module_id}
 *
 * [Returns]
 *  The next char* to the end of the output buffer
 *
 * [Arguments]
 *  char* OUT out:              output buffer
 *  apr_size_t OUT size:        output buffer size
 *  apr_uint16_t IN module_id:  parameter value
 *
 */ 
IN_DECLARE(char*) generate_insup_db_operation_id_parameter(char* OUT out, apr_size_t size, apr_uint16_t IN module_id) {
    apr_uint16_t swap_module_id = SWAP_IF_LITTLE_ENDIAN(module_id);
    char* current_ptr = out;
    *current_ptr++ = DB_OPERATION_ID;

    //Length: value is 2 bytes long
    *current_ptr++ = 0x00;
    *current_ptr++ = 0x02;

    memcpy(current_ptr, &swap_module_id, INSUP_MODULE_ID_SIZE);
    current_ptr += INSUP_MODULE_ID_SIZE;
    size = current_ptr - out;

    return current_ptr;

    
}


/* This function will generate the INSUP DB_OPERATION_NAME parameter
 * Uses as a predefined operation name for DB Query
 *
 * [Protocol]
 *  TYPE(1byte): 0x02           (DB_OPERATION_NAME)
 *  LENGTH(2byte): 0x0000 | M+1 (The length of the operation name + size 1byte) 
 *  VALUE(M+1byte): 0x00 | M    (Size: 1 byte = M)
 *                  0xXXXX      (Operation name: M bytes)
 *
 * [Returns]
 *  The next char* to the end of the output buffer
 *
 * [Arguments]
 *  char* OUT out:                          output buffer
 *  apr_size_t OUT size:                    output buffer size
 *  char* IN operation_name:                parameter value
 *  apr_uint16_t IN operation_name_length:  operation_name length
 *
 */ 

IN_DECLARE(char*) generate_insup_db_operation_name_parameter(char* OUT out, apr_size_t &size, const char* IN operation_name, apr_uint16_t IN operation_name_length) {

    //Check error
    if (out == NULL || operation_name == NULL) {
        LOGW("Failed to generate insup_db_operation_name_parameter *out=[%d], *operation_name=[%s]", out, operation_name);
        size = 0;
        return out;
    }
    if (operation_name_length == 0) {
        operation_name_length = strlen(operation_name);
    }



    apr_uint16_t swap_size = SWAP_IF_LITTLE_ENDIAN(operation_name_length+1);


    char* current_ptr = out;
    *current_ptr++ = DB_OPERATION_NAME;

    //Length: value is 2 bytes long
    memcpy(current_ptr, &swap_size, sizeof(swap_size));
    current_ptr += INSUP_PARAMETER_LENGTH_SIZE;                 //parameter body size: operation_name_length + 1

    *current_ptr++ = operation_name_length;                     //value 1 byte: size
    memcpy(current_ptr, operation_name, operation_name_length); //operation name string

    current_ptr += operation_name_length;

    size = current_ptr - out;
    return current_ptr;
}





/* This function will generate the INSUP SQL_INPUT parameter
 * Receives the input values from a JSON array
 *
 * [Protocol]
 *  TYPE(1byte):    0x03        (SQL_INPUT)
 *  LENGTH(2byte):  0x0000      (The length of the values+2bytes*number of values+1byte: parameter count) 
 *  VALUE(M+1byte): 0x00 = M    (1byte parameter count)
 *                  0x0000      (First parameter size = n)
 *                  0x00*n      (First parameter value)
 *                  0x0000      (Second parameter size = m)
 *                  0x00*m      (Second parameter value)    
 *
 * [Returns]
 *  The next char* to the end of the output buffer
 *
 * [Arguments]
 *  char* OUT out:                          output buffer
 *  apr_size_t OUT size:                    output buffer size
 *  char* IN operation_name:                parameter value
 *  apr_uint16_t IN operation_name_length:  operation_name length
 *
 */ 

IN_DECLARE(char*) generate_insup_sql_input_parameter(char* OUT out, apr_size_t &size, const Json::Value* input_values) {

    size = 0;

    //Check error
    if (out == NULL || input_values == NULL) {
        LOGW("Failed to generate insup_sql_input_parameter *out=[%d], *input_values=[%d]", out, input_values);
        return out;
    }





    char* current_ptr = out;
    int input_count = input_values->size();

    *current_ptr++ = SQL_INPUT;


    char* size_ptr = current_ptr;
    current_ptr += INSUP_PARAMETER_LENGTH_SIZE; // setup the SQL_INPUT size after parsing the JSON values

    char input_value[P_BUFFER_SIZE] = {0,};
    apr_uint16_t input_value_size;
    apr_uint16_t swap_input_value_size;

    *current_ptr++ = input_count;

    for (int i = 0 ; i < input_count ; i++) {

        //JSON value to char*
        if ((*input_values)[i].isString()) {
            strncpy(input_value, (*input_values)[i].asCString(), sizeof(input_value));
        } else if ((*input_values)[i].isNumeric()) {
            in_itoa(input_value, (*input_values)[i].asInt());
        }

        input_value_size = strlen(input_value);
        swap_input_value_size = SWAP_IF_LITTLE_ENDIAN(input_value_size);

        // i th parameter size (max 1024)
        memcpy(current_ptr, &swap_input_value_size, INSUP_PARAMETER_LENGTH_SIZE);
        current_ptr += INSUP_PARAMETER_LENGTH_SIZE;


        // i th parameter value
        memcpy(current_ptr, input_value, input_value_size);
        current_ptr += input_value_size;
                
    }

    input_value_size = current_ptr - size_ptr + INSUP_PARAMETER_LENGTH_SIZE;
    swap_input_value_size = SWAP_IF_LITTLE_ENDIAN(input_value_size);
    memcpy(size_ptr, &swap_input_value_size, INSUP_PARAMETER_LENGTH_SIZE);


    size = current_ptr - out;

    return current_ptr;

}







/* this function will convert the ext_gw internal protocol's values into INSUP sid
 * INSUP SID:   30 bytes
 *      <>
 * INAS TID:    16 bytes
 * INAS SID:    4  bytes
 * INAS SVCID:  10 bytes
 */
IN_DECLARE(void) ext_gw_tid_to_insup_sid(char* insup_sid, const char* inas_tid, apr_uint16_t inas_sid, const char* inas_svcid) {
    char byte_sid[P_HDR_SID_SIZE+1] = {0,};

    sprintf(byte_sid, "%x", inas_sid);


    memcpy(insup_sid,                               inas_tid, P_HDR_TID_SIZE);
    memcpy(insup_sid+P_HDR_TID_SIZE,                byte_sid, P_HDR_SID_SIZE);
    memcpy(insup_sid+P_HDR_TID_SIZE+P_HDR_SID_SIZE, inas_svcid, P_HDR_SVC_SIZE);
   
}

/* This functino will parse the INAS TID, SID, SVCID from the INSUP SID
 * [Arguments]
 *  const char* IN insup_sid:   The INSUP SID
 *  char* OUT inas_tid:         The INAS TID
 *  apr_uint16_t OUT &inas_sid: The INAS SID
 *  char* OUT inas_svcid:       The INAS SVCID
 */
IN_DECLARE(void) parse_insup_sid(const char* IN insup_sid, char* OUT inas_tid, apr_uint16_t OUT &inas_sid, char* OUT inas_svcid) {

    char byte_sid[P_HDR_SID_SIZE+1] = {0,};

    memcpy(inas_tid, insup_sid, P_HDR_TID_SIZE);
    memcpy(byte_sid, insup_sid+P_HDR_TID_SIZE, P_HDR_SID_SIZE);
    
    inas_sid = strtoul(byte_sid, NULL, 16);

    memcpy(inas_svcid, insup_sid+P_HDR_TID_SIZE+P_HDR_SID_SIZE, P_HDR_SVC_SIZE);
}


/* 
 * This function parses the DB_STATUS parameter
 * [Arguments]
 *  t_cli_ctx* cli: Output session client
 *  char* param: the start pointer of the parameter
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_db_status_response_parameter(t_cli_ctx* cli, char* param, update_state_callback callback) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);
    int header_length = INSUP_PARAMETER_TYPE_SIZE+INSUP_PARAMETER_LENGTH_SIZE;
    char update_status = 'A';

    if (cli != NULL) {
        LOGV("[%s|%04d] Received DB status parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+header_length);
        //TODO
        // Check the DB status and update the outbound session status
        // 'MA' -> 'A'
        // 'MB' -> 'B'
        // 'DA' -> 'D'
        // 'DB' -> 'B'
        if (*(param+header_length+1) == 'B') {
            update_status = 'B';
        } else if (*(param+header_length) == 'M') {
            update_status = 'A';
        } else if (*(param+header_length) == 'D') {
            update_status = 'D';
        } else {
            LOGW("[%s|%04d] Unknown status from DB server. Value [%s]\n", cli->name, cli->sid, param+header_length);
        }

        if (callback != NULL) {
            callback(cli, update_status);
        } else {
            LOGW("WARNING: Client context update plugin handle's update_state function is NULL.\n");
        }
    } else {
        // no client context, just warning log
        LOGW("No client context for DB Status parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }

    return param + size + header_length;
}



/* 
 * This function parses the SQL_OUTPUT parameter
 * [Arguments]
 *  t_cli_ctx* cli:         Output session client
 *  char* param:            The start pointer of the parameter
 *  Json::Value* OUT json:  The output in JSON format
 *  char* result_desc:      The description if the result is not normal
 *  apr_size_t result_desc_size: The size of the description buffer
 *
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_sql_output_response_parameter(t_cli_ctx* cli, char* param, Json::Value* OUT json, char* OUT result_desc, apr_size_t result_desc_size) {

    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);
    char* current_ptr = param+INSUP_PARAMETER_TYPE_SIZE+INSUP_PARAMETER_LENGTH_SIZE;

    if (cli != NULL) {
        LOGV("[%s|%04d] Received DB query response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGV("Received DB query response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }


    /* record count:                    1 byte
     * 1st record's fields_count:       1 byte
     * 1st record's 1st field size:     2 bytes = m
     * 1st record's 1st field value:    m bytes
     * 1st record's 2nd field size:     2 bytes = n
     * 1st record's 2nd field value:    n bytes
     * ....
     * 2nd record's field count:        1 byte
     * 2nd record's 1st field size:     2 byte = o
     * 2nd record's 1st field value:    o bytes
     * ....
     */


    int record_count = *current_ptr++;
    int field_count;
    apr_uint16_t field_size;
    char field_buffer[P_BUFFER_SIZE] = {0,};

    LOGV("Record count: %d\n", record_count);

    // loop through records
    for (int i = 0 ; i < record_count; i++) {
        Json::Value record;
        field_count = *current_ptr++;
        LOGV("Field count: %d\n", field_count);

        // loop through fields
        for (int j = 0 ; j < field_count ; j++) {
            field_size = get_field_size(current_ptr);
            current_ptr += INSUP_FIELD_LENGTH_SIZE;
 
          
            memcpy(field_buffer, current_ptr, field_size);
            current_ptr += field_size;
            field_buffer[field_size] = 0;

            LOGV("Record#%d | Field#%d [%d]: %s\n", i, j, field_size, field_buffer);

            // if the field index is for the result description and the field size is not 0
            if ( j == INSUP_SQL_OUTPUT_RESULT_DESC_INDEX) {
                if (field_size > 0) {
                    strncpy(result_desc, field_buffer, result_desc_size);
                    if (cli != NULL) {
                        LOGD("[%s|%04d] Received DB result description, [%s]\n", cli->name, cli->sid, result_desc);
                    } else {
                        LOGD("Received DB result description, [%s]\n", cli->name, cli->sid, result_desc);
                    }
                }
            } else {
                record.append(field_buffer);
            }
 

        }

        json->append(record);
    }
    
    return current_ptr;
}


/* 
 * This function parses the DB_LOGON_INFO parameter
 *
 * [Protocol]
 *  VCA         (1byte): 0xF0 Fixed
 *  AS ID       (1byte)
 *  ModuleName  (1byte)
 *  NetConnectID(1byte)
 *  IP          (4byte): 0x00000000 IPv4
 *
 * [Arguments]
 *  t_cli_ctx* cli:         Output session client
 *  char* param:            The start pointer of the parameter
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_db_logon_info_response_parameter(t_cli_ctx* IN cli, char* IN param) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (param == NULL) {
        LOGW("DB_LOGON_INFO is not invoked properly. Input parameter is NULL\n");
        return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
    }

    if (cli != NULL) {
        LOGV("[%s|%04d] Received DB logon infomation response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGV("Received DB logon information response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }
     t_insup_body_logon_info insup_db_logon_info;

    memcpy(&insup_db_logon_info, param + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE, sizeof(insup_db_logon_info));

    LOGD("DB_LOGON_INFO parameter: VCA[0x%02X], INAS ID[%d], Module name [0x%02X], Net connect id [0x%02X], IP[%d.%d.%d.%d]\n", insup_db_logon_info.vca, insup_db_logon_info.inas_id, insup_db_logon_info.module_name, insup_db_logon_info.net_connect_id, insup_db_logon_info.ip[0], insup_db_logon_info.ip[1], insup_db_logon_info.ip[2], insup_db_logon_info.ip[3]); 

    return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
}




/* 
 * This function parses the SQL_RESULT parameter
 *
 * [Protocol]
 *  TYPE(1byte):   0x05         (SQL_RESULT)
 *  LENGTH(2byte): 0x0000       (The length of the result, 2byte) 
 *  VALUE(2byte):  0x00         (Result category: 1 byte)
 *                 0x00         (Result values: 1 byte)
 *
 * [Result specification] 
 *  Result category(1 byte)     Result value(1 byte)
 *  0x00 : Success              0x00 : Success
 *                              0x01 : No data
 *  0x10 : AS fail              0x10 : NO OP ID
 *                              0x11 : Invalid OP ID
 *                              0x12 : No input parameter
 *                              0x13 : Invalid input parameter
 *                              0x14 : No op name
 *                              0x15 : Invalid op name
 *  0x20 : DB fail              0x20 : SQL operation error
 *                              0x21 : DBMS not connected state
 *                              0x22 : DBMS not accessible state
 *
 * 
 * [Arguments]
 *  t_cli_ctx* cli:                 Output session client
 *  char* param:                    The start pointer of the parameter
 *  int OUT &sql_result:            The sql result, output 
 *  char* OUT result_desc:          The description if the result is not normal
 *  apr_size_t IN result_desc_size: The size of the description buffer
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_sql_result_response_parameter(t_cli_ctx* cli, char* &param, int OUT &result_category, int OUT &result_value, char* OUT result_desc, apr_size_t result_desc_size) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (cli != NULL) {
        LOGV("[%s|%04d] Received SQL result response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGV("Received SQL result response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }

    result_category = *(param + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE);
    result_value = *(param + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE + INSUP_RESULT_CATEGORY_SIZE);

    switch(result_value) {
        case SQL_RESULT_VALUE_NO_OP_ID:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: No operation id [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_INVALID_OP_ID:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: Invalid operation id [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_NO_PARAMETER:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: No input parameter [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_INVALID_PARAMETER:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: Invalid input parameter [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_NO_OP_NAME:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: No operation name [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_INVALID_OP_NAME:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: Invalid operation name [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;

        // SQL_RESULT_CATEGORY_DBFAIL
        case SQL_RESULT_VALUE_SQL_ERROR:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [DB error]: SQL operation error [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_DBMS_NOT_CONNECTED:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [DB error]: DBMS not connected [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;
        case SQL_RESULT_VALUE_DBMS_NOT_ACCESSIBLE:
            snprintf(result_desc, result_desc_size, "[ERROR] SQL result error [AS error]: DBMS not accessible [0x%X|0x%X]\n", result_category, result_category);
            LOGW(result_desc);
            break;

    }

    return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
}

/* 
 * This function parses the DB_OPERATION_NAME parameter
 *
 * [Protocol]
 *  TYPE(1byte): 0x02           (DB_OPERATION_NAME)
 *  LENGTH(2byte): 0x0000 | M+1 (The length of the operation name + size 1byte) 
 *  VALUE(M+1byte): 0x00 | M    (Size: 1 byte = M)
 *                  0xXXXX      (Operation name: M bytes)

 * 
 * [Arguments]
 *  t_cli_ctx* cli:             Output session client
 *  char* param:                The start pointer of the parameter
 *  char* OUT operation_name    The db operation name
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_db_operation_name_response_parameter(t_cli_ctx* IN cli, char* IN param, char* OUT operation_name) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (operation_name == NULL || param == NULL) {
        LOGW("DB_OPERATION_NAME is not invoked properly. Operation name buffer or input parameter is NULL\n");
        return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
    }

    if (cli != NULL) {
        LOGD("[%s|%04d] Received DB operation name response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGD("Received DB operation name response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }
    int operation_name_length = *(param+INSUP_PARAMETER_TYPE_SIZE+INSUP_PARAMETER_LENGTH_SIZE);

    memcpy(operation_name, param + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE + 1, operation_name_length);
    *(operation_name + operation_name_length) = 0;

    LOGD("DB_OPERATION_NAME parameter: [%d] %s\n", operation_name_length, operation_name); 
    

    return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
}





// TODO: output parameter JSON?
IN_DECLARE(void) parse_db_nettest_response_parameter(t_cli_ctx* cli, char* &param) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (cli != NULL) {
        LOGD("[%s|%04d] Received DB nettest response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGD("Received DB nettest response parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }

    //TODO: Generate a JSON::Value from the SQL output
    // Check the DB status and update the outbound session status
    //
    //
    //
    //
    //
    param += size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
}

// TODO: output parameter JSON?
IN_DECLARE(void) parse_db_query_request_ack_parameter(t_cli_ctx* cli, char* &param) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (cli != NULL) {
        LOGD("[%s|%04d] Received DB Query ack parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        // no client context, just warning log
        LOGD("Received DB Query ack parameter, Type: [0x%X] Size: [%d] Value [%s]\n", type, size, param+3);
    }

    param += size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;
}



/**
 * This function will parse the invalid parameter and skip the parameter
 * [Arguments]
 *  t_cli_ctx* cli: Output session client
 *  char* param: the start pointer of the parameter
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
IN_DECLARE(char*) parse_invalid_parameter(t_cli_ctx* cli, char* param) {
    apr_byte_t type = *param;
    apr_uint16_t size = get_parameter_size(param+INSUP_PARAMETER_TYPE_SIZE);

    if (cli != NULL) {
        LOGW("[%s|%04d] Received invalid parameter, Type: [0x%X] Size: [%d] Value [%s]\n", cli->name, cli->sid, type, size, param+3);
    } else {
        LOGW("Received invalid parameter, Type: [0x%X] Size: [%d] Value: [%s]\n", type, size, param+3);
    }
    return param + size + INSUP_PARAMETER_TYPE_SIZE + INSUP_PARAMETER_LENGTH_SIZE;

}


    /* This function is to parse the size parameter in the body parameter
     * if the OS is little endian swap the bytes
     * ASSERT: size parameter length size = 2 byte
     * [Arguments]
     *  void* size_ptr:     An anonymous pointer to the field size
     *
     * [Return]
     *  apr_uint16_t:       The field size converted from the anonymous pointer
     */
    IN_DECLARE(apr_uint16_t) get_parameter_size(void* size_ptr){
 #if INSUP_PARAMETER_LENGTH_SIZE==2
        apr_uint16_t size;
        memcpy(&size, size_ptr, INSUP_PARAMETER_LENGTH_SIZE);
        size = SWAP_IF_LITTLE_ENDIAN(size);
        return size;
#else
        LOGE("INSUP parameter length size has been changed: Fix get_parameter_size in insup_protocol.h and cpp");
        return *size_ptr;
#endif
    }

    /* This function is to parse the size parameter in the SQL_OUTPUT parameter
     * if the OS is little endian swap the bytes
     * ASSERT: size parameter length size = 2 byte
     * [Arguments]
     *  void* size_ptr:     An anonymous pointer to the field size
     *
     * [Return]
     *  apr_uint16_t:       The field size converted from the anonymous pointer
     */
    IN_DECLARE(apr_uint16_t) get_field_size(void* size_ptr){
 #if INSUP_FIELD_LENGTH_SIZE==2
        apr_uint16_t size;
        memcpy(&size, size_ptr, INSUP_PARAMETER_LENGTH_SIZE);
        size = SWAP_IF_LITTLE_ENDIAN(size);
        return size;
#else
        LOGE("INSUP field length size has been changed: Fix get_field_size in insup_protocol.h and cpp");
        return *size_ptr;
#endif
    }



    /* This function will setup the INSUP header to default values
     * [Arguments]
     *  t_insup_message_header* hdr: The header pointer to setup the default values
     */
    IN_DECLARE(void) setup_default_insup_hdr(t_insup_message_header* hdr) {

        char wtime[INSUP_HEADER_WTIME_SIZE+1];
 
        in_time_to_string_milli(wtime, sizeof(wtime), apr_time_now());


        memset(hdr, 0, sizeof(t_insup_message_header));

        hdr->msg_len = 0;
        //hdr->msg_code = 0x**
        hdr->svca = INSUP_DEFAULT_SVCA;
        hdr->dvca = INSUP_DEFAULT_DVCA;
        //hdr->inas_id = 0x**
        //hdr->session_id = "******************************"
        memcpy(hdr->svc_id, "INAS", INSUP_HEADER_SVC_ID_SIZE);
        hdr->result = 0;

        memcpy(hdr->wtime, wtime, INSUP_HEADER_WTIME_SIZE);
        
        hdr->major_version = PKG_MAJORVERSION_NUMBER;
        hdr->minor_version = PKG_MINORVERSION_NUMBER;
        hdr->dummy = 0;
        hdr->use_request_ack = INSUP_DONT_USE_REQUEST_ACK;

    }

           
/* 
 * This function will generate the inas internal return value from the INSUP results: Request result, SQL result category, SQL result value
 *
 * [Arguments] 
 *  int IN request_result
 *  int IN sql_result_category
 *  int IN sql_result_value
 *
 * [Return]
 *  int : generated inas internal return value
 */

    IN_DECLARE(int) generate_inas_internal_return_value_from_insup_result(int request_result, int sql_result_category, int sql_result_value) {
        return (request_result << 16)+ (sql_result_category << 8) + sql_result_value;
    }

       


