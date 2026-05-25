#include "ax_process_utils.h"

#include <android-base/properties.h>
#include <android/log.h>
#include <dirent.h>
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
static cpu_set_t g_balanced_cpu_set;
static cpu_set_t g_prime_cpu_set;
static std::mutex g_init_mutex;
static bool g_cpu_sets_initialized = false;

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
            while (*token && *token != ',') token++;
            continue;
        }
        if (end >= CPU_SETSIZE) end = CPU_SETSIZE - 1;

        for (unsigned int i = start; i <= end; ++i)
            CPU_SET(i, cpu_set);

        while (*token && *token != ',') token++;
        if (*token == ',') token++;
    }
    return true;
}

static void initialize_cpuset() {
    CPU_ZERO(&g_small_cpu_set);
    CPU_ZERO(&g_big_cpu_set);
    CPU_ZERO(&g_all_cpu_set);
    CPU_ZERO(&g_balanced_cpu_set);
    CPU_ZERO(&g_prime_cpu_set);

    std::string small_str = android::base::GetProperty("persist.sys.axion_cpu_small", "0,1,2,3");
    std::string big_str   = android::base::GetProperty("persist.sys.axion_cpu_big", "4,5,6,7");
    std::string prime_str = android::base::GetProperty("persist.sys.axion_cpu_prime", "");

    if (!prime_str.empty()) {
        ParseCpuset(prime_str, &g_prime_cpu_set);
        big_str += "," + prime_str;
    }

    ParseCpuset(small_str, &g_small_cpu_set);
    ParseCpuset(big_str, &g_big_cpu_set);

    int max_cpus = sysconf(_SC_NPROCESSORS_ONLN);
    for (int i = 0; i < max_cpus && i < CPU_SETSIZE; ++i)
        CPU_SET(i, &g_all_cpu_set);

    if (CPU_COUNT(&g_small_cpu_set) == 0) {
        g_small_cpu_set = g_all_cpu_set;
    }
    if (CPU_COUNT(&g_big_cpu_set) < 2) {
        g_big_cpu_set = g_all_cpu_set;
    }
    if (CPU_COUNT(&g_prime_cpu_set) < 2) {
        g_prime_cpu_set = g_big_cpu_set;
    }

    int small_count = 0;
    int big_count = 0;
    
    for (int i = 0; i < CPU_SETSIZE; ++i) {
        if (CPU_ISSET(i, &g_small_cpu_set)) small_count++;
        if (CPU_ISSET(i, &g_big_cpu_set)) big_count++;
    }
    
    int small_to_include = (small_count + 1) / 2;
    int big_to_include = (big_count + 1) / 2;
    
    if (small_count > 0 && small_to_include == 0) small_to_include = 1;
    if (big_count > 0 && big_to_include == 0) big_to_include = 1;
    
    int added = 0;
    for (int i = 0; i < CPU_SETSIZE && added < small_to_include; ++i) {
        if (CPU_ISSET(i, &g_small_cpu_set)) {
            CPU_SET(i, &g_balanced_cpu_set);
            added++;
        }
    }
    
    added = 0;
    for (int i = 0; i < CPU_SETSIZE && added < big_to_include; ++i) {
        if (CPU_ISSET(i, &g_big_cpu_set)) {
            CPU_SET(i, &g_balanced_cpu_set);
            added++;
        }
    }
    
    int balanced_count = 0;
    for (int i = 0; i < CPU_SETSIZE; ++i) {
        if (CPU_ISSET(i, &g_balanced_cpu_set)) balanced_count++;
    }
    if (balanced_count == 0) {
        g_balanced_cpu_set = g_all_cpu_set;
    }

    ALOGV("CPU sets initialized: small=[%s](%d cores), big=[%s](%d cores), balanced=(%d cores), total_cpus=%d",
          small_str.c_str(), small_count, big_str.c_str(), big_count, balanced_count, max_cpus);
}

static const cpu_set_t* get_target_cpuset(int group) {
    const cpu_set_t* target = nullptr;
    
    switch (group) {
        case 0:
            target = &g_big_cpu_set;
            break;
        case 1:
            target = &g_small_cpu_set;
            break;
        case 2:
            target = &g_all_cpu_set;
            break;
        case 3:
            target = &g_balanced_cpu_set;
            break;
        case 4:
            target = &g_prime_cpu_set;
            break;
        default:
            return nullptr;
    }
    
    return target;
}

static bool build_cpuset(const cpu_set_t* source, cpu_set_t* target, int length) {
    CPU_ZERO(target);
    
    if (length <= 0) {
        *target = *source;
        return true;
    }
    
    int count = 0;
    for (int i = 0; i < CPU_SETSIZE; ++i) {
        if (CPU_ISSET(i, source)) {
            CPU_SET(i, target);
            count++;
            if (count >= length) break;
        }
    }
    
    return count > 0;
}

static bool set_thread_affinity(int tid, const cpu_set_t* cpu_set) {
    if (sched_setaffinity(tid, sizeof(cpu_set_t), cpu_set) == -1) {
        ALOGE("Failed to set CPU affinity for thread %d: %s", tid, strerror(errno));
        return false;
    }
    return true;
}

static bool is_process_id(int tid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/status", tid);
    FILE* f = fopen(path, "r");
    if (!f) return false;
    
    char line[256];
    int pid = -1, tgid = -1;
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "Pid:\t%d", &pid) == 1) continue;
        if (sscanf(line, "Tgid:\t%d", &tgid) == 1) break;
    }
    fclose(f);
    
    return (pid > 0 && tgid > 0 && pid == tgid);
}

static bool set_process_affinity(int pid, const cpu_set_t* cpu_set) {
    if (!is_process_id(pid)) {
        ALOGV("ID %d is a thread ID, setting affinity for single thread only", pid);
        return set_thread_affinity(pid, cpu_set);
    }
    
    ALOGV("ID %d is a process ID, setting affinity for all threads", pid);
    
    char task_path[64];
    snprintf(task_path, sizeof(task_path), "/proc/%d/task", pid);
    DIR* task_dir = opendir(task_path);
    
    if (task_dir == nullptr) {
        return set_thread_affinity(pid, cpu_set);
    }
    
    bool success = true;
    struct dirent* entry;
    
    while ((entry = readdir(task_dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        
        int thread_id = atoi(entry->d_name);
        if (thread_id > 0) {
            if (!set_thread_affinity(thread_id, cpu_set)) {
                success = false;
            }
#if DEBUG_BUILD
            else {
                ALOGV("Set affinity for thread %d in process %d", thread_id, pid);
            }
#endif
        }
    }
    
    closedir(task_dir);
    
#if DEBUG_BUILD
    if (success) {
        ALOGV("Successfully set affinity for all threads in process %d", pid);
    }
#endif
    
    return success;
}

static void ensure_cpuset_initialized() {
    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (!g_cpu_sets_initialized) {
        initialize_cpuset();
        g_cpu_sets_initialized = true;
    }
}

bool SetThreadAffinity(int tid, int group) {
    ensure_cpuset_initialized();

    const cpu_set_t* target_cpu_set = get_target_cpuset(group);
    if (!target_cpu_set) return false;
    
    bool result = set_process_affinity(tid, target_cpu_set);
    
#if DEBUG_BUILD
    if (result) {
        const char* group_name = (group == 0) ? "big cores" : 
                                 (group == 1) ? "small cores" : 
                                 (group == 2) ? "all cores" : 
                                 (group == 3) ? "balanced cores" : "unknown";
        ALOGV("Successfully set affinity for thread/process %d to %s", tid, group_name);
    }
#else
    (void)group;
#endif
    
    return result;
}

bool SetThreadAffinity(int tid, int group, int length) {
    ensure_cpuset_initialized();

    const cpu_set_t* source_set = get_target_cpuset(group);
    if (!source_set) return false;
    
    cpu_set_t target_set;
    if (!build_cpuset(source_set, &target_set, length)) {
        ALOGE("No CPUs available for group %d (tid=%d)", group, tid);
        return false;
    }
    
    bool result = set_process_affinity(tid, &target_set);
    
#if DEBUG_BUILD
    if (result) {
        const char* group_name = (group == 0) ? "big cores" : 
                                 (group == 1) ? "small cores" : 
                                 (group == 2) ? "all cores" : 
                                 (group == 3) ? "balanced cores" : "unknown";
        ALOGV("Set affinity for thread/process %d to group=%d (%s) with length=%d",
              tid, group, group_name, length);
    }
#endif
    
    return result;
}

bool SetSingleThreadAffinity(int tid, int group) {
    ensure_cpuset_initialized();

    const cpu_set_t* target_cpu_set = get_target_cpuset(group);
    if (!target_cpu_set) return false;

    return set_thread_affinity(tid, target_cpu_set);
}

bool SetSingleThreadAffinity(int tid, int group, int length) {
    ensure_cpuset_initialized();

    const cpu_set_t* source_set = get_target_cpuset(group);
    if (!source_set) return false;

    cpu_set_t target_set;
    if (!build_cpuset(source_set, &target_set, length)) {
        ALOGE("No CPUs available for group %d (tid=%d)", group, tid);
        return false;
    }

    return set_thread_affinity(tid, &target_set);
}

void RefreshCpuSets() {
    ensure_cpuset_initialized();
}

} // namespace axion::process
