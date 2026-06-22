#include "ax_process_utils.h"

#include <android-base/properties.h>
#include <android/log.h>
#include <processgroup/processgroup.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <sys/resource.h>
#include <unistd.h>

#include <cutils/android_filesystem_config.h>
#include <system/thread_defs.h>

#include <string>
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
namespace {

bool IsTaskProfileWriteAllowedProcess() {
    FILE* file = fopen("/proc/self/cmdline", "re");
    if (file == nullptr) {
        return false;
    }

    char cmdline[64] = {};
    const size_t length = fread(cmdline, 1, sizeof(cmdline) - 1, file);
    fclose(file);
    if (length == 0) {
        return false;
    }

    const std::string_view name(cmdline, strnlen(cmdline, sizeof(cmdline)));
    return name == "system_server" || name == "surfaceflinger"
            || name == "/system/bin/surfaceflinger";
}

bool CanUseTaskProfileWrites() {
    static const bool canUse = [] {
        const uid_t uid = getuid();
        return uid == AID_ROOT || uid == AID_GRAPHICS ||
                (uid == AID_SYSTEM && IsTaskProfileWriteAllowedProcess());
    }();
    return canUse;
}

std::string DeriveCpusByCapacity(bool want_prime) {
    const int cpu_count = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    if (cpu_count <= 0 || cpu_count > CPU_SETSIZE) return "";

    std::vector<int> caps(cpu_count, -1);
    int min_cap = 0;
    int max_cap = 0;
    bool seen = false;
    for (int cpu = 0; cpu < cpu_count; ++cpu) {
        char path[96];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpu_capacity", cpu);
        FILE* file = fopen(path, "r");
        if (file == nullptr) continue;
        int cap = 0;
        if (fscanf(file, "%d", &cap) == 1 && cap > 0) {
            caps[cpu] = cap;
            if (!seen) {
                min_cap = max_cap = cap;
                seen = true;
            } else {
                if (cap < min_cap) min_cap = cap;
                if (cap > max_cap) max_cap = cap;
            }
        }
        fclose(file);
    }

    if (!seen || max_cap <= 0 || min_cap == max_cap) return "";

    std::string result;
    for (int cpu = 0; cpu < cpu_count; ++cpu) {
        if (caps[cpu] < 0) continue;
        const bool match =
                want_prime ? (caps[cpu] == max_cap) : (caps[cpu] > min_cap);
        if (!match) continue;
        if (!result.empty()) result += ",";
        result += std::to_string(cpu);
    }
    return result;
}

class CpuTopology {
public:
    CpuTopology() { Init(); }

    const cpu_set_t* MaskForGroup(int group) const {
        switch (group) {
            case kCpuGroupBig: return &big_;
            case kCpuGroupSmall: return &small_;
            case kCpuGroupAll: return &all_;
            case kCpuGroupBalanced: return &balanced_;
            case kCpuGroupPrime: return &prime_;
            default: return nullptr;
        }
    }

private:
    void Init();

    cpu_set_t small_;
    cpu_set_t big_;
    cpu_set_t all_;
    cpu_set_t balanced_;
    cpu_set_t prime_;
};

void CpuTopology::Init() {
    CPU_ZERO(&small_);
    CPU_ZERO(&big_);
    CPU_ZERO(&all_);
    CPU_ZERO(&balanced_);
    CPU_ZERO(&prime_);

    std::string small_str =
            android::base::GetProperty("persist.sys.axion_cpu_small", "0,1,2,3");
    std::string big_str =
            android::base::GetProperty("persist.sys.axion_cpu_big", "");
    if (big_str.empty()) {
        big_str = android::base::GetProperty("persist.sys.axion_cpu_svp", "");
    }
    if (big_str.empty()) {
        big_str = DeriveCpusByCapacity(false);
    }
    if (big_str.empty()) {
        big_str = "4,5,6,7";
    }
    std::string prime_str =
            android::base::GetProperty("persist.sys.axion_cpu_prime", "");
    if (prime_str.empty()) {
        prime_str = DeriveCpusByCapacity(true);
    }

    if (!prime_str.empty()) {
        ParseCpuset(prime_str, &prime_);
        big_str += "," + prime_str;
    }

    ParseCpuset(small_str, &small_);
    ParseCpuset(big_str, &big_);

    const int cpu_count = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    for (int cpu = 0; cpu < cpu_count && cpu < CPU_SETSIZE; ++cpu) {
        CPU_SET(cpu, &all_);
    }

    if (CPU_COUNT(&small_) == 0) small_ = all_;
    if (CPU_COUNT(&big_) < 2) big_ = all_;
    if (CPU_COUNT(&prime_) == 0) prime_ = big_;

    int small_count = 0;
    int big_count = 0;
    for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
        if (CPU_ISSET(cpu, &small_)) ++small_count;
        if (CPU_ISSET(cpu, &big_)) ++big_count;
    }

    int small_to_include = (small_count + 1) / 2;
    int big_to_include = (big_count + 1) / 2;
    if (small_count > 0 && small_to_include == 0) small_to_include = 1;
    if (big_count > 0 && big_to_include == 0) big_to_include = 1;

    int added = 0;
    for (int cpu = 0; cpu < CPU_SETSIZE && added < small_to_include; ++cpu) {
        if (CPU_ISSET(cpu, &small_)) {
            CPU_SET(cpu, &balanced_);
            ++added;
        }
    }
    added = 0;
    for (int cpu = 0; cpu < CPU_SETSIZE && added < big_to_include; ++cpu) {
        if (CPU_ISSET(cpu, &big_)) {
            CPU_SET(cpu, &balanced_);
            ++added;
        }
    }

    if (CPU_COUNT(&balanced_) == 0) balanced_ = all_;

    ALOGV("CPU sets: small=[%s](%d) big=[%s](%d) balanced=(%d) cpus=%d",
          small_str.c_str(), small_count, big_str.c_str(), big_count,
          CPU_COUNT(&balanced_), cpu_count);
}

const CpuTopology& Topology() {
    static const CpuTopology topology;
    return topology;
}

bool SetAffinityForThread(int tid, const cpu_set_t* mask) {
    if (sched_setaffinity(tid, sizeof(cpu_set_t), mask) == -1) {
        ALOGE("sched_setaffinity tid=%d failed: %s", tid, strerror(errno));
        return false;
    }
    return true;
}

bool IsProcessId(int tid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/status", tid);
    FILE* file = fopen(path, "r");
    if (file == nullptr) return false;

    char line[256];
    int pid = -1;
    int tgid = -1;
    while (fgets(line, sizeof(line), file) != nullptr) {
        if (sscanf(line, "Pid:\t%d", &pid) == 1) continue;
        if (sscanf(line, "Tgid:\t%d", &tgid) == 1) break;
    }
    fclose(file);
    return pid > 0 && tgid > 0 && pid == tgid;
}

bool SetAffinityForTask(int pid, const cpu_set_t* mask) {
    if (!IsProcessId(pid)) {
        return SetAffinityForThread(pid, mask);
    }

    char task_path[64];
    snprintf(task_path, sizeof(task_path), "/proc/%d/task", pid);
    DIR* task_dir = opendir(task_path);
    if (task_dir == nullptr) {
        return SetAffinityForThread(pid, mask);
    }

    bool success = true;
    for (struct dirent* entry = readdir(task_dir); entry != nullptr;
         entry = readdir(task_dir)) {
        if (entry->d_name[0] == '.') continue;
        const int thread_id = atoi(entry->d_name);
        if (thread_id > 0 && !SetAffinityForThread(thread_id, mask)) {
            success = false;
        }
    }
    closedir(task_dir);
    return success;
}

bool BuildBoundedMask(const cpu_set_t* source, cpu_set_t* target, int length) {
    CPU_ZERO(target);
    if (length <= 0) {
        *target = *source;
        return true;
    }
    int count = 0;
    for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
        if (CPU_ISSET(cpu, source)) {
            CPU_SET(cpu, target);
            if (++count >= length) break;
        }
    }
    return count > 0;
}

}

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

bool SetThreadAffinity(int tid, int group) {
    const cpu_set_t* mask = Topology().MaskForGroup(group);
    if (mask == nullptr) return false;
    return SetAffinityForTask(tid, mask);
}

bool SetThreadAffinity(int tid, int group, int length) {
    const cpu_set_t* source = Topology().MaskForGroup(group);
    if (source == nullptr) return false;

    cpu_set_t mask;
    if (!BuildBoundedMask(source, &mask, length)) {
        ALOGE("No CPUs available for group %d (tid=%d)", group, tid);
        return false;
    }
    return SetAffinityForTask(tid, &mask);
}

bool SetSingleThreadAffinity(int tid, int group) {
    const cpu_set_t* mask = Topology().MaskForGroup(group);
    if (mask == nullptr) return false;
    return SetAffinityForThread(tid, mask);
}

bool SetSingleThreadAffinity(int tid, int group, int length) {
    const cpu_set_t* source = Topology().MaskForGroup(group);
    if (source == nullptr) return false;

    cpu_set_t mask;
    if (!BuildBoundedMask(source, &mask, length)) {
        ALOGE("No CPUs available for group %d (tid=%d)", group, tid);
        return false;
    }
    return SetAffinityForThread(tid, &mask);
}

bool SetThreadPriority(int tid, int priority) {
    return tid >= 0 && setpriority(PRIO_PROCESS, tid, priority) == 0;
}

bool SetCurrentThreadPriority(int priority) {
    return SetThreadPriority(gettid(), priority);
}

bool SetThreadCpusetPolicy(int tid, SchedPolicy policy) {
    return tid >= 0 && CanUseTaskProfileWrites() && set_cpuset_policy(tid, policy) == 0;
}

bool SetCurrentThreadCpusetPolicy(SchedPolicy policy) {
    return SetThreadCpusetPolicy(gettid(), policy);
}

bool SetThreadSchedPolicy(int tid, SchedPolicy policy) {
    return tid >= 0 && CanUseTaskProfileWrites() && set_sched_policy(tid, policy) == 0;
}

bool SetCurrentThreadSchedPolicy(SchedPolicy policy) {
    return SetThreadSchedPolicy(gettid(), policy);
}

bool SetThreadCgroupPolicy(int tid, SchedPolicy policy) {
    if (tid < 0) return false;

    SchedPolicy previous = SP_DEFAULT;
    const bool hasPrevious = get_sched_policy(tid, &previous) == 0;
    if (!SetThreadSchedPolicy(tid, policy)) return false;
    if (SetThreadCpusetPolicy(tid, policy)) return true;
    if (hasPrevious) SetThreadSchedPolicy(tid, previous);
    return false;
}

bool SetCurrentThreadCgroupPolicy(SchedPolicy policy) {
    return SetThreadCgroupPolicy(gettid(), policy);
}

bool SetThreadPolicy(int tid, SchedPolicy policy, int priority) {
    if (tid < 0 || !CanUseTaskProfileWrites()) return false;

    bool success = true;
    if (!SetThreadCgroupPolicy(tid, policy)) success = false;
    if (!SetThreadPriority(tid, priority)) success = false;
    return success;
}

bool SetCurrentThreadPolicy(SchedPolicy policy, int priority) {
    return SetThreadPolicy(gettid(), policy, priority);
}

bool SetThreadForegroundPolicy(int tid) {
    return SetThreadPolicy(tid, SP_FOREGROUND, ANDROID_PRIORITY_NORMAL);
}

bool SetCurrentThreadForegroundPolicy() {
    return SetThreadForegroundPolicy(gettid());
}

bool SetThreadForegroundWindowPolicy(int tid) {
    if (tid < 0) return false;
    return SetThreadPolicy(tid, SP_FOREGROUND_WINDOW, ANDROID_PRIORITY_DISPLAY);
}

bool SetCurrentThreadForegroundWindowPolicy() {
    return SetThreadForegroundWindowPolicy(gettid());
}

bool SetThreadBackgroundPolicy(int tid) {
    return SetThreadPolicy(tid, SP_BACKGROUND, ANDROID_PRIORITY_BACKGROUND);
}

bool SetCurrentThreadBackgroundPolicy() {
    return SetThreadBackgroundPolicy(gettid());
}

bool SetThreadProfile(int tid, std::string_view profile, bool useFdCache) {
    if (tid < 0 || profile.empty()) return false;
    if (!CanUseTaskProfileWrites()) return false;
    return SetTaskProfiles(tid, {profile}, useFdCache);
}

bool SetProcessProfile(uid_t uid, pid_t pid, std::string_view profile) {
    if (pid <= 0 || profile.empty()) return false;
    if (!CanUseTaskProfileWrites()) return false;
    return SetProcessProfiles(uid, pid, {profile});
}

static const char* CpusetProfileForPolicy(SchedPolicy policy) {
    return policy == SP_CNT ? nullptr : get_cpuset_policy_profile_name(policy);
}

static const char* SchedProfileForPolicy(SchedPolicy policy) {
    return policy == SP_CNT ? nullptr : get_sched_policy_profile_name(policy);
}

bool SetThreadCpusetProfile(int tid, SchedPolicy policy, bool useFdCache) {
    const char* profile = CpusetProfileForPolicy(policy);
    return profile != nullptr && SetThreadProfile(tid, profile, useFdCache);
}

bool SetProcessCpusetProfile(uid_t uid, pid_t pid, SchedPolicy policy) {
    const char* profile = CpusetProfileForPolicy(policy);
    return profile != nullptr && SetProcessProfile(uid, pid, profile);
}

bool SetThreadSchedProfile(int tid, SchedPolicy policy, bool useFdCache) {
    const char* profile = SchedProfileForPolicy(policy);
    return profile != nullptr && SetThreadProfile(tid, profile, useFdCache);
}

bool SetProcessSchedProfile(uid_t uid, pid_t pid, SchedPolicy policy) {
    const char* profile = SchedProfileForPolicy(policy);
    return profile != nullptr && SetProcessProfile(uid, pid, profile);
}

void RefreshCpuSets() {
    (void)Topology();
}

} // namespace axion::process
