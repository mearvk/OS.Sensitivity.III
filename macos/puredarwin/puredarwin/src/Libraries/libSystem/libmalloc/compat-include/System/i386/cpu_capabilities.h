/* PureDarwin compat redirect for split userspace builds. */
#ifndef PRIVATE
#define PUREDARWIN_DEFINED_PRIVATE_FOR_CPU_CAPABILITIES 1
#define PRIVATE 1
#endif

#include <i386/cpu_capabilities.h>

#ifdef PUREDARWIN_DEFINED_PRIVATE_FOR_CPU_CAPABILITIES
#undef PRIVATE
#undef PUREDARWIN_DEFINED_PRIVATE_FOR_CPU_CAPABILITIES
#endif
