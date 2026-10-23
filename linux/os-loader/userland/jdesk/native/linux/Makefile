# SPDX-License-Identifier: GPL-2.0
#
# MEARVK Java Desktop Framework — Native Build (Linux)
#
# Produces: libjdesk.so (shared library loaded by JavaFX via JNI)
#
# Requirements:
#   - GCC with x86_64 support
#   - X11 development headers (libx11-dev, libxrandr-dev, libxinerama-dev)
#   - JDK 28 headers ($JAVA_HOME/include)
#
# Usage:
#   make                  - Build libjdesk.so
#   make install          - Install to /usr/local/lib
#   make clean            - Remove build artifacts
#   make test             - Build and run native test binary
#

CC          = gcc
CFLAGS      = -Wall -Wextra -Werror -O2 -fPIC -D_GNU_SOURCE
CFLAGS     += -march=x86-64-v3  # Baseline: SSE4.2, AVX, AVX2, BMI1/2, FMA

# x86_64 optimization: use the best method for modern processors
# -march=x86-64-v3 targets: Haswell+ (2013+), Zen2+ (2019+)
# Enables: SSE4.2, AVX, AVX2, FMA, BMI1, BMI2, LZCNT, MOVBE
# This is the sweet spot — runs on 95%+ of x64 machines while
# using vector operations for pixel blending and layout math.
OPTFLAGS    = -ftree-vectorize -ffast-math -funroll-loops

# JNI headers — detect installed JDK (prefer 28, fall back to 25, then default-java)
ifeq ($(JAVA_HOME),)
  ifneq ($(wildcard /usr/lib/jvm/java-28-openjdk-amd64/include/jni.h),)
    JAVA_HOME := /usr/lib/jvm/java-28-openjdk-amd64
  else ifneq ($(wildcard /usr/lib/jvm/java-25-openjdk-amd64/include/jni.h),)
    JAVA_HOME := /usr/lib/jvm/java-25-openjdk-amd64
  else
    JAVA_HOME := $(shell readlink -f /usr/lib/jvm/default-java 2>/dev/null || echo /usr/lib/jvm/java-25-openjdk-amd64)
  endif
endif
JNI_INCLUDE = -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

# X11 libraries
X11_LIBS    = -lX11 -lXrandr -lXinerama -lXcursor
SYS_LIBS    = -lpthread -lm -ldl

# Include paths
INCLUDES    = -I../include $(JNI_INCLUDE)

# Source files
SRCS        = jdesk_linux.c jdesk_jni.c
OBJS        = $(SRCS:.c=.o)

# Output
TARGET      = libjdesk.so
TEST_BIN    = jdesk_test
LAUNCHER    = jdesk-bin

# Installation
PREFIX      = /usr/local
BINDIR      = $(PREFIX)/bin
LIBDIR      = $(PREFIX)/lib
INCLUDEDIR  = $(PREFIX)/include/jdesk
SHAREDIR    = $(PREFIX)/share/jdesk

.PHONY: all install uninstall clean test launcher

all: $(TARGET) $(LAUNCHER)

$(TARGET): $(OBJS)
	$(CC) -shared -o $@ $(OBJS) $(X11_LIBS) $(SYS_LIBS)
	@echo "Built: $(TARGET) (x86-64-v3, AVX2-optimized)"
	@echo "  Load in Java: System.loadLibrary(\"jdesk\")"

%.o: %.c ../include/jdesk.h
	$(CC) $(CFLAGS) $(OPTFLAGS) $(INCLUDES) -c -o $@ $<

# Native test binary (no JNI, tests X11 + CPU detection directly)
test: jdesk_linux.c ../include/jdesk.h
	$(CC) $(CFLAGS) $(OPTFLAGS) -I../include -DJDESK_TEST_MAIN \
		-o $(TEST_BIN) jdesk_linux.c jdesk_test.c \
		$(X11_LIBS) $(SYS_LIBS)
	./$(TEST_BIN)

# Native launcher binary (standalone ELF that discovers and execs into JVM)
launcher: $(LAUNCHER)

$(LAUNCHER): jdesk_launcher.c
	$(CC) -Wall -Wextra -O2 -D_GNU_SOURCE \
		-Wno-format-truncation -Wno-stringop-truncation \
		-o $@ $< -lX11
	@echo "Built: $(LAUNCHER) (native Linux launcher)"
	@echo "  Install: sudo install -m 755 $(LAUNCHER) /usr/local/bin/jdesk"

install: $(TARGET) $(LAUNCHER)
	install -d $(DESTDIR)$(BINDIR)
	install -d $(DESTDIR)$(LIBDIR)
	install -d $(DESTDIR)$(INCLUDEDIR)
	install -d $(DESTDIR)$(SHAREDIR)/icons
	install -m 755 $(LAUNCHER) $(DESTDIR)$(BINDIR)/jdesk
	install -m 755 $(TARGET) $(DESTDIR)$(LIBDIR)/$(TARGET)
	install -m 644 ../include/jdesk.h $(DESTDIR)$(INCLUDEDIR)/jdesk.h
	ldconfig 2>/dev/null || true
	@echo "Installed: $(DESTDIR)$(BINDIR)/jdesk (launcher)"
	@echo "Installed: $(DESTDIR)$(LIBDIR)/$(TARGET) (native library)"

uninstall:
	rm -f $(DESTDIR)$(BINDIR)/jdesk
	rm -f $(DESTDIR)$(LIBDIR)/$(TARGET)
	rm -f $(DESTDIR)$(INCLUDEDIR)/jdesk.h
	rmdir $(DESTDIR)$(INCLUDEDIR) 2>/dev/null || true

clean:
	rm -f $(OBJS) $(TARGET) $(TEST_BIN) $(LAUNCHER)
