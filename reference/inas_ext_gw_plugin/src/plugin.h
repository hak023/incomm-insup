#ifndef _PLUGIN__H_
#define _PLUGIN__H_

#include "common.h"
#include <json/json.h>

typedef struct {apr_uint16_t sid;char name[D_NAME_SIZE];} t_cli_ctx;

typedef struct _t_plugin_handle t_plugin_handle;

struct _t_plugin_handle {
    char name[64]; /**< Plugin name */
    char conf_path[256]; /**< Conf file */

    /* Inbound session */
    int (*on_iconnect)(t_cli_ctx * cli);
    int (*on_idisconnect)(t_cli_ctx * cli);
    int (*on_idle)(t_cli_ctx * cli, apr_time_t elasped_time);
    int (*on_irecv)(t_cli_ctx * cli, apr_time_t rtime, const char * op, const char * tid, const apr_uint16_t sid, const char * svcid, const char * srcid, Json::Value & root);
    int (*isend)(t_cli_ctx * cli, const char * tid, const char * IN buf, apr_size_t IN size);
    int (*isend_sig)(apr_uint16_t sid, const char * tid, const char * IN buf, apr_size_t IN size);

    /* Outbound session */
    int (*on_oconnect)(t_cli_ctx * cli, char * conf);
    int (*on_odisconnect)(t_cli_ctx * cli);
    int (*on_oidle)(t_cli_ctx * cli, apr_time_t elasped_time);
    int (*on_orecv_tokenize)(t_cli_ctx * cli, apr_socket_t * csock, char * OUT buf, apr_size_t * IN OUT psize);
    int (*on_orecv)(t_cli_ctx * cli, apr_time_t rtime, char * IN buf, apr_size_t IN size, int & OUT wantStatistics);
    int (*osend)(t_cli_ctx * cli, const char IN * buf, apr_size_t IN size, int IN wantStatistics);
    int (*osend_rr)(char IN * buf, apr_size_t IN size, int IN wantStatistics, int (*on_modify_data) (t_cli_ctx * cli, char IN OUT * buf, apr_size_t IN size));

    /* Common session */
    int (*update_state)(t_cli_ctx * cli, char state);

    /* Internal callback */
    int (*on_init)(struct _t_plugin_handle *);
    void (*on_finish)(struct _t_plugin_handle *);
};

#endif /* _PLUGIN__H_ */
