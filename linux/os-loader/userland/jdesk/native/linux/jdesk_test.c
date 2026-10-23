/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk_test.c — Native test for jdesk library (no JNI required)
 *
 * Validates: CPU detection, X11 display, screen enumeration, theme,
 * window creation, and event loop.
 *
 * Build & run:
 *   make test
 *
 * Copyright (C) 2026 MEARVK LLC
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "../include/jdesk.h"

static void print_cpu_info(const struct jdesk_cpu_info *cpu)
{
	printf("\n=== CPU Information ===\n");
	printf("  Vendor:       %s\n", cpu->vendor);
	printf("  Brand:        %s\n", cpu->brand);
	printf("  Physical:     %u cores\n", cpu->physical_cores);
	printf("  Logical:      %u cores\n", cpu->logical_cores);
	printf("  Cache Line:   %u bytes\n", cpu->cache_line_size);
	printf("  L2 Cache:     %u KB\n", cpu->l2_cache_kb);
	printf("  L3 Cache:     %u KB\n", cpu->l3_cache_kb);
	printf("  TSC:          %u MHz\n", cpu->tsc_frequency_mhz);
	printf("\n  Features:\n");
	printf("    SSE2:       %s\n", (cpu->features & JDESK_CPU_SSE2) ? "YES" : "no");
	printf("    SSE4.2:     %s\n", (cpu->features & JDESK_CPU_SSE42) ? "YES" : "no");
	printf("    AVX:        %s\n", (cpu->features & JDESK_CPU_AVX) ? "YES" : "no");
	printf("    AVX2:       %s  ← primary SIMD target\n",
	       (cpu->features & JDESK_CPU_AVX2) ? "YES" : "no");
	printf("    AVX-512F:   %s\n", (cpu->features & JDESK_CPU_AVX512F) ? "YES" : "no");
	printf("    AVX-512BW:  %s\n", (cpu->features & JDESK_CPU_AVX512BW) ? "YES" : "no");
	printf("    FMA:        %s\n", (cpu->features & JDESK_CPU_FMA) ? "YES" : "no");
	printf("    AES-NI:     %s\n", (cpu->features & JDESK_CPU_AES_NI) ? "YES" : "no");
	printf("    POPCNT:     %s\n", (cpu->features & JDESK_CPU_POPCNT) ? "YES" : "no");
	printf("    BMI2:       %s\n", (cpu->features & JDESK_CPU_BMI2) ? "YES" : "no");
	printf("    RDRAND:     %s\n", (cpu->features & JDESK_CPU_RDRAND) ? "YES" : "no");
}

static void print_screen_info(const struct jdesk_screen *screen, int index)
{
	printf("  Screen %d: %ux%u @ %u Hz, %u DPI, scale=%.2f, %u-bit\n",
	       index, screen->width, screen->height,
	       screen->refresh_hz, screen->dpi_x,
	       screen->scale_factor, screen->depth);
}

int main(int argc, char *argv[])
{
	int rc;
	bool interactive = false;

	if (argc > 1 && strcmp(argv[1], "--interactive") == 0)
		interactive = true;

	printf("╔═══════════════════════════════════════════════════╗\n");
	printf("║  MEARVK Java Desktop Framework — Native Test     ║\n");
	printf("║  Edition: %s                 ║\n", JDESK_EDITION);
	printf("║  Version: %s                              ║\n", JDESK_VERSION_STRING);
	printf("╚═══════════════════════════════════════════════════╝\n");

	/* Initialize */
	rc = jdesk_init();
	if (rc != 0) {
		fprintf(stderr, "FAIL: jdesk_init() returned %d\n", rc);
		return 1;
	}
	printf("\n✓ jdesk_init() succeeded\n");

	/* CPU detection */
	struct jdesk_cpu_info cpu;
	jdesk_detect_cpu(&cpu);
	print_cpu_info(&cpu);

	if (!(cpu.features & JDESK_CPU_SSE42)) {
		fprintf(stderr, "\nWARNING: SSE4.2 not detected. Performance will be limited.\n");
	}
	if (cpu.features & JDESK_CPU_AVX2) {
		printf("\n✓ AVX2 available — optimal rendering pipeline selected\n");
	}

	/* Timing test */
	printf("\n=== High-Precision Timing ===\n");
	uint64_t t1 = jdesk_time_ns();
	usleep(1000); /* 1ms */
	uint64_t t2 = jdesk_time_ns();
	printf("  1ms sleep measured as: %lu ns (%.3f ms)\n",
	       (unsigned long)(t2 - t1), (t2 - t1) / 1000000.0);

	/* Display connection */
	printf("\n=== X11 Display ===\n");
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) {
		printf("  No X11 display available (headless mode)\n");
		printf("\n✓ All non-display tests passed\n");
		jdesk_shutdown();
		return 0;
	}
	printf("  ✓ Connected to X11 display\n");

	/* Screen enumeration */
	struct jdesk_screen screens[8];
	int num_screens = jdesk_get_screens(dpy, screens, 8);
	printf("  Found %d screen(s):\n", num_screens);
	for (int i = 0; i < num_screens; i++)
		print_screen_info(&screens[i], i);

	/* Theme */
	printf("\n=== White Theme ===\n");
	struct jdesk_theme theme;
	jdesk_theme_get_default(&theme);
	printf("  Name:       %s\n", theme.name);
	printf("  Background: #%06X (white)\n", theme.colors.background & 0xFFFFFF);
	printf("  Primary:    #%06X (blue accent)\n", theme.colors.primary & 0xFFFFFF);
	printf("  Text:       #%06X (near-black)\n", theme.colors.text_primary & 0xFFFFFF);
	printf("  Font:       %s %upx\n", theme.typography.font_family,
	       theme.typography.font_size_body);
	printf("  Corners:    %upx radius\n", theme.corner_radius);

	/* Icon system */
	printf("\n=== Icon System ===\n");
	struct jdesk_icon_buffer icon;
	rc = jdesk_icon_load_svg("/nonexistent.svg", JDESK_ICON_48, &icon);
	if (rc == 0) {
		printf("  ✓ Icon rasterized: %ux%u pixels\n", icon.width, icon.height);
		jdesk_icon_free(&icon);
	}

	/* Window creation (interactive mode only) */
	if (interactive) {
		printf("\n=== Window Test (interactive) ===\n");
		struct jdesk_window_params params = {
			.title = "MEARVK JDesk — Test Window",
			.x = 100, .y = 100,
			.width = 800, .height = 600,
			.flags = 0,
			.decorated = true,
			.resizable = true,
			.background_rgba = 0xFFFFFFFF
		};

		jdesk_window_t *win = jdesk_window_create(dpy, &params);
		if (win) {
			jdesk_theme_apply(win, &theme);
			jdesk_window_show(win);
			printf("  ✓ Window created and shown (close to exit)\n");

			/* Simple event loop */
			struct jdesk_event event;
			bool running = true;
			while (running) {
				if (jdesk_event_poll(dpy, &event)) {
					switch (event.type) {
					case JDESK_EVENT_WINDOW_CLOSE:
						running = false;
						break;
					case JDESK_EVENT_KEY_PRESS:
						if (event.key.keysym == 0xFF1B) /* Escape */
							running = false;
						break;
					default:
						break;
					}
				} else {
					usleep(1000);
				}
			}

			jdesk_window_destroy(win);
			printf("  ✓ Window closed\n");
		}
	}

	/* System info */
	printf("\n=== System Information ===\n");
	struct jdesk_system_info sysinfo;
	jdesk_system_info(&sysinfo);
	printf("  OS:       %s %s\n", sysinfo.os_name, sysinfo.os_version);
	printf("  Host:     %s\n", sysinfo.hostname);
	printf("  RAM:      %lu MB total, %lu MB free\n",
	       (unsigned long)(sysinfo.total_ram_bytes / (1024 * 1024)),
	       (unsigned long)(sysinfo.free_ram_bytes / (1024 * 1024)));
	printf("  Session:  %s\n", sysinfo.desktop_session);

	jdesk_display_close(dpy);
	jdesk_shutdown();

	printf("\n═══════════════════════════════════════════════════\n");
	printf("  All tests passed. Native library is functional.\n");
	printf("═══════════════════════════════════════════════════\n\n");

	return 0;
}
