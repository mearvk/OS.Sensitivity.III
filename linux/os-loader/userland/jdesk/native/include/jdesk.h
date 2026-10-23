/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk.h — MEARVK Java Desktop Framework Native Bridge
 *
 * Cross-platform desktop environment framework that closely resembles the
 * Linux kernel + X11 + Desktop profile. Runs on Linux, Windows, macOS.
 * Written for JavaFX full-screen mode with white theme.
 *
 * This header defines the native Linux interface for:
 *   - X11 display connection and full-screen management
 *   - x86_64 processor feature detection (SSE4, AVX2, AVX-512)
 *   - Hardware-accelerated rendering hints to JavaFX
 *   - System tray, taskbar, and window management primitives
 *   - Icon rendering pipeline (SVG → rasterized at native DPI)
 *   - Input event forwarding (keyboard, mouse, touch)
 *
 * Integration target: MEARVK OpenJDK 28 Edition
 * Kept separate from JDK source — loaded via System.loadLibrary("jdesk")
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#ifndef __JDESK_H
#define __JDESK_H

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ===========================================================================
 * Version & Identity
 * ===========================================================================
 */

#define JDESK_VERSION_MAJOR     1
#define JDESK_VERSION_MINOR     0
#define JDESK_VERSION_PATCH     0
#define JDESK_VERSION_STRING    "1.0.0"
#define JDESK_EDITION           "Galactic Cherry Marvell"

/* ===========================================================================
 * x86_64 Processor Feature Detection
 *
 * We detect CPU features at library load time and expose them to Java.
 * JavaFX rendering paths can then select the optimal pipeline:
 *   - SSE4.2:  Baseline vector operations, string processing
 *   - AVX2:    256-bit vector, integer operations, pixel blending
 *   - AVX-512: 512-bit vector, bulk pixel operations (if available)
 *   - AES-NI:  Hardware crypto for secure window compositor
 *   - TSC:     High-precision timing for frame scheduling
 * ===========================================================================
 */

/* CPU feature flags (populated at init) */
#define JDESK_CPU_SSE2          (1 <<  0)
#define JDESK_CPU_SSE3          (1 <<  1)
#define JDESK_CPU_SSSE3         (1 <<  2)
#define JDESK_CPU_SSE41         (1 <<  3)
#define JDESK_CPU_SSE42         (1 <<  4)
#define JDESK_CPU_AVX           (1 <<  5)
#define JDESK_CPU_AVX2          (1 <<  6)
#define JDESK_CPU_AVX512F       (1 <<  7)
#define JDESK_CPU_AVX512BW      (1 <<  8)
#define JDESK_CPU_FMA           (1 <<  9)
#define JDESK_CPU_AES_NI        (1 << 10)
#define JDESK_CPU_POPCNT        (1 << 11)
#define JDESK_CPU_RDTSC         (1 << 12)
#define JDESK_CPU_BMI1          (1 << 13)
#define JDESK_CPU_BMI2          (1 << 14)
#define JDESK_CPU_RDRAND        (1 << 15)

/* CPU topology */
struct jdesk_cpu_info {
	uint32_t features;          /* Bitmask of JDESK_CPU_* flags */
	uint32_t physical_cores;    /* Physical CPU cores */
	uint32_t logical_cores;     /* Logical CPUs (with HT) */
	uint32_t cache_line_size;   /* L1 cache line in bytes (typically 64) */
	uint32_t l1_cache_kb;       /* L1 data cache size */
	uint32_t l2_cache_kb;       /* L2 cache size */
	uint32_t l3_cache_kb;       /* L3 cache size */
	uint32_t tsc_frequency_mhz; /* TSC frequency (for frame timing) */
	char     vendor[16];        /* "GenuineIntel" or "AuthenticAMD" */
	char     brand[64];         /* Full processor brand string */
};

/* ===========================================================================
 * Display & Screen Management
 * ===========================================================================
 */

/* Display connection (wraps X11 Display* on Linux) */
typedef struct jdesk_display jdesk_display_t;

/* Screen geometry */
struct jdesk_screen {
	uint32_t width;             /* Pixels wide */
	uint32_t height;            /* Pixels tall */
	uint32_t dpi_x;             /* Horizontal DPI */
	uint32_t dpi_y;             /* Vertical DPI */
	uint32_t refresh_hz;        /* Refresh rate */
	uint32_t depth;             /* Color depth (bits) */
	double   scale_factor;      /* HiDPI scale (1.0, 1.25, 1.5, 2.0) */
	uint32_t screen_index;      /* Multi-monitor index */
};

/* Full-screen mode flags */
#define JDESK_FULLSCREEN_EXCLUSIVE   (1 << 0)  /* Exclusive (bypass compositor) */
#define JDESK_FULLSCREEN_WINDOWED    (1 << 1)  /* Borderless windowed */
#define JDESK_FULLSCREEN_MULTI       (1 << 2)  /* Span multiple monitors */

/* ===========================================================================
 * Window Management
 * ===========================================================================
 */

/* Window handle */
typedef struct jdesk_window jdesk_window_t;

/* Window creation parameters */
struct jdesk_window_params {
	const char *title;
	uint32_t x, y;
	uint32_t width, height;
	uint32_t flags;             /* JDESK_FULLSCREEN_* */
	bool     decorated;         /* Show window decorations */
	bool     resizable;
	bool     always_on_top;
	uint32_t background_rgba;   /* Default: 0xFFFFFFFF (white) */
};

/* Window state */
enum jdesk_window_state {
	JDESK_WIN_NORMAL = 0,
	JDESK_WIN_MAXIMIZED,
	JDESK_WIN_MINIMIZED,
	JDESK_WIN_FULLSCREEN,
	JDESK_WIN_HIDDEN
};

/* ===========================================================================
 * Desktop Panel (Taskbar/Dock)
 * ===========================================================================
 */

/* Panel position */
enum jdesk_panel_position {
	JDESK_PANEL_TOP = 0,
	JDESK_PANEL_BOTTOM,
	JDESK_PANEL_LEFT,
	JDESK_PANEL_RIGHT
};

/* Panel item (clickable icon on taskbar) */
struct jdesk_panel_item {
	uint32_t id;
	const char *label;          /* Tooltip text */
	const char *icon_path;      /* SVG or PNG icon path */
	bool     highlighted;       /* Active/focused state */
	bool     has_badge;         /* Notification badge */
	uint32_t badge_count;       /* Badge number (0 = dot only) */
};

/* Panel configuration */
struct jdesk_panel_config {
	enum jdesk_panel_position position;
	uint32_t height_px;         /* Panel height (or width if vertical) */
	uint32_t icon_size_px;      /* Icon dimension (square) */
	uint32_t padding_px;        /* Padding between items */
	uint32_t background_rgba;   /* Panel background color */
	bool     auto_hide;
	bool     show_clock;
	bool     show_system_tray;
};

/* ===========================================================================
 * Icon System
 * ===========================================================================
 */

/* Icon sizes (standard grid) */
enum jdesk_icon_size {
	JDESK_ICON_16  = 16,
	JDESK_ICON_24  = 24,
	JDESK_ICON_32  = 32,
	JDESK_ICON_48  = 48,
	JDESK_ICON_64  = 64,
	JDESK_ICON_96  = 96,
	JDESK_ICON_128 = 128,
	JDESK_ICON_256 = 256
};

/* Icon categories (for themed lookup) */
enum jdesk_icon_category {
	JDESK_ICON_CAT_APPS = 0,       /* Application launchers */
	JDESK_ICON_CAT_PLACES,          /* Filesystem locations */
	JDESK_ICON_CAT_DEVICES,         /* Hardware */
	JDESK_ICON_CAT_ACTIONS,         /* Toolbar actions */
	JDESK_ICON_CAT_STATUS,          /* System status */
	JDESK_ICON_CAT_MIMETYPES,       /* File types */
	JDESK_ICON_CAT_EMBLEMS          /* Overlay badges */
};

/* Rasterized icon buffer (passed to JavaFX Image) */
struct jdesk_icon_buffer {
	uint32_t *pixels;           /* ARGB8888 pixel data */
	uint32_t  width;
	uint32_t  height;
	uint32_t  stride;           /* Bytes per row */
};

/* ===========================================================================
 * Theme System (White Theme)
 * ===========================================================================
 */

/* Color scheme */
struct jdesk_theme_colors {
	uint32_t background;        /* Main background: 0xFFFFFFFF (white) */
	uint32_t surface;           /* Card/panel surface: 0xFFF8F9FA */
	uint32_t primary;           /* Primary accent: 0xFF1A73E8 (blue) */
	uint32_t secondary;         /* Secondary: 0xFF5F6368 (grey) */
	uint32_t text_primary;      /* Main text: 0xFF202124 (near-black) */
	uint32_t text_secondary;    /* Subtitle: 0xFF5F6368 */
	uint32_t border;            /* Borders: 0xFFDADCE0 */
	uint32_t hover;             /* Hover state: 0xFFF1F3F4 */
	uint32_t active;            /* Active/pressed: 0xFFE8EAED */
	uint32_t error;             /* Error: 0xFFD93025 */
	uint32_t success;           /* Success: 0xFF1E8E3E */
	uint32_t warning;           /* Warning: 0xFFF9AB00 */
	uint32_t shadow;            /* Drop shadow: 0x1A000000 */
};

/* Typography */
struct jdesk_theme_typography {
	const char *font_family;    /* Default: "Inter" or system sans-serif */
	uint32_t font_size_title;   /* Title: 20px */
	uint32_t font_size_body;    /* Body: 14px */
	uint32_t font_size_caption; /* Caption: 12px */
	uint32_t font_size_panel;   /* Panel/taskbar: 11px */
	uint32_t font_weight_normal;/* 400 */
	uint32_t font_weight_medium;/* 500 */
	uint32_t font_weight_bold;  /* 700 */
};

/* Complete theme specification */
struct jdesk_theme {
	const char *name;           /* "White" */
	struct jdesk_theme_colors colors;
	struct jdesk_theme_typography typography;
	uint32_t corner_radius;     /* Window corners: 8px */
	uint32_t shadow_elevation;  /* Material elevation: 2-8px */
	uint32_t animation_ms;      /* Transition duration: 200ms */
	uint32_t icon_style;        /* 0=filled, 1=outlined, 2=rounded */
};

/* ===========================================================================
 * Input Events
 * ===========================================================================
 */

/* Mouse button codes */
#define JDESK_MOUSE_LEFT         1
#define JDESK_MOUSE_MIDDLE       2
#define JDESK_MOUSE_RIGHT        3
#define JDESK_MOUSE_SCROLL_UP    4
#define JDESK_MOUSE_SCROLL_DOWN  5

/* Key modifier flags */
#define JDESK_MOD_SHIFT          (1 << 0)
#define JDESK_MOD_CTRL           (1 << 1)
#define JDESK_MOD_ALT            (1 << 2)
#define JDESK_MOD_SUPER          (1 << 3)
#define JDESK_MOD_CAPS_LOCK      (1 << 4)

/* Event types */
enum jdesk_event_type {
	JDESK_EVENT_KEY_PRESS = 0,
	JDESK_EVENT_KEY_RELEASE,
	JDESK_EVENT_MOUSE_PRESS,
	JDESK_EVENT_MOUSE_RELEASE,
	JDESK_EVENT_MOUSE_MOVE,
	JDESK_EVENT_MOUSE_SCROLL,
	JDESK_EVENT_WINDOW_RESIZE,
	JDESK_EVENT_WINDOW_CLOSE,
	JDESK_EVENT_WINDOW_FOCUS,
	JDESK_EVENT_WINDOW_UNFOCUS,
	JDESK_EVENT_DISPLAY_CHANGE
};

/* Generic event structure */
struct jdesk_event {
	enum jdesk_event_type type;
	uint64_t timestamp_ns;      /* High-precision timestamp (from TSC) */
	uint32_t modifiers;         /* JDESK_MOD_* */
	union {
		struct { uint32_t keycode; uint32_t keysym; } key;
		struct { int32_t x; int32_t y; uint32_t button; } mouse;
		struct { uint32_t width; uint32_t height; } resize;
		struct { int32_t dx; int32_t dy; } scroll;
	};
};

/* Event callback (registered from Java via JNI) */
typedef void (*jdesk_event_callback_t)(const struct jdesk_event *event,
				       void *user_data);

/* ===========================================================================
 * System Integration
 * ===========================================================================
 */

/* System information */
struct jdesk_system_info {
	struct jdesk_cpu_info cpu;
	uint64_t total_ram_bytes;
	uint64_t free_ram_bytes;
	uint32_t num_screens;
	struct jdesk_screen screens[8];  /* Up to 8 monitors */
	char     hostname[64];
	char     os_name[32];       /* "Linux", "Windows", "macOS" */
	char     os_version[32];    /* "5.15.204", "11", "14.0" */
	char     desktop_session[32]; /* "jdesk", "GNOME", "KDE" */
};

/* ===========================================================================
 * Public API Functions
 * ===========================================================================
 */

/* Initialization & shutdown */
int  jdesk_init(void);
void jdesk_shutdown(void);

/* CPU feature detection */
void jdesk_detect_cpu(struct jdesk_cpu_info *info);
bool jdesk_has_feature(uint32_t feature_flag);

/* Display management */
jdesk_display_t *jdesk_display_open(const char *display_name);
void             jdesk_display_close(jdesk_display_t *dpy);
int              jdesk_get_screens(jdesk_display_t *dpy,
				   struct jdesk_screen *screens,
				   int max_screens);

/* Window management */
jdesk_window_t *jdesk_window_create(jdesk_display_t *dpy,
				    const struct jdesk_window_params *params);
void            jdesk_window_destroy(jdesk_window_t *win);
void            jdesk_window_show(jdesk_window_t *win);
void            jdesk_window_hide(jdesk_window_t *win);
void            jdesk_window_set_fullscreen(jdesk_window_t *win, uint32_t flags);
void            jdesk_window_set_state(jdesk_window_t *win,
				       enum jdesk_window_state state);
void            jdesk_window_set_title(jdesk_window_t *win, const char *title);

/* Event loop */
void jdesk_event_register(jdesk_window_t *win,
			  jdesk_event_callback_t cb, void *user_data);
int  jdesk_event_poll(jdesk_display_t *dpy, struct jdesk_event *event);
void jdesk_event_loop(jdesk_display_t *dpy);

/* Icon system */
int  jdesk_icon_load_svg(const char *svg_path, enum jdesk_icon_size size,
			 struct jdesk_icon_buffer *out);
int  jdesk_icon_lookup(const char *name, enum jdesk_icon_category category,
		       enum jdesk_icon_size size, struct jdesk_icon_buffer *out);
void jdesk_icon_free(struct jdesk_icon_buffer *buf);

/* Theme */
void jdesk_theme_get_default(struct jdesk_theme *theme);
void jdesk_theme_apply(jdesk_window_t *win, const struct jdesk_theme *theme);

/* System info */
void jdesk_system_info(struct jdesk_system_info *info);

/* High-precision timing (uses RDTSC where available) */
uint64_t jdesk_time_ns(void);
uint64_t jdesk_time_us(void);

/* Frame scheduling (targets display refresh rate) */
void jdesk_vsync_wait(jdesk_display_t *dpy);
void jdesk_frame_begin(void);
void jdesk_frame_end(void);

#ifdef __cplusplus
}
#endif

#endif /* __JDESK_H */
