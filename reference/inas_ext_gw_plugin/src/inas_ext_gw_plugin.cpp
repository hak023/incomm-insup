//============================================================================
// Name        : inas_ext_gw_plugin.cpp
// Author      : 
// Version     :
// Copyright   : Your copyright notice
// Description : Hello World in C++, Ansi-style
//============================================================================

#include "common.h"
#include "plugin.h"
#include "version.h"
#include "ingw.h"
#include "inmg.h"
#include "insup1.h"
#include "insup2.h"

#if defined(__DATE__) && defined(__TIME__)
static char g_pkg_built[32] = {0,} /*__DATE__ " " __TIME__*/;
#else
static char g_pkg_built[32] = "Unknown";
#endif

#ifdef __cplusplus
     extern "C" {
#endif

     IN_DECLARE(const char *) get_pkg_built(void);
     IN_DECLARE(void) set_pkg_version(const char *);
     IN_DECLARE(const char *) get_pkg_version(void);

#ifdef __cplusplus
     }
#endif

static struct {
	t_plugin_handle * hplug;
} g_plugs[] = {
    { ingw_get_handle() },
    { inmg_get_handle() },
	{ insup1_get_handle() },
	{ insup2_get_handle() },
};

/**
 * @return
 */
IN_EXPORT_INTERFACE(int) create_plugin_handle(char * plug_name, t_plugin_handle ** OUT phplug, t_plugin_handle * IN hplug) {
	for(apr_uint32_t i = 0; i < sizeof(g_plugs)/sizeof(g_plugs[0]); ++i) {
		if(strcmp(g_plugs[i].hplug->name, plug_name) == 0) {
			t_plugin_handle * h = g_plugs[i].hplug;
			strncpy(h->conf_path, hplug->conf_path, sizeof(h->conf_path));
			h->isend = hplug->isend;
			h->isend_sig = hplug->isend_sig;
			h->osend = hplug->osend;
			h->osend_rr = hplug->osend_rr;
			h->update_state = hplug->update_state;
			if(h->on_init) {
				h->on_init(h);
			}
            *phplug = h;
			return IN_SUCCESS;
		}
	}
	return IN_FAIL;
}

/**
 * @return
 */
IN_EXPORT_INTERFACE(void) destroy_plugin_handle(t_plugin_handle * IN hplug) {
	if(hplug && hplug->on_finish) {
		hplug->on_finish(hplug);
	}
}

/**
 * Get the date a time that the pkg was built
 * @return The server build time string
 */
IN_EXPORT_INTERFACE(const char *) get_plugin_built(void) {
    if(!g_pkg_built[0])
        snprintf(g_pkg_built, sizeof(g_pkg_built), "%04d%02d%02d%02d%02d%02d", BUILD_YEAR, BUILD_MONTH, BUILD_DAY, BUILD_HOUR, BUILD_MIN, BUILD_SEC);
    return g_pkg_built;
}

/**
 * get_plugin_version
 * @return
 */
IN_EXPORT_INTERFACE(const char *) get_plugin_version(void) {
	return PKG_VERSION;
}

/**
 * set_plugin_logging_cb
 * @return
 */
int (*g_logging_cb)(const int lv, const char * fname, const int line, const char * key, const char *fmt, ...) = NULL;

IN_EXPORT_INTERFACE(void ) set_plugin_logging_cb(int (*cb)(const int lv, const char * fname, const int line, const char * key, const char *fmt, ...)) {
	g_logging_cb = cb;
}
