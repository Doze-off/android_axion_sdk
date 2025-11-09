#include "ax_process_utils.h"

#include <android-base/properties.h>
#include <android/log.h>
#include <errno.h>
#include <string.h>
#include <sys/sysinfo.h>
#include <unistd.h>
#include <mutex>
#include <vector>

#define DEBUG_BUILD 0

#define LOG_TAG "AxProcessUtils"

#if DEBUG_BUILD
  #define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
  #define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#else
  #define ALOGE(...) ((void)0)
  #define ALOGV(...) ((void)0)
#endif

namespace axion::process {

static cpu_set_t g_small_cpu_set;
static cpu_set_t g_big_cpu_set;
static cpu_set_t g_all_cpu_set;
static std::once_flag g_cpu_sets_initialized;

bool ParseCpuset(const std::string& cpus, cpu_set_t* cpu_set) {
    if (!cpu_set) return false;
    CPU_ZERO(cpu_set);

    unsigned int start = 0, end = 0;
    const char* token = cpus.c_str();

    while (*token) {
        while (*token == ',' || *token == ' ') token++;
        if (!*token) break;

        int matched = sscanf(token, "%u-%u", &start, &end);
        if (matched <= 0) break;
        if (matched == 1) end = start;

        if (start >= CPU_SETSIZE) {
            ALOGE("Ignoring CPU %u (>= CPU_SETSIZE)", start);
            continue;
        }
        if (end >= CPU_SETSIZE) end = CPU_SETSIZE - 1;

        for (unsigned int i = start; i <= end; ++i)
            CPU_SET(i, cpu_set);

        token = strchr(token, ',');
        if (!token) break;
        token++;
    }
    return true;
}

static void initialize_cpuset() {
    CPU_ZERO(&g_small_cpu_set);
    CPU_ZERO(&g_big_cpu_set);
    CPU_ZERO(&g_all_cpu_set);

    std::string small_str = android::base::GetProperty("persist.sys.axion_cpu_small", "0,1,2,3");
    std::string big_str   = android::base::GetProperty("persist.sys.axion_cpu_big", "4,5,6,7");
    std::string prime_str = android::base::GetProperty("persist.sys.axion_cpu_prime", "");

    if (!prime_str.empty()) {
        big_str += "," + prime_str;
    }

    ParseCpuset(small_str, &g_small_cpu_set);
    ParseCpuset(big_str, &g_big_cpu_set);

    int max_cpus = sysconf(_SC_NPROCESSORS_ONLN);
    for (int i = 0; i < max_cpus && i < CPU_SETSIZE; ++i)
        CPU_SET(i, &g_all_cpu_set);

    ALOGV("CPU sets initialized: small=[%s], big=[%s], total_cpus=%d",
          small_str.c_str(), big_str.c_str(), max_cpus);
}

bool SetThreadAffinity(int tid, int group) {
    std::call_once(g_cpu_sets_initialized, initialize_cpuset);

    const cpu_set_t* target_cpu_set = nullptr;
#if DEBUG_BUILD
    const char* group_name = nullptr;
#endif

    switch (group) {
        case 1:
            target_cpu_set = &g_small_cpu_set;
#if DEBUG_BUILD
            group_name = "small cores";
#endif
            break;
        case 0:
            target_cpu_set = &g_big_cpu_set;
#if DEBUG_BUILD
            group_name = "big cores";
#endif
            break;
        case 2:
            target_cpu_set = &g_all_cpu_set;
#if DEBUG_BUILD
            group_name = "all cores";
#endif
            break;
        default:
            ALOGE("Invalid CPU group %d for thread %d", group, tid);
            return false;
    }

    if (sched_setaffinity(tid, sizeof(cpu_set_t), target_cpu_set) == -1) {
        ALOGE("Failed to set CPU affinity for thread %d: %s",
              tid, strerror(errno));
        return false;
    }

#if DEBUG_BUILD
    ALOGV("Successfully set affinity for thread %d to %s", tid, group_name);
#else
    (void)group;
#endif

    return true;
}

} // namespace axion::process
