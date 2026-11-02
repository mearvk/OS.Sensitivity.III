# This warning is emitted by Clang for no good reason, given that it doesn't
# impact compilation in any way, and produces a lot of log spam.
add_compile_options(-Wno-undef-prefix)

add_definitions(-DTARGET_OS_MACOSX=1)

if(PUREDARWIN_ENABLE_KERNEL)
    add_subdirectory(Kernel)
endif()

if(PUREDARWIN_PREBUILT_LIBSYSTEM_ROOT)
    include(${CMAKE_SOURCE_DIR}/cmake/prebuilt_libsystem.cmake)
elseif(PUREDARWIN_ENABLE_LIBRARIES)
    add_subdirectory(Libraries)
endif()

if(PUREDARWIN_ENABLE_USERSPACE)
    add_subdirectory(Userspace)
endif()
