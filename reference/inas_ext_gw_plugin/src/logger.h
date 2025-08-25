#ifndef _LOGGER__H_
#define _LOGGER__H_

#include "common.h"

#define LOG_LV_ERR                  0
#define LOG_LV_WARNING              1
#define LOG_LV_SYS                  2
#define LOG_LV_INF                  3
#define LOG_LV_DBG                  5
#define LOG_LV_DBG_VERBOSE          6

extern int (*g_logging_cb)(const int lv, const char * fname, const int line, const char * key, const char *fmt, ...);

#define LOGE(...)                   g_logging_cb(LOG_LV_ERR,   __FILE__, __LINE__, "ERR", __VA_ARGS__)
#define LOGEK(_KEY_, ...)           g_logging_cb(LOG_LV_ERR,   __FILE__, __LINE__, _KEY_, __VA_ARGS__)
#define LOGW(...)                   g_logging_cb(LOG_LV_SYS,   __FILE__, __LINE__, "WARN", __VA_ARGS__)
#define LOGS(...)                   g_logging_cb(LOG_LV_SYS,   __FILE__, __LINE__, "SYS"  , __VA_ARGS__)
#define LOGI(...)                   g_logging_cb(LOG_LV_INF,   __FILE__, __LINE__, "INFO" , __VA_ARGS__)
#define LOGI0(...)                  g_logging_cb(LOG_LV_INF+0,  __FILE__, __LINE__, "INFO"  , __VA_ARGS__)
#define LOGI1(...)                  g_logging_cb(LOG_LV_INF+1,  __FILE__, __LINE__, "INFO"  , __VA_ARGS__)
#define LOGI0K(_KEY_, ...)          g_logging_cb(LOG_LV_INF+0,  __FILE__, __LINE__, _KEY_  , __VA_ARGS__)
#define LOGI1K(_KEY_, ...)          g_logging_cb(LOG_LV_INF+1,  __FILE__, __LINE__, _KEY_  , __VA_ARGS__)
#define LOGD(...)                   g_logging_cb(LOG_LV_DBG,   __FILE__, __LINE__, "DEBUG", __VA_ARGS__)
#define LOGV(...)                   g_logging_cb(LOG_LV_DBG_VERBOSE, __FILE__, __LINE__, "VERBOS", __VA_ARGS__)

#endif /* _LOGGER__H_ */
