/*
 * util.h
 *
 *  Created on: 2014. 2. 21.
 *      Author: PROkY
 */

#ifndef _UTIL__H_
#define _UTIL__H_

#include "common.h"
#include <sys/time.h>
#include <sys/timeb.h>



/**
 * in_time_to_string_milli
 * @return
 */
inline apr_int32_t in_time_to_string_milli(char * buf, apr_size_t size, apr_time_t t) {
    apr_time_t milli = t /1000L;
    apr_time_exp_t timeinfo;
    apr_time_exp_tz(&timeinfo, t, 32400);
    timeinfo.tm_year += 1900;
    timeinfo.tm_mon ++;
    buf[0] = (timeinfo.tm_year / 1000) % 10 + '0' ;
    buf[1] = (timeinfo.tm_year / 100) % 10 + '0';
    buf[2] = (timeinfo.tm_year / 10) % 10 + '0';
    buf[3] = (timeinfo.tm_year % 10) + '0';
    buf[4] = (timeinfo.tm_mon / 10) % 10 + '0';
    buf[5] = (timeinfo.tm_mon % 10) + '0';
    buf[6] = (timeinfo.tm_mday / 10) % 10 + '0';
    buf[7] = (timeinfo.tm_mday % 10) + '0';
    buf[8] = (timeinfo.tm_hour / 10) % 10 + '0';
    buf[9] = (timeinfo.tm_hour % 10) + '0';
    buf[10] = (timeinfo.tm_min / 10) % 10 + '0';
    buf[11] = (timeinfo.tm_min % 10) + '0';
    buf[12] = (timeinfo.tm_sec / 10) % 10 + '0';
    buf[13] = (timeinfo.tm_sec % 10) + '0';
    buf[14] = (milli / 100L) % 10 + '0' ;
    buf[15] = (milli / 10L) % 10 + '0' ;
    buf[16] = (milli % 10L) + '0';
    buf[17] = 0;
    return 17;
}

#endif /* _UTIL__H_ */

