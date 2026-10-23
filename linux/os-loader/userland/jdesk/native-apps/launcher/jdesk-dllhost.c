/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk-dllhost.c — JDesk Windows DLL Host (compiled for Wine/PE)
 *
 * Loads a Windows DLL via LoadLibrary and invokes its entry point.
 * Compiled as a Windows PE executable to run under Wine, providing
 * native Windows API access for the loaded DLL.
 *
 * Build (cross-compile for Windows):
 *   x86_64-w64-mingw32-gcc -O2 -o jdesk-dllhost.exe jdesk-dllhost.c
 *
 * Usage (under Wine):
 *   wine jdesk-dllhost.exe <library.dll> [entry_point] [-- args...]
 *
 * If no entry point specified, attempts (in order):
 *   1. DllMain (with DLL_PROCESS_ATTACH)
 *   2. ServiceMain
 *   3. WinMain / wWinMain
 *   4. main / wmain
 *   5. DllRegisterServer
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#include <windows.h>
#include <stdio.h>
#include <string.h>

#define DLLHOST_VERSION "1.0.0"

/* Entry point signatures */
typedef BOOL (WINAPI *DllMain_t)(HINSTANCE, DWORD, LPVOID);
typedef int (*main_t)(int argc, char **argv);
typedef void (WINAPI *ServiceMain_t)(DWORD, LPWSTR*);
typedef int (WINAPI *WinMain_t)(HINSTANCE, HINSTANCE, LPSTR, int);
typedef HRESULT (STDAPICALLTYPE *DllRegisterServer_t)(void);

/* Candidate entry points in priority order */
static const char *candidates[] = {
    "DllMain",
    "ServiceMain",
    "WinMain",
    "wWinMain",
    "main",
    "wmain",
    "DllRegisterServer",
    "plugin_init",
    "jdesk_start",
    NULL
};

static void print_usage(void)
{
    fprintf(stderr,
        "jdesk-dllhost v%s — JDesk Windows DLL Host\n"
        "\n"
        "Usage: jdesk-dllhost.exe <library.dll> [entry_point] [-- args...]\n"
        "\n"
        "Loads a Windows DLL and invokes its entry point under Wine.\n"
        "If no entry point specified, auto-discovers from candidates.\n"
        "\n"
        "Copyright (C) 2026 MEARVK LLC\n",
        DLLHOST_VERSION);
}

int main(int argc, char **argv)
{
    if (argc < 2) {
        print_usage();
        return 1;
    }

    const char *dll_path = argv[1];
    const char *entry_name = NULL;
    int entry_argc = 0;
    char **entry_argv = NULL;

    /* Parse arguments */
    if (argc >= 3 && strcmp(argv[2], "--") != 0) {
        entry_name = argv[2];
    }

    /* Find "--" separator for entry point arguments */
    for (int i = 2; i < argc; i++) {
        if (strcmp(argv[i], "--") == 0 && i + 1 < argc) {
            entry_argc = argc - (i + 1);
            entry_argv = &argv[i + 1];
            break;
        }
    }

    fprintf(stderr,
        "============================================================\n"
        "  JDesk DLL Host v%s\n"
        "============================================================\n"
        "  DLL:    %s\n"
        "  Entry:  %s\n"
        "  Args:   %d\n"
        "============================================================\n",
        DLLHOST_VERSION, dll_path,
        entry_name ? entry_name : "(auto-detect)",
        entry_argc);

    /* Load the DLL */
    HMODULE hDll = LoadLibraryA(dll_path);
    if (!hDll) {
        DWORD err = GetLastError();
        fprintf(stderr, "[jdesk-dllhost] ERROR: LoadLibrary failed (error %lu)\n", err);
        fprintf(stderr, "[jdesk-dllhost] DLL: %s\n", dll_path);
        return 1;
    }

    fprintf(stderr, "[jdesk-dllhost] DLL loaded at %p\n", (void*)hDll);

    /* Resolve entry point */
    FARPROC entry_func = NULL;

    if (entry_name) {
        /* Explicit entry point */
        entry_func = GetProcAddress(hDll, entry_name);
        if (!entry_func) {
            fprintf(stderr, "[jdesk-dllhost] ERROR: Cannot find '%s' in DLL\n", entry_name);
            FreeLibrary(hDll);
            return 1;
        }
        fprintf(stderr, "[jdesk-dllhost] Resolved: %s @ %p\n", entry_name, (void*)entry_func);
    } else {
        /* Auto-detect entry point */
        for (int i = 0; candidates[i] != NULL; i++) {
            entry_func = GetProcAddress(hDll, candidates[i]);
            if (entry_func) {
                entry_name = candidates[i];
                fprintf(stderr, "[jdesk-dllhost] Auto-detected entry: %s @ %p\n",
                    entry_name, (void*)entry_func);
                break;
            }
        }

        if (!entry_func) {
            fprintf(stderr, "[jdesk-dllhost] ERROR: No known entry point found in DLL\n");
            fprintf(stderr, "[jdesk-dllhost] Tried: DllMain, ServiceMain, WinMain, main, ...\n");
            FreeLibrary(hDll);
            return 1;
        }
    }

    /* Invoke based on entry point type */
    int result = 0;

    fprintf(stderr, "[jdesk-dllhost] Invoking: %s\n", entry_name);
    fprintf(stderr, "[jdesk-dllhost] ──────────────────────────────────────\n");

    if (strcmp(entry_name, "DllMain") == 0) {
        DllMain_t func = (DllMain_t)entry_func;
        BOOL ok = func(hDll, DLL_PROCESS_ATTACH, NULL);
        result = ok ? 0 : 1;

    } else if (strcmp(entry_name, "WinMain") == 0 || strcmp(entry_name, "wWinMain") == 0) {
        WinMain_t func = (WinMain_t)entry_func;
        result = func(GetModuleHandle(NULL), NULL, GetCommandLineA(), SW_SHOW);

    } else if (strcmp(entry_name, "DllRegisterServer") == 0) {
        DllRegisterServer_t func = (DllRegisterServer_t)entry_func;
        HRESULT hr = func();
        result = SUCCEEDED(hr) ? 0 : 1;
        fprintf(stderr, "[jdesk-dllhost] DllRegisterServer returned: 0x%08lX\n", (unsigned long)hr);

    } else if (strcmp(entry_name, "main") == 0 || strcmp(entry_name, "wmain") == 0 ||
               strcmp(entry_name, "plugin_init") == 0 || strcmp(entry_name, "jdesk_start") == 0) {
        main_t func = (main_t)entry_func;
        result = func(entry_argc, entry_argv);

    } else {
        /* Generic — try as int func(int, char**) */
        main_t func = (main_t)entry_func;
        result = func(entry_argc, entry_argv);
    }

    fprintf(stderr, "[jdesk-dllhost] ──────────────────────────────────────\n");
    fprintf(stderr, "[jdesk-dllhost] Entry returned: %d\n", result);

    /* Cleanup */
    FreeLibrary(hDll);

    fprintf(stderr,
        "============================================================\n"
        "  JDesk DLL Host — Shutdown Complete (exit %d)\n"
        "============================================================\n",
        result);

    return result;
}
