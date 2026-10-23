# SPDX-License-Identifier: GPL-2.0
#
# Makefile for Userland - Ubuntu Determinant Alpha RS
#
# Orchestrates the build of all userland components.
#
# Copyright (C) 2026 MEARVK LLC
#

SHELL := /bin/bash

PREFIX  ?= /usr
DESTDIR ?=

# Userland components
COMPONENTS := x11 wallpapers java chromium jdesk

.PHONY: all $(COMPONENTS) rootfs-extract install clean help

all: $(COMPONENTS)

help:
	@echo "Userland Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all             - Build all userland components"
	@echo "  x11             - Build X11 display system"
	@echo "  rootfs-extract  - Extract Ubuntu Base rootfs to build/rootfs"
	@echo "  install         - Install all components to DESTDIR"
	@echo "  clean           - Clean all build artifacts"
	@echo ""
	@echo "Components:"
	@echo "  x11/            - X.Org server 21.1.24, libX11, icons, apps"
	@echo ""
	@echo "Rootfs:"
	@echo "  ubuntu-base-24.04.4-base-amd64.tar.gz (29 MB)"

# ==============================================================================
# X11 Display System
# ==============================================================================

x11:
	$(MAKE) -C x11

# ==============================================================================
# Desktop Wallpapers
# ==============================================================================

wallpapers:
	$(MAKE) -C wallpapers

# ==============================================================================
# OpenJDK 28
# ==============================================================================

java:
	$(MAKE) -C java

# ==============================================================================
# Chromium Browser
# ==============================================================================

chromium:
	$(MAKE) -C chromium

# ==============================================================================
# JDesk Desktop Environment
# ==============================================================================

jdesk:
	@echo "[JDesk] Building native library..."
	$(MAKE) -C jdesk/native/linux JAVA_HOME=$(JAVA_HOME)
	@echo "[JDesk] Compiling launcher Java sources..."
	$(MAKE) -C jdesk/native-apps compile

# ==============================================================================
# Root Filesystem Extraction
# ==============================================================================

ROOTFS_TAR := ubuntu-base-24.04.4-base-amd64.tar.gz
ROOTFS_DIR := $(CURDIR)/../build/rootfs

rootfs-extract: $(ROOTFS_TAR)
	@echo "Extracting Ubuntu Base 24.04.4 rootfs..."
	mkdir -p $(ROOTFS_DIR)
	fakeroot tar -xzf $(ROOTFS_TAR) -C $(ROOTFS_DIR)
	@echo "Rootfs ready at $(ROOTFS_DIR)"

# ==============================================================================
# Install
# ==============================================================================

install: all
	@if [ -z "$(DESTDIR)" ]; then \
		echo "ERROR: Set DESTDIR (e.g., make install DESTDIR=/mnt/rootfs)"; \
		exit 1; \
	fi
	$(MAKE) -C x11 install DESTDIR=$(DESTDIR)
	$(MAKE) -C wallpapers install DESTDIR=$(DESTDIR)
	$(MAKE) -C java install DESTDIR=$(DESTDIR)
	$(MAKE) -C jdesk/native/linux install DESTDIR=$(DESTDIR)
	$(MAKE) -C jdesk/native-apps install DESTDIR=$(DESTDIR)
	@if [ -f chromium/chromium-src/out/Release/chrome ]; then \
		$(MAKE) -C chromium install DESTDIR=$(DESTDIR); \
	else \
		echo "  (Chromium not built from source — install via apt in rootfs instead)"; \
	fi

# ==============================================================================
# Clean
# ==============================================================================

clean:
	$(MAKE) -C x11 clean
	$(MAKE) -C wallpapers clean 2>/dev/null || true
	$(MAKE) -C java clean 2>/dev/null || true
