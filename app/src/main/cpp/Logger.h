#ifndef VRBROWSER_LOGGER_H
#define VRBROWSER_LOGGER_H

#include <android/log.h>
#define VRLOG(format, ...) __android_log_print(ANDROID_LOG_INFO, "VRBrowser", format, ##__VA_ARGS__);
#define VRLINE VRLOG("%s:%s:%d", __FILE__, __FUNCTION__, __LINE__)


#endif //VRBROWSER_LOGGER_H
