/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk_linux.c — MEARVK Java Desktop Framework — Linux Native Bridge
 *
 * X11 display connection, full-screen management, CPU feature detection,
 * and system integration for the Java Desktop Framework.
 *
 * This library is loaded by JavaFX via System.loadLibrary("jdesk") and
 * provides the native backing for the cross-platform desktop environment.
 *
 * Build:
 *   gcc -shared -fPIC -O2 -march=x86-64-v3 -o libjdesk.so jdesk_linux.c \
 *       -lX11 -lXrandr -lXinerama -lXcursor -lpthread -lm
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

/* _GNU_SOURCE defined via -D_GNU_SOURCE on compiler command line */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/sysinfo.h>
#include <sys/utsname.h>
#include <cpuid.h>

#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/Xatom.h>
#include <X11/keysym.h>
#include <X11/cursorfont.h>
#include <X11/extensions/Xrandr.h>
#include <X11/extensions/Xinerama.h>

#include "../include/jdesk.h"

/* ===========================================================================
 * Internal Structures
 * ===========================================================================
 */

struct jdesk_display {
	Display *x_display;
	int      screen_num;
	Screen  *screen;
	Window   root;
	Atom     wm_delete;
	Atom     net_wm_state;
	Atom     net_wm_state_fullscreen;
	Atom     net_wm_state_above;
	Atom     net_wm_name;
	Atom     utf8_string;
	int      xrandr_event_base;
	int      xrandr_error_base;
	bool     xrandr_available;
	bool     xinerama_available;
};

struct jdesk_window {
	jdesk_display_t *display;
	Window           x_window;
	GC               gc;
	XVisualInfo      visual_info;
	Colormap         colormap;
	uint32_t         width;
	uint32_t         height;
	uint32_t         flags;
	enum jdesk_window_state state;
	jdesk_event_callback_t event_cb;
	void            *event_user_data;
	bool             mapped;
};

/* Global state */
static struct {
	bool initialized;
	struct jdesk_cpu_info cpu;
	struct jdesk_theme default_theme;
	uint64_t tsc_freq_hz;       /* TSC frequency for timing */
} g_jdesk = { 0 };

/* ===========================================================================
 * x86_64 CPU Feature Detection
 *
 * Uses CPUID instruction to detect available SIMD extensions.
 * JavaFX pixel operations benefit from AVX2 (2x throughput over SSE).
 * ===========================================================================
 */

static inline void cpuid(uint32_t leaf, uint32_t subleaf,
			 uint32_t *eax, uint32_t *ebx,
			 uint32_t *ecx, uint32_t *edx)
{
	__cpuid_count(leaf, subleaf, *eax, *ebx, *ecx, *edx);
}

void jdesk_detect_cpu(struct jdesk_cpu_info *info)
{
	uint32_t eax, ebx, ecx, edx;
	uint32_t max_leaf, max_ext_leaf;

	memset(info, 0, sizeof(*info));

	/* Vendor string */
	cpuid(0, 0, &max_leaf, &ebx, &ecx, &edx);
	memcpy(info->vendor + 0, &ebx, 4);
	memcpy(info->vendor + 4, &edx, 4);
	memcpy(info->vendor + 8, &ecx, 4);
	info->vendor[12] = '\0';

	/* Feature flags from leaf 1 */
	if (max_leaf >= 1) {
		cpuid(1, 0, &eax, &ebx, &ecx, &edx);

		if (edx & (1 << 26)) info->features |= JDESK_CPU_SSE2;
		if (ecx & (1 <<  0)) info->features |= JDESK_CPU_SSE3;
		if (ecx & (1 <<  9)) info->features |= JDESK_CPU_SSSE3;
		if (ecx & (1 << 19)) info->features |= JDESK_CPU_SSE41;
		if (ecx & (1 << 20)) info->features |= JDESK_CPU_SSE42;
		if (ecx & (1 << 28)) info->features |= JDESK_CPU_AVX;
		if (ecx & (1 << 12)) info->features |= JDESK_CPU_FMA;
		if (ecx & (1 << 25)) info->features |= JDESK_CPU_AES_NI;
		if (ecx & (1 << 23)) info->features |= JDESK_CPU_POPCNT;
		if (edx & (1 <<  4)) info->features |= JDESK_CPU_RDTSC;
		if (ecx & (1 << 30)) info->features |= JDESK_CPU_RDRAND;

		/* Cache line size */
		info->cache_line_size = ((ebx >> 8) & 0xFF) * 8;

		/* Logical cores from this package */
		info->logical_cores = (ebx >> 16) & 0xFF;
	}

	/* Extended features from leaf 7 */
	if (max_leaf >= 7) {
		cpuid(7, 0, &eax, &ebx, &ecx, &edx);

		if (ebx & (1 <<  5)) info->features |= JDESK_CPU_AVX2;
		if (ebx & (1 <<  3)) info->features |= JDESK_CPU_BMI1;
		if (ebx & (1 <<  8)) info->features |= JDESK_CPU_BMI2;
		if (ebx & (1 << 16)) info->features |= JDESK_CPU_AVX512F;
		if (ebx & (1 << 30)) info->features |= JDESK_CPU_AVX512BW;
	}

	/* Brand string from extended leaves */
	cpuid(0x80000000, 0, &max_ext_leaf, &ebx, &ecx, &edx);
	if (max_ext_leaf >= 0x80000004) {
		cpuid(0x80000002, 0, (uint32_t *)&info->brand[0],
		      (uint32_t *)&info->brand[4],
		      (uint32_t *)&info->brand[8],
		      (uint32_t *)&info->brand[12]);
		cpuid(0x80000003, 0, (uint32_t *)&info->brand[16],
		      (uint32_t *)&info->brand[20],
		      (uint32_t *)&info->brand[24],
		      (uint32_t *)&info->brand[28]);
		cpuid(0x80000004, 0, (uint32_t *)&info->brand[32],
		      (uint32_t *)&info->brand[36],
		      (uint32_t *)&info->brand[40],
		      (uint32_t *)&info->brand[44]);
		info->brand[48] = '\0';
	}

	/* Core count from sysconf */
	info->physical_cores = sysconf(_SC_NPROCESSORS_CONF);
	info->logical_cores = sysconf(_SC_NPROCESSORS_ONLN);

	/* Cache sizes from leaf 4 (Intel) or leaf 0x80000006 (AMD) */
	if (max_ext_leaf >= 0x80000006) {
		cpuid(0x80000006, 0, &eax, &ebx, &ecx, &edx);
		info->l2_cache_kb = (ecx >> 16) & 0xFFFF;
	}

	/* L3 from extended leaf 0x80000006 edx */
	if (max_ext_leaf >= 0x80000006) {
		cpuid(0x80000006, 0, &eax, &ebx, &ecx, &edx);
		info->l3_cache_kb = ((edx >> 18) & 0x3FFF) * 512;
	}

	/* TSC frequency estimation */
	if (info->features & JDESK_CPU_RDTSC) {
		struct timespec t1, t2;
		uint64_t tsc1, tsc2;
		unsigned int lo, hi;

		clock_gettime(CLOCK_MONOTONIC, &t1);
		__asm__ volatile("rdtsc" : "=a"(lo), "=d"(hi));
		tsc1 = ((uint64_t)hi << 32) | lo;

		usleep(10000); /* 10ms calibration */

		clock_gettime(CLOCK_MONOTONIC, &t2);
		__asm__ volatile("rdtsc" : "=a"(lo), "=d"(hi));
		tsc2 = ((uint64_t)hi << 32) | lo;

		uint64_t elapsed_ns = (t2.tv_sec - t1.tv_sec) * 1000000000ULL +
				      (t2.tv_nsec - t1.tv_nsec);
		if (elapsed_ns > 0) {
			g_jdesk.tsc_freq_hz = (tsc2 - tsc1) * 1000000000ULL / elapsed_ns;
			info->tsc_frequency_mhz = g_jdesk.tsc_freq_hz / 1000000;
		}
	}
}

bool jdesk_has_feature(uint32_t feature_flag)
{
	return (g_jdesk.cpu.features & feature_flag) != 0;
}

/* ===========================================================================
 * High-Precision Timing
 * ===========================================================================
 */

uint64_t jdesk_time_ns(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return (uint64_t)ts.tv_sec * 1000000000ULL + ts.tv_nsec;
}

uint64_t jdesk_time_us(void)
{
	return jdesk_time_ns() / 1000;
}

/* ===========================================================================
 * X11 Display Management
 * ===========================================================================
 */

jdesk_display_t *jdesk_display_open(const char *display_name)
{
	jdesk_display_t *dpy;
	Display *x_dpy;

	x_dpy = XOpenDisplay(display_name);
	if (!x_dpy) {
		fprintf(stderr, "jdesk: failed to open X11 display '%s'\n",
			display_name ? display_name : ":0");
		return NULL;
	}

	dpy = calloc(1, sizeof(*dpy));
	if (!dpy) {
		XCloseDisplay(x_dpy);
		return NULL;
	}

	dpy->x_display = x_dpy;
	dpy->screen_num = DefaultScreen(x_dpy);
	dpy->screen = ScreenOfDisplay(x_dpy, dpy->screen_num);
	dpy->root = RootWindow(x_dpy, dpy->screen_num);

	/* Intern atoms for window management */
	dpy->wm_delete = XInternAtom(x_dpy, "WM_DELETE_WINDOW", False);
	dpy->net_wm_state = XInternAtom(x_dpy, "_NET_WM_STATE", False);
	dpy->net_wm_state_fullscreen = XInternAtom(x_dpy, "_NET_WM_STATE_FULLSCREEN", False);
	dpy->net_wm_state_above = XInternAtom(x_dpy, "_NET_WM_STATE_ABOVE", False);
	dpy->net_wm_name = XInternAtom(x_dpy, "_NET_WM_NAME", False);
	dpy->utf8_string = XInternAtom(x_dpy, "UTF8_STRING", False);

	/* Check for XRandR */
	dpy->xrandr_available = XRRQueryExtension(x_dpy,
						  &dpy->xrandr_event_base,
						  &dpy->xrandr_error_base);

	/* Check for Xinerama */
	dpy->xinerama_available = XineramaIsActive(x_dpy);

	return dpy;
}

void jdesk_display_close(jdesk_display_t *dpy)
{
	if (!dpy) return;
	if (dpy->x_display)
		XCloseDisplay(dpy->x_display);
	free(dpy);
}

int jdesk_get_screens(jdesk_display_t *dpy,
		      struct jdesk_screen *screens, int max_screens)
{
	int count = 0;

	if (!dpy || !screens || max_screens <= 0)
		return 0;

	if (dpy->xrandr_available) {
		XRRScreenResources *res = XRRGetScreenResources(dpy->x_display,
								dpy->root);
		if (res) {
			for (int i = 0; i < res->noutput && count < max_screens; i++) {
				XRROutputInfo *out = XRRGetOutputInfo(dpy->x_display,
								     res,
								     res->outputs[i]);
				if (out && out->connection == RR_Connected && out->crtc) {
					XRRCrtcInfo *crtc = XRRGetCrtcInfo(dpy->x_display,
									   res, out->crtc);
					if (crtc) {
						screens[count].width = crtc->width;
						screens[count].height = crtc->height;
						screens[count].screen_index = count;
						screens[count].depth = DefaultDepth(dpy->x_display,
										    dpy->screen_num);

						/* DPI from mm dimensions */
						if (out->mm_width > 0)
							screens[count].dpi_x = (crtc->width * 254) /
									       (out->mm_width * 10);
						else
							screens[count].dpi_x = 96;
						screens[count].dpi_y = screens[count].dpi_x;

						/* Scale factor */
						screens[count].scale_factor =
							screens[count].dpi_x / 96.0;
						if (screens[count].scale_factor < 1.0)
							screens[count].scale_factor = 1.0;

						/* Refresh rate from current mode */
						for (int m = 0; m < res->nmode; m++) {
							if (res->modes[m].id == crtc->mode) {
								XRRModeInfo *mode = &res->modes[m];
								if (mode->hTotal && mode->vTotal)
									screens[count].refresh_hz =
										(uint32_t)((double)mode->dotClock /
											   (mode->hTotal * mode->vTotal));
								break;
							}
						}
						if (screens[count].refresh_hz == 0)
							screens[count].refresh_hz = 60;

						count++;
						XRRFreeCrtcInfo(crtc);
					}
				}
				if (out) XRRFreeOutputInfo(out);
			}
			XRRFreeScreenResources(res);
		}
	}

	/* Fallback: single screen from default */
	if (count == 0) {
		screens[0].width = WidthOfScreen(dpy->screen);
		screens[0].height = HeightOfScreen(dpy->screen);
		screens[0].dpi_x = 96;
		screens[0].dpi_y = 96;
		screens[0].refresh_hz = 60;
		screens[0].depth = DefaultDepth(dpy->x_display, dpy->screen_num);
		screens[0].scale_factor = 1.0;
		screens[0].screen_index = 0;
		count = 1;
	}

	return count;
}

/* ===========================================================================
 * Window Management
 * ===========================================================================
 */

jdesk_window_t *jdesk_window_create(jdesk_display_t *dpy,
				    const struct jdesk_window_params *params)
{
	jdesk_window_t *win;
	XSetWindowAttributes xwa;
	unsigned long xwa_mask;
	XSizeHints size_hints;

	if (!dpy || !params)
		return NULL;

	win = calloc(1, sizeof(*win));
	if (!win)
		return NULL;

	win->display = dpy;
	win->width = params->width;
	win->height = params->height;
	win->flags = params->flags;
	win->state = JDESK_WIN_NORMAL;

	/* Window attributes — white background */
	xwa.background_pixel = WhitePixel(dpy->x_display, dpy->screen_num);
	xwa.border_pixel = 0;
	xwa.event_mask = ExposureMask | KeyPressMask | KeyReleaseMask |
			 ButtonPressMask | ButtonReleaseMask |
			 PointerMotionMask | StructureNotifyMask |
			 FocusChangeMask | EnterWindowMask | LeaveWindowMask;
	xwa.override_redirect = False;
	xwa_mask = CWBackPixel | CWBorderPixel | CWEventMask;

	/* For exclusive fullscreen, override redirect bypasses WM */
	if (params->flags & JDESK_FULLSCREEN_EXCLUSIVE) {
		xwa.override_redirect = True;
		xwa_mask |= CWOverrideRedirect;
	}

	win->x_window = XCreateWindow(
		dpy->x_display, dpy->root,
		params->x, params->y, params->width, params->height,
		0, /* border width */
		CopyFromParent, /* depth */
		InputOutput,
		CopyFromParent, /* visual */
		xwa_mask, &xwa);

	if (!win->x_window) {
		free(win);
		return NULL;
	}

	/* Register close event */
	XSetWMProtocols(dpy->x_display, win->x_window, &dpy->wm_delete, 1);

	/* Set window title */
	if (params->title) {
		XChangeProperty(dpy->x_display, win->x_window,
				dpy->net_wm_name, dpy->utf8_string, 8,
				PropModeReplace,
				(unsigned char *)params->title,
				strlen(params->title));
		XStoreName(dpy->x_display, win->x_window, params->title);
	}

	/* Size hints */
	memset(&size_hints, 0, sizeof(size_hints));
	if (!params->resizable) {
		size_hints.flags = PMinSize | PMaxSize;
		size_hints.min_width = params->width;
		size_hints.max_width = params->width;
		size_hints.min_height = params->height;
		size_hints.max_height = params->height;
	}
	XSetWMNormalHints(dpy->x_display, win->x_window, &size_hints);

	/* Graphics context */
	win->gc = XCreateGC(dpy->x_display, win->x_window, 0, NULL);

	/* Fullscreen via EWMH */
	if (params->flags & JDESK_FULLSCREEN_WINDOWED) {
		jdesk_window_set_fullscreen(win, JDESK_FULLSCREEN_WINDOWED);
	}

	return win;
}

void jdesk_window_destroy(jdesk_window_t *win)
{
	if (!win) return;
	if (win->gc)
		XFreeGC(win->display->x_display, win->gc);
	if (win->x_window)
		XDestroyWindow(win->display->x_display, win->x_window);
	free(win);
}

void jdesk_window_show(jdesk_window_t *win)
{
	if (!win) return;
	XMapRaised(win->display->x_display, win->x_window);
	XFlush(win->display->x_display);
	win->mapped = true;
}

void jdesk_window_hide(jdesk_window_t *win)
{
	if (!win) return;
	XUnmapWindow(win->display->x_display, win->x_window);
	XFlush(win->display->x_display);
	win->mapped = false;
}

void jdesk_window_set_fullscreen(jdesk_window_t *win, uint32_t flags)
{
	(void)flags;
	if (!win) return;

	XEvent event;
	memset(&event, 0, sizeof(event));
	event.type = ClientMessage;
	event.xclient.window = win->x_window;
	event.xclient.message_type = win->display->net_wm_state;
	event.xclient.format = 32;
	event.xclient.data.l[0] = 1; /* _NET_WM_STATE_ADD */
	event.xclient.data.l[1] = win->display->net_wm_state_fullscreen;
	event.xclient.data.l[2] = 0;
	event.xclient.data.l[3] = 1; /* Source: application */

	XSendEvent(win->display->x_display, win->display->root, False,
		   SubstructureRedirectMask | SubstructureNotifyMask, &event);
	XFlush(win->display->x_display);

	win->state = JDESK_WIN_FULLSCREEN;
}

void jdesk_window_set_state(jdesk_window_t *win, enum jdesk_window_state state)
{
	if (!win) return;
	win->state = state;

	switch (state) {
	case JDESK_WIN_FULLSCREEN:
		jdesk_window_set_fullscreen(win, JDESK_FULLSCREEN_WINDOWED);
		break;
	case JDESK_WIN_HIDDEN:
		jdesk_window_hide(win);
		break;
	case JDESK_WIN_NORMAL:
	default:
		jdesk_window_show(win);
		break;
	}
}

void jdesk_window_set_title(jdesk_window_t *win, const char *title)
{
	if (!win || !title) return;
	XChangeProperty(win->display->x_display, win->x_window,
			win->display->net_wm_name,
			win->display->utf8_string, 8,
			PropModeReplace,
			(unsigned char *)title, strlen(title));
	XStoreName(win->display->x_display, win->x_window, title);
	XFlush(win->display->x_display);
}

/* ===========================================================================
 * Event Handling
 * ===========================================================================
 */

void jdesk_event_register(jdesk_window_t *win,
			  jdesk_event_callback_t cb, void *user_data)
{
	if (!win) return;
	win->event_cb = cb;
	win->event_user_data = user_data;
}

int jdesk_event_poll(jdesk_display_t *dpy, struct jdesk_event *event)
{
	XEvent xe;

	if (!dpy || !event)
		return 0;

	if (!XPending(dpy->x_display))
		return 0;

	XNextEvent(dpy->x_display, &xe);
	memset(event, 0, sizeof(*event));
	event->timestamp_ns = jdesk_time_ns();

	switch (xe.type) {
	case KeyPress:
		event->type = JDESK_EVENT_KEY_PRESS;
		event->key.keycode = xe.xkey.keycode;
		event->key.keysym = XLookupKeysym(&xe.xkey, 0);
		if (xe.xkey.state & ShiftMask) event->modifiers |= JDESK_MOD_SHIFT;
		if (xe.xkey.state & ControlMask) event->modifiers |= JDESK_MOD_CTRL;
		if (xe.xkey.state & Mod1Mask) event->modifiers |= JDESK_MOD_ALT;
		if (xe.xkey.state & Mod4Mask) event->modifiers |= JDESK_MOD_SUPER;
		return 1;

	case KeyRelease:
		event->type = JDESK_EVENT_KEY_RELEASE;
		event->key.keycode = xe.xkey.keycode;
		event->key.keysym = XLookupKeysym(&xe.xkey, 0);
		return 1;

	case ButtonPress:
		event->type = JDESK_EVENT_MOUSE_PRESS;
		event->mouse.x = xe.xbutton.x;
		event->mouse.y = xe.xbutton.y;
		event->mouse.button = xe.xbutton.button;
		return 1;

	case ButtonRelease:
		event->type = JDESK_EVENT_MOUSE_RELEASE;
		event->mouse.x = xe.xbutton.x;
		event->mouse.y = xe.xbutton.y;
		event->mouse.button = xe.xbutton.button;
		return 1;

	case MotionNotify:
		event->type = JDESK_EVENT_MOUSE_MOVE;
		event->mouse.x = xe.xmotion.x;
		event->mouse.y = xe.xmotion.y;
		return 1;

	case ConfigureNotify:
		event->type = JDESK_EVENT_WINDOW_RESIZE;
		event->resize.width = xe.xconfigure.width;
		event->resize.height = xe.xconfigure.height;
		return 1;

	case FocusIn:
		event->type = JDESK_EVENT_WINDOW_FOCUS;
		return 1;

	case FocusOut:
		event->type = JDESK_EVENT_WINDOW_UNFOCUS;
		return 1;

	case ClientMessage:
		event->type = JDESK_EVENT_WINDOW_CLOSE;
		return 1;

	default:
		return 0;
	}
}

void jdesk_event_loop(jdesk_display_t *dpy)
{
	struct jdesk_event event;

	while (1) {
		if (jdesk_event_poll(dpy, &event)) {
			if (event.type == JDESK_EVENT_WINDOW_CLOSE)
				break;
		} else {
			/* No events: sleep 1ms to avoid busy-wait */
			usleep(1000);
		}
	}
}

/* ===========================================================================
 * Theme System (White Theme Default)
 * ===========================================================================
 */

void jdesk_theme_get_default(struct jdesk_theme *theme)
{
	if (!theme) return;

	memset(theme, 0, sizeof(*theme));
	theme->name = "White";

	/* Clean, modern white color scheme */
	theme->colors.background     = 0xFFFFFFFF;  /* Pure white */
	theme->colors.surface        = 0xFFF8F9FA;  /* Slightly off-white */
	theme->colors.primary        = 0xFF1A73E8;  /* Google Blue */
	theme->colors.secondary      = 0xFF5F6368;  /* Material Grey 700 */
	theme->colors.text_primary   = 0xFF202124;  /* Near-black */
	theme->colors.text_secondary = 0xFF5F6368;  /* Grey */
	theme->colors.border         = 0xFFDADCE0;  /* Light border */
	theme->colors.hover          = 0xFFF1F3F4;  /* Light hover */
	theme->colors.active         = 0xFFE8EAED;  /* Active state */
	theme->colors.error          = 0xFFD93025;  /* Red */
	theme->colors.success        = 0xFF1E8E3E;  /* Green */
	theme->colors.warning        = 0xFFF9AB00;  /* Amber */
	theme->colors.shadow         = 0x1A000000;  /* 10% black shadow */

	/* Typography */
	theme->typography.font_family      = "Inter";
	theme->typography.font_size_title  = 20;
	theme->typography.font_size_body   = 14;
	theme->typography.font_size_caption = 12;
	theme->typography.font_size_panel  = 11;
	theme->typography.font_weight_normal = 400;
	theme->typography.font_weight_medium = 500;
	theme->typography.font_weight_bold   = 700;

	/* Visual properties */
	theme->corner_radius    = 8;
	theme->shadow_elevation = 4;
	theme->animation_ms     = 200;
	theme->icon_style       = 1; /* Outlined */
}

void jdesk_theme_apply(jdesk_window_t *win, const struct jdesk_theme *theme)
{
	if (!win || !theme) return;

	/* Set window background to theme background color */
	uint32_t bg = theme->colors.background;
	unsigned long pixel = (((bg >> 16) & 0xFF) << 16) |
			      (((bg >> 8) & 0xFF) << 8) |
			      (bg & 0xFF);

	XSetWindowBackground(win->display->x_display, win->x_window, pixel);
	XClearWindow(win->display->x_display, win->x_window);
	XFlush(win->display->x_display);
}

/* ===========================================================================
 * System Information
 * ===========================================================================
 */

void jdesk_system_info(struct jdesk_system_info *info)
{
	struct sysinfo si;
	struct utsname un;
	jdesk_display_t *dpy;

	if (!info) return;
	memset(info, 0, sizeof(*info));

	/* CPU info */
	info->cpu = g_jdesk.cpu;

	/* Memory */
	if (sysinfo(&si) == 0) {
		info->total_ram_bytes = si.totalram * si.mem_unit;
		info->free_ram_bytes = si.freeram * si.mem_unit;
	}

	/* OS info */
	if (uname(&un) == 0) {
		snprintf(info->os_name,    sizeof(info->os_name),
			 "%.*s", (int)(sizeof(info->os_name)    - 1), un.sysname);
		snprintf(info->os_version, sizeof(info->os_version),
			 "%.*s", (int)(sizeof(info->os_version) - 1), un.release);
		snprintf(info->hostname,   sizeof(info->hostname),
			 "%.*s", (int)(sizeof(info->hostname)   - 1), un.nodename);
	}

	strncpy(info->desktop_session, "jdesk", sizeof(info->desktop_session) - 1);

	/* Screen info */
	dpy = jdesk_display_open(NULL);
	if (dpy) {
		info->num_screens = jdesk_get_screens(dpy, info->screens, 8);
		jdesk_display_close(dpy);
	}
}

/* ===========================================================================
 * Frame Scheduling
 * ===========================================================================
 */

void jdesk_vsync_wait(jdesk_display_t *dpy)
{
	(void)dpy;
	/* On Linux without DRI3/present, sleep to ~16.6ms boundary (60Hz) */
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	uint64_t now_us = ts.tv_sec * 1000000ULL + ts.tv_nsec / 1000;
	uint64_t frame_us = 16667; /* 60 Hz */
	uint64_t next = ((now_us / frame_us) + 1) * frame_us;
	uint64_t sleep_us = next - now_us;
	if (sleep_us > 0 && sleep_us < frame_us)
		usleep(sleep_us);
}

void jdesk_frame_begin(void)
{
	/* Placeholder for frame timing instrumentation */
}

void jdesk_frame_end(void)
{
	/* Placeholder for frame timing instrumentation */
}

/* ===========================================================================
 * Icon System (stub — full implementation uses librsvg or nanosvg)
 * ===========================================================================
 */

int jdesk_icon_load_svg(const char *svg_path, enum jdesk_icon_size size,
			struct jdesk_icon_buffer *out)
{
	if (!svg_path || !out)
		return -1;

	/* Allocate a white placeholder icon */
	uint32_t dim = (uint32_t)size;
	out->width = dim;
	out->height = dim;
	out->stride = dim * 4;
	out->pixels = calloc(dim * dim, sizeof(uint32_t));
	if (!out->pixels)
		return -1;

	/* Fill with white background, dark border */
	for (uint32_t y = 0; y < dim; y++) {
		for (uint32_t x = 0; x < dim; x++) {
			if (x == 0 || x == dim - 1 || y == 0 || y == dim - 1)
				out->pixels[y * dim + x] = 0xFFDADCE0; /* Border */
			else
				out->pixels[y * dim + x] = 0xFFFFFFFF; /* White */
		}
	}

	return 0;
}

int jdesk_icon_lookup(const char *name, enum jdesk_icon_category category,
		      enum jdesk_icon_size size, struct jdesk_icon_buffer *out)
{
	char path[512];
	(void)category;

	snprintf(path, sizeof(path), "/usr/share/jdesk/icons/%s.svg", name);
	return jdesk_icon_load_svg(path, size, out);
}

void jdesk_icon_free(struct jdesk_icon_buffer *buf)
{
	if (buf && buf->pixels) {
		free(buf->pixels);
		buf->pixels = NULL;
	}
}

/* ===========================================================================
 * Initialization & Shutdown
 * ===========================================================================
 */

int jdesk_init(void)
{
	if (g_jdesk.initialized)
		return 0;

	/* Detect CPU capabilities */
	jdesk_detect_cpu(&g_jdesk.cpu);

	/* Initialize default theme */
	jdesk_theme_get_default(&g_jdesk.default_theme);

	/* X11 thread safety (required for JavaFX which uses multiple threads) */
	XInitThreads();

	g_jdesk.initialized = true;

	printf("jdesk: initialized — %s edition\n", JDESK_EDITION);
	printf("jdesk: CPU: %s\n", g_jdesk.cpu.brand);
	printf("jdesk: Features: SSE4.2=%d AVX2=%d AVX512=%d AES-NI=%d\n",
	       jdesk_has_feature(JDESK_CPU_SSE42),
	       jdesk_has_feature(JDESK_CPU_AVX2),
	       jdesk_has_feature(JDESK_CPU_AVX512F),
	       jdesk_has_feature(JDESK_CPU_AES_NI));
	printf("jdesk: Cores: %u physical, %u logical\n",
	       g_jdesk.cpu.physical_cores, g_jdesk.cpu.logical_cores);
	printf("jdesk: TSC: %u MHz\n", g_jdesk.cpu.tsc_frequency_mhz);

	return 0;
}

void jdesk_shutdown(void)
{
	g_jdesk.initialized = false;
	printf("jdesk: shutdown complete\n");
}
