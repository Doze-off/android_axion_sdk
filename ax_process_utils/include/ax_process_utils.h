#pragma once
#include <processgroup/sched_policy.h>
#include <sched.h>
#include <sys/types.h>
#include <string>
#include <string_view>

namespace axion::process {

constexpr int kCpuGroupBig = 0;
constexpr int kCpuGroupSmall = 1;
constexpr int kCpuGroupAll = 2;
constexpr int kCpuGroupBalanced = 3;
constexpr int kCpuGroupPrime = 4;

bool ParseCpuset(const std::string& cpus, cpu_set_t* cpu_set);
bool SetThreadAffinity(int tid, int group);
bool SetThreadAffinity(int tid, int group, int length);
bool SetSingleThreadAffinity(int tid, int group);
bool SetSingleThreadAffinity(int tid, int group, int length);
bool SetThreadPriority(int tid, int priority);
bool SetCurrentThreadPriority(int priority);
bool SetThreadCpusetPolicy(int tid, SchedPolicy policy);
bool SetCurrentThreadCpusetPolicy(SchedPolicy policy);
bool SetThreadSchedPolicy(int tid, SchedPolicy policy);
bool SetCurrentThreadSchedPolicy(SchedPolicy policy);
bool SetThreadCgroupPolicy(int tid, SchedPolicy policy);
bool SetCurrentThreadCgroupPolicy(SchedPolicy policy);
bool SetThreadPolicy(int tid, SchedPolicy policy, int priority);
bool SetCurrentThreadPolicy(SchedPolicy policy, int priority);
bool SetThreadForegroundPolicy(int tid);
bool SetCurrentThreadForegroundPolicy();
bool SetThreadForegroundWindowPolicy(int tid);
bool SetCurrentThreadForegroundWindowPolicy();
bool SetThreadBackgroundPolicy(int tid);
bool SetCurrentThreadBackgroundPolicy();
bool SetThreadProfile(int tid, std::string_view profile, bool useFdCache = false);
bool SetProcessProfile(uid_t uid, pid_t pid, std::string_view profile);
bool SetThreadCpusetProfile(int tid, SchedPolicy policy, bool useFdCache = true);
bool SetProcessCpusetProfile(uid_t uid, pid_t pid, SchedPolicy policy);
bool SetThreadSchedProfile(int tid, SchedPolicy policy, bool useFdCache = true);
bool SetProcessSchedProfile(uid_t uid, pid_t pid, SchedPolicy policy);
void RefreshCpuSets();

} // namespace axion::process
