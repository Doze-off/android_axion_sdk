#pragma once
#include <sched.h>
#include <string>

namespace axion::process {

bool ParseCpuset(const std::string& cpus, cpu_set_t* cpu_set);
bool SetThreadAffinity(int tid, int group);
bool SetThreadAffinity(int tid, int group, int length);
void RefreshCpuSets();

} // namespace axion::process
