/* SPDX-License-Identifier: GPL-2.0 */
/*
 * jdesk_jni.c — JNI Bridge: Java ↔ Native Desktop Framework
 *
 * This file implements the JNI native methods called from the Java side.
 * It bridges JavaFX Scene/Stage operations to the underlying X11 primitives
 * via libjdesk.
 *
 * The JNI layer translates between:
 *   - Java String ↔ C const char*
 *   - Java int/long ↔ C uint32_t/uint64_t
 *   - Java arrays ↔ C structs
 *   - Java callbacks ↔ C function pointers
 *
 * Class: us.mearvk.jdesk.system.NativeBridge
 *
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include "../include/jdesk.h"

/* Cache JVM reference for callbacks */
static JavaVM *g_jvm = NULL;

/* ===========================================================================
 * JNI_OnLoad / JNI_OnUnload
 * ===========================================================================
 */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
	(void)reserved;
	g_jvm = vm;
	jdesk_init();
	return JNI_VERSION_10;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved)
{
	(void)vm;
	(void)reserved;
	jdesk_shutdown();
	g_jvm = NULL;
}

/* ===========================================================================
 * us.mearvk.jdesk.system.NativeBridge — CPU Info
 * ===========================================================================
 */

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetCpuFeatures
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetCpuFeatures(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	struct jdesk_cpu_info info;
	jdesk_detect_cpu(&info);
	return (jint)info.features;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetCpuBrand
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetCpuBrand(JNIEnv *env, jclass cls)
{
	(void)cls;
	struct jdesk_cpu_info info;
	jdesk_detect_cpu(&info);
	return (*env)->NewStringUTF(env, info.brand);
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetCoreCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetCoreCount(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	struct jdesk_cpu_info info;
	jdesk_detect_cpu(&info);
	return (jint)info.logical_cores;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeHasAVX2
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeHasAVX2(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	return jdesk_has_feature(JDESK_CPU_AVX2) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeHasAVX512
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeHasAVX512(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	return jdesk_has_feature(JDESK_CPU_AVX512F) ? JNI_TRUE : JNI_FALSE;
}

/* ===========================================================================
 * us.mearvk.jdesk.system.NativeBridge — Display & Screen
 * ===========================================================================
 */

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetScreenCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetScreenCount(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) return 1;

	struct jdesk_screen screens[8];
	int count = jdesk_get_screens(dpy, screens, 8);
	jdesk_display_close(dpy);
	return (jint)count;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetScreenWidth
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetScreenWidth(JNIEnv *env, jclass cls, jint index)
{
	(void)env; (void)cls;
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) return 1920;

	struct jdesk_screen screens[8];
	int count = jdesk_get_screens(dpy, screens, 8);
	jdesk_display_close(dpy);

	if (index >= 0 && index < count)
		return (jint)screens[index].width;
	return 1920;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetScreenHeight
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetScreenHeight(JNIEnv *env, jclass cls, jint index)
{
	(void)env; (void)cls;
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) return 1080;

	struct jdesk_screen screens[8];
	int count = jdesk_get_screens(dpy, screens, 8);
	jdesk_display_close(dpy);

	if (index >= 0 && index < count)
		return (jint)screens[index].height;
	return 1080;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetScreenDPI
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetScreenDPI(JNIEnv *env, jclass cls, jint index)
{
	(void)env; (void)cls;
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) return 96;

	struct jdesk_screen screens[8];
	int count = jdesk_get_screens(dpy, screens, 8);
	jdesk_display_close(dpy);

	if (index >= 0 && index < count)
		return (jint)screens[index].dpi_x;
	return 96;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetScaleFactor
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetScaleFactor(JNIEnv *env, jclass cls, jint index)
{
	(void)env; (void)cls;
	jdesk_display_t *dpy = jdesk_display_open(NULL);
	if (!dpy) return 1.0;

	struct jdesk_screen screens[8];
	int count = jdesk_get_screens(dpy, screens, 8);
	jdesk_display_close(dpy);

	if (index >= 0 && index < count)
		return (jdouble)screens[index].scale_factor;
	return 1.0;
}

/* ===========================================================================
 * us.mearvk.jdesk.system.NativeBridge — Timing
 * ===========================================================================
 */

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeTimeNanos
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeTimeNanos(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	return (jlong)jdesk_time_ns();
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetTSCFrequencyMHz
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetTSCFrequencyMHz(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	struct jdesk_cpu_info info;
	jdesk_detect_cpu(&info);
	return (jint)info.tsc_frequency_mhz;
}

/* ===========================================================================
 * us.mearvk.jdesk.system.NativeBridge — System Info
 * ===========================================================================
 */

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetTotalRAM
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetTotalRAM(JNIEnv *env, jclass cls)
{
	(void)env; (void)cls;
	struct jdesk_system_info info;
	jdesk_system_info(&info);
	return (jlong)info.total_ram_bytes;
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetOSName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetOSName(JNIEnv *env, jclass cls)
{
	(void)cls;
	struct jdesk_system_info info;
	jdesk_system_info(&info);
	return (*env)->NewStringUTF(env, info.os_name);
}

/*
 * Class:     us_mearvk_jdesk_system_NativeBridge
 * Method:    nativeGetHostname
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_us_mearvk_jdesk_system_NativeBridge_nativeGetHostname(JNIEnv *env, jclass cls)
{
	(void)cls;
	struct jdesk_system_info info;
	jdesk_system_info(&info);
	return (*env)->NewStringUTF(env, info.hostname);
}
