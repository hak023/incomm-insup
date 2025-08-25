#ifndef _INSUP__H_
#define _INSUP2__H_

#include "common.h"

#ifdef __cplusplus
     extern "C" {
#endif


    #define DONOT_USE_HEARTBEAT 0
    #define USE_HEARTBEAT       1
    #define IS_USE_HEARTBEAT    USE_HEARTBEAT


    /*
     * INSUP Header description
     * [Header]: 62 byte fixed
     * | MSG_LEN(2) | MSG_CODE(1) | SVCA(1) | DVCA(1) | INAS_ID(1) | SESSION_ID(30) | SVC_ID(4) | RESULT(1) | WTIME(17) | MAJOR_VERSION(1) | MINOR_VERSION(1) | DUMMY(1) | ACK(1) | Body
     *
     * [Body]: "MSG_LEN" bytes
     * |PARAMETER_COUNT=N(1) | 1_PARAM_TYPE(1) | 1_PARAM_LENGTH=M(2) | 1_PARAM_DATA(M) | N_PARAM_TYPE(1) | N_PARAM_LENGTH=O(2) | N_PARAM_DATA(O) |
     *
     */

    #define INSUP_HEADER_SIZE               (INSUP_HEADER_MSG_LEN_SIZE+INSUP_HEADER_MSG_CODE_SIZE+INSUP_HEADER_SVCA_SIZE+INSUP_HEADER_DVCA_SIZE+INSUP_HEADER_INAS_ID_SIZE+INSUP_HEADER_SESSION_ID_SIZE+INSUP_HEADER_SVC_ID_SIZE+INSUP_HEADER_RESULT_SIZE+INSUP_HEADER_WTIME_SIZE+INSUP_HEADER_MAJOR_VERSION_SIZE+INSUP_HEADER_MINOR_VERSION_SIZE+INSUP_HEADER_DUMMY_SIZE+INSUP_HEADER_ACK_SIZE)
    #define INSUP_HEADER_MSG_LEN_SIZE       2
    #define INSUP_HEADER_MSG_CODE_SIZE      1
    #define INSUP_HEADER_SVCA_SIZE          1
    #define INSUP_HEADER_DVCA_SIZE          1
    #define INSUP_HEADER_INAS_ID_SIZE       1
    #define INSUP_HEADER_SESSION_ID_SIZE    30
    #define INSUP_HEADER_SVC_ID_SIZE        4
    #define INSUP_HEADER_RESULT_SIZE        1
    #define INSUP_HEADER_WTIME_SIZE         17
    #define INSUP_HEADER_MAJOR_VERSION_SIZE 1
    #define INSUP_HEADER_MINOR_VERSION_SIZE 1
    #define INSUP_HEADER_DUMMY_SIZE         1
    #define INSUP_HEADER_ACK_SIZE           1

    #define INSUP_PARAMETER_COUNT_SIZE      1
    #define INSUP_PARAMETER_TYPE_SIZE       1
    #define INSUP_PARAMETER_LENGTH_SIZE     2

    #define INSUP_PARAMETER_HEADER_SIZE     INSUP_PARAMETER_TYPE_SIZE+INSUP_PARAMETER_LENGTH_SIZE


    // DB_OPERATION_ID value
    #define INSUP_MODULE_ID_SIZE            2

    // SQL_OUTPUT value
    #define INSUP_FIELD_LENGTH_SIZE         2

    // SQL_RESULT
    #define INSUP_RESULT_CATEGORY_SIZE      1
    #define INSUP_RESULT_VALUE_SIZE         1

    #define INSUP_DEFAULT_VCA               0xA2
    #define INSUP_DEFAULT_SVCA              0x11
    #define INSUP_DEFAULT_DVCA              0xA2
    #define INSUP_DEFAULT_INAS_ID           "100"    // Simulator ASID: 100
    #define INSUP_DEFAULT_NET_ID            "1"
    #define INSUP_DEFAULT_IP                {127,0,0,1}  //127.0.0.1


    #define INSUP_SQL_OUTPUT_RESULT_DESC_INDEX  1   // The index of the result description from the API, index start with 0



    // SQL_RESULT's result category specification
    typedef enum {
        SQL_RESULT_CATEGORY_SUCCESS = 0x00,
        SQL_RESULT_CATEGORY_ASFAIL  = 0x10,
        SQL_RESULT_CATEGORY_DBFAIL  = 0x20
    } e_insup_sql_result_category;

    typedef enum {
        // SQL_RESULT_CATEGORY_SUCCESS
        SQL_RESULT_VALUE_SUCCESS      =  0x00,
        SQL_RESULT_VALUE_NO_DATA      =  0x01,

        // SQL_RESULT_CATEGORY_ASFAIL
        SQL_RESULT_VALUE_NO_OP_ID       = 0x10,
        SQL_RESULT_VALUE_INVALID_OP_ID  = 0x11,
        SQL_RESULT_VALUE_NO_PARAMETER   = 0x12,
        SQL_RESULT_VALUE_INVALID_PARAMETER  = 0x13,
        SQL_RESULT_VALUE_NO_OP_NAME     = 0x14,
        SQL_RESULT_VALUE_INVALID_OP_NAME = 0x15,

        // SQL_RESULT_CATEGORY_DBFAIL
        SQL_RESULT_VALUE_SQL_ERROR            = 0x20,
        SQL_RESULT_VALUE_DBMS_NOT_CONNECTED   = 0x21,
        SQL_RESULT_VALUE_DBMS_NOT_ACCESSIBLE  = 0x22

    } e_insup_sql_result_value;

    // SQL_RESULT's result value specification
    /* The Message code in the header. Used in t_insup_message_header.msg_code */ 
    typedef enum {
        DB_QUERY_REQUEST = 1,
        DB_QUERY_RESPONSE,
        DB_ACCESS_REQUEST,
        DB_ACCESS_RESPONSE,
        DB_NETTEST_REQUEST,
        DB_NETTEST_RESPONSE,
        DB_STATUS_REQUEST,
        DB_STATUS_RESPONSE,
        DB_QUERY_REQUEST_ACK,
    } e_insup_header_msg_code;

    
    /* The control parameter in the parameter type. Used in t_insup_parameter.type */
    typedef enum {
        DB_OPERATION_ID = 1,
        DB_OPERATION_NAME,
        SQL_INPUT,
        SQL_OUTPUT,
        SQL_RESULT,
        DB_STATUS,
        DB_LOGON_INFO,
    } e_insup_body_parameter_type;

    /* The result value in the header */
    typedef enum {
        INSUP_RESULT_SUCCESS            = 0x01,
        INSUP_RESULT_FAIL               = 0x02,
        INSUP_RESULT_MODULE_NOT_FOUND   = 0x10,
        INSUP_RESULT_SENDDATA_FAIL      = 0x11,
        INSUP_RESULT_INVALID_MESSAGE    = 0x12,
        INSUP_RESULT_LOGIN_DENIED       = 0x20,
        INSUP_RESULT_OVERLOAD_REJECT    = 0x21
    } e_insup_header_result_value;


    /* The header use_request_ack parameter */
    typedef enum {
        INSUP_USE_REQUEST_ACK = 0x00,
        INSUP_DONT_USE_REQUEST_ACK = 0x7F
    } g_insup_header_use_request_ack;

    /* API header struct */
    typedef struct _t_insup_message_header          t_insup_message_header;
    typedef struct _t_insup_body_parameter          t_insup_body_parameter;
    typedef struct _t_insup_body_logon_info         t_insup_body_logon_info;
    typedef struct _t_insup_body_db_status          t_insup_body_db_status;
    typedef struct _t_insup_body_db_result          t_insup_body_db_result;
    typedef struct _t_insup_body_db_operation_name  t_insup_body_db_operation_id;

    typedef struct _t_insup_session_id    t_insup_session_id;




    struct _t_insup_message_header {
        apr_uint16_t    msg_len;                                    //  2 byte: packet body size
        apr_byte_t      msg_code;                                   //  1 byte: message code -> e_insup_header_msg_code
        apr_byte_t      svca;                                       //  1 byte: Source Virtual Communication Address(not in use)
        apr_byte_t      dvca;                                       //  1 byte: Destination Virtual Communication Address(not in use)
        apr_byte_t      inas_id;                                    //  1 byte: inas id
        char            session_id[INSUP_HEADER_SESSION_ID_SIZE];   // 30 byte: session id
        char            svc_id[INSUP_HEADER_SVC_ID_SIZE];           //  4 byte: svc id (not in use)
        apr_byte_t      result;                                     //  1 byte: result
        char            wtime[INSUP_HEADER_WTIME_SIZE];             // 17 byte: wtime
        apr_byte_t      major_version;                              //  1 byte: major version
        apr_byte_t      minor_version;                              //  1 byte: minor version
        apr_byte_t      dummy;                                      //  1 byte: dummy
        apr_byte_t      use_request_ack;                            //  1 byte: Not use(0x7F), use(0x00)
    };

    struct _t_insup_body_parameter {
        apr_byte_t      parameter_count;    //  1 byte: parameter count
        apr_byte_t      type;               //  1 byte: type -> e_insup_body_parameter_type
        apr_uint16_t    size;               //  2 byte: size
    };

    struct _t_insup_body_logon_info {
        apr_byte_t vca;
        apr_byte_t inas_id;
        apr_byte_t module_name;
        apr_byte_t net_connect_id;
        apr_byte_t ip[4];
    };

    struct _t_insup_body_db_status {
        apr_byte_t system_category;
        apr_byte_t system_status;
    };

    struct _t_insup_body_db_result {
        apr_byte_t result_category;
        apr_byte_t result_value;
    };

    struct _t_insup_body_db_operation_id {
        apr_byte_t size;
        apr_byte_t operation_name[40];
    };

    struct _t_insup_session_id {
        apr_byte_t transaction_id[16];
        apr_byte_t session_id[4];
        apr_byte_t svc_id[10];
    };


    // Used as a callback to update the client context state from the plugin handle
    typedef int (*update_state_callback)(t_cli_ctx*, char);



    /* INSUP protocol utils */

    #define SWAP_IF_LITTLE_ENDIAN(x)    (in_is_little_endian() ? swap_2bytes((x)) : (x))
    
    /* This function is to parse the size parameter in the body parameter
     * if the OS is little endian swap the bytes
     * ASSERT: size parameter length size = 2 byte
     * [Arguments]
     *  void* size_ptr:     An anonymous pointer to the field size
     *
     * [Return]
     *  apr_uint16_t:       The field size converted from the anonymous pointer
     */
    IN_DECLARE(apr_uint16_t) get_parameter_size(void* size_ptr);

    /* This function is to parse the size parameter in the SQL_OUTPUT parameter
     * if the OS is little endian swap the bytes
     * ASSERT: size parameter length size = 2 byte
     * [Arguments]
     *  void* size_ptr:     An anonymous pointer to the field size
     *
     * [Return]
     *  apr_uint16_t:       The field size converted from the anonymous pointer
     */
    IN_DECLARE(apr_uint16_t) get_field_size(void* size_ptr);



    IN_DECLARE(void) generate_insup_sid(char* insup_sid, const char* inas_tid, apr_uint16_t inas_sid);
    IN_DECLARE(void) ext_gw_tid_to_insup_sid(char* insup_sid, const char* inas_tid, apr_uint16_t inas_sid, const char* inas_svcid);





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
    IN_DECLARE(void) generate_insup_db_operation_id_parameter(char* OUT out, apr_uint16_t IN module_id);




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

    IN_DECLARE(char*) generate_insup_db_operation_name_parameter(char* OUT out, apr_size_t &size, const char* IN operation_name, apr_uint16_t IN operation_name_length);





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

IN_DECLARE(char*) generate_insup_sql_input_parameter(char* OUT out, apr_size_t &size, const Json::Value* input_values);



    /* This function will setup the INSUP header to default values
     * [Arguments]
     *  t_insup_message_header* hdr: The header pointer to setup the default values
     */
    IN_DECLARE(void) setup_default_insup_hdr(t_insup_message_header* hdr);


    /* This functino will parse the INAS TID, SID, SVCID from the INSUP SID
     * [Arguments]
     *  const char* IN insup_sid:   The INSUP SID
     *  char* OUT inas_tid:         The INAS TID
     *  apr_uint16_t OUT &inas_sid: The INAS SID
     *  char* OUT inas_svcid:       The INAS SVCID
     */
    IN_DECLARE(void) parse_insup_sid(const char* IN insup_sid, char* OUT inas_tid, apr_uint16_t OUT &inas_sid, char* OUT inas_svcid);


/* 
 * This function parses the DB_STATUS parameter
 * [Arguments]
 *  t_cli_ctx* cli: Output session client
 *  char* param: the start pointer of the parameter
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
    IN_DECLARE(char*) parse_db_status_response_parameter(t_cli_ctx* cli, char* param, update_state_callback callback);



/* 
 * This function parses the SQL_OUTPUT parameter
 * [Arguments]
 *  t_cli_ctx* cli:         Output session client
 *  char* param:            The start pointer of the parameter
 *  Json::Value* OUT json:  The output in JSON format
 *  char* result_desc:      The description if the result is not normal
 *  apr_size_t result_desc_size: The size of the description buffer
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
    IN_DECLARE(char*) parse_sql_output_response_parameter(t_cli_ctx* cli, char* param, Json::Value* OUT json, char* OUT result_desc, apr_size_t result_desc_size);


/* 
 * This function parses the DB_OPERATION_NAME parameter
 * [Arguments]
 *  t_cli_ctx* cli:         Output session client
 *  char* param:            The start pointer of the parameter
 *  char*   operation_name: The output char pointer of the operation name
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
    IN_DECLARE(char*) parse_db_operation_name_response_parameter(t_cli_ctx* IN cli, char* IN param, char* OUT operation_name);


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
IN_DECLARE(char*) parse_db_logon_info_response_parameter(t_cli_ctx* IN cli, char* IN param);



/**
 * This function will parse the invalid parameter and skip the parameter
 * [Arguments]
 *  t_cli_ctx* cli: Output session client
 *  char* param: the start pointer of the parameter
 *
 * [Return]
 *  char* : the next pointer of the parameter
 */
    IN_DECLARE(char*) parse_invalid_parameter(t_cli_ctx* cli, char* param);


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
    IN_DECLARE(char*) parse_sql_result_response_parameter(t_cli_ctx* cli, char* &param, int OUT &result_category, int OUT &result_value, char* OUT result_desc, apr_size_t IN result_desc_size);




    IN_DECLARE(void) parse_db_access_response_parameter(t_cli_ctx* cli, char* &param);
    IN_DECLARE(void) parse_db_nettest_response_parameter(t_cli_ctx* cli, char* &param);
    IN_DECLARE(void) parse_db_query_request_ack_parameter(t_cli_ctx* cli, char* &param);

    
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

    IN_DECLARE(int) generate_inas_internal_return_value_from_insup_result(int request_result, int sql_result_category, int sql_result_value);

#ifdef __cplusplus
     }
#endif

#endif /* _INSUP2__H_ */
