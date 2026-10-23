# SPDX-License-Identifier: GPL-2.0
#
# JDesk Native Application Launcher — Makefile
#
# Installs native binaries for all three major OSes, desktop icons,
# and resource profiles for the JVM Memory Proxy.
#
# Copyright (C) 2026 MEARVK LLC
# Author: Maximilian Eric Alexander Rupplin von Keffikon

DESTDIR ?= /opt/jdesk
JAVA_SRC_DIR = launcher
JAVA_SRC_TREE= ../src
JAVA_SRCS    = $(JAVA_SRC_DIR)/NativeAppLauncher.java \
               $(JAVA_SRC_DIR)/LibraryLinker.java \
               $(JAVA_SRC_DIR)/DesktopIconGrid.java
JAVA_OUT_DIR = $(JAVA_SRC_DIR)/classes
JAVAC        ?= javac
JAVA_VERSION := $(shell javac -version 2>&1 | awk '{print $$2}' | cut -d. -f1)

# JavaFX classpath — detect installed openjfx jars
FX_JARS := $(wildcard /usr/share/java/javafx-base-11.jar)
ifneq ($(FX_JARS),)
  FX_CP = /usr/share/java/javafx-base-11.jar:/usr/share/java/javafx-graphics-11.jar:/usr/share/java/javafx-controls-11.jar
else
  FX_CP =
endif

JAVA_FLAGS   = --release 17
ifneq ($(FX_CP),)
  JAVAC_FLAGS = $(JAVA_FLAGS) -cp "$(FX_CP)"
else
  JAVAC_FLAGS = $(JAVA_FLAGS)
endif

# Native application install locations
APPS_DIR = $(DESTDIR)/apps
ICONS_DIR = $(DESTDIR)/icons
MANIFESTS_DIR = $(DESTDIR)/manifests
PROFILES_DIR = $(DESTDIR)/profiles

# Disk allocation check (3 GB minimum for all three OS stacks)
MIN_ALLOCATION_MB = 3072

.PHONY: all install install-linux install-wine install-darling install-icons \
        install-profiles install-all check-space clean compile compile-native

all: compile compile-native

compile: $(JAVA_SRCS)
	@echo "[JDesk] Compiling launcher Java sources (release 17, JavaFX cp)..."
	@mkdir -p $(JAVA_OUT_DIR)
	$(JAVAC) $(JAVAC_FLAGS) -sourcepath "$(JAVA_SRC_TREE)" -d $(JAVA_OUT_DIR) $(JAVA_SRCS)
	@echo "[JDesk] Compiled: $(JAVA_OUT_DIR)/"

# ===========================================================================
#  Native Host Binaries (LibraryLinker Co-Linking Engine)
# ===========================================================================

CC       ?= gcc
MINGW_CC ?= x86_64-w64-mingw32-gcc
CFLAGS    = -O2 -Wall -Wextra -march=x86-64-v2
LDFLAGS   = -ldl

LIBHOST_SRC  = $(JAVA_SRC_DIR)/jdesk-libhost.c
LIBHOST_BIN  = $(JAVA_SRC_DIR)/jdesk-libhost
DLLHOST_SRC  = $(JAVA_SRC_DIR)/jdesk-dllhost.c
DLLHOST_BIN  = $(JAVA_SRC_DIR)/jdesk-dllhost.exe

compile-native: $(LIBHOST_BIN) $(DLLHOST_BIN)

$(LIBHOST_BIN): $(LIBHOST_SRC)
	@echo "[JDesk] Compiling jdesk-libhost (Linux ELF, host for .so)..."
	$(CC) $(CFLAGS) -o $@ $< $(LDFLAGS)
	@echo "[JDesk] Built: $@"

$(DLLHOST_BIN): $(DLLHOST_SRC)
	@echo "[JDesk] Cross-compiling jdesk-dllhost.exe (PE, host for .dll via Wine)..."
	@if command -v $(MINGW_CC) >/dev/null 2>&1; then \
		$(MINGW_CC) -O2 -Wall -o $@ $<; \
		echo "[JDesk] Built: $@"; \
	else \
		echo "[JDesk] SKIP: MinGW not installed ($(MINGW_CC) not found)"; \
		echo "[JDesk]   Install: sudo apt install gcc-mingw-w64-x86-64"; \
		echo "[JDesk]   Or skip Windows DLL support for now."; \
	fi

install-native: compile-native install-dirs
	@echo "[JDesk] Installing library host binaries..."
	@mkdir -p $(DESTDIR)/bin
	@cp -f $(LIBHOST_BIN) $(DESTDIR)/bin/jdesk-libhost
	@chmod 755 $(DESTDIR)/bin/jdesk-libhost
	@if [ -f $(DLLHOST_BIN) ]; then \
		cp -f $(DLLHOST_BIN) $(DESTDIR)/bin/jdesk-dllhost.exe; \
		echo "[JDesk] Installed: $(DESTDIR)/bin/jdesk-dllhost.exe"; \
	fi
	@mkdir -p $(DESTDIR)/libs
	@mkdir -p $(DESTDIR)/plugins
	@echo "[JDesk] Library host installed to $(DESTDIR)/bin/"
	@echo "[JDesk] Library drop paths:"
	@echo "         Trusted libs:    $(DESTDIR)/libs/"
	@echo "         JDesk plugins:   $(DESTDIR)/plugins/"

# ===========================================================================
#  Installation Targets
# ===========================================================================

install-dirs:
	@mkdir -p $(APPS_DIR)/libreoffice
	@mkdir -p $(APPS_DIR)/vscodium
	@mkdir -p $(APPS_DIR)/chromium
	@mkdir -p $(APPS_DIR)/terminal
	@mkdir -p $(APPS_DIR)/pcmanfm
	@mkdir -p $(APPS_DIR)/wine-apps
	@mkdir -p $(APPS_DIR)/macos-apps
	@mkdir -p $(ICONS_DIR)
	@mkdir -p $(MANIFESTS_DIR)
	@mkdir -p $(PROFILES_DIR)

install-icons: install-dirs
	@echo "[JDesk] Installing desktop icons..."
	@cp icons/*.svg $(ICONS_DIR)/
	@echo "[JDesk] Icons installed to $(ICONS_DIR)"

install-manifests: install-dirs
	@echo "[JDesk] Installing application manifests..."
	@cp manifests/*.jdesk-app $(MANIFESTS_DIR)/
	@echo "[JDesk] Manifests installed to $(MANIFESTS_DIR)"

install-profiles: install-dirs
	@echo "[JDesk] Installing Memory Proxy resource profiles..."
	@cp profiles/jdesk-apps.xml $(PROFILES_DIR)/
	@echo "[JDesk] Profiles installed to $(PROFILES_DIR)"

install-linux: install-dirs check-space
	@echo "[JDesk] Installing Linux native applications..."
	@echo "  → LibreOffice Writer (ELF, ~350 MB)"
	@./scripts/install-libreoffice.sh $(APPS_DIR)/libreoffice
	@echo "  → VSCodium IDE (ELF, ~300 MB)"
	@./scripts/install-vscodium.sh $(APPS_DIR)/vscodium
	@echo "  → Chromium Browser (ELF, ~180 MB)"
	@./scripts/install-chromium.sh $(APPS_DIR)/chromium
	@echo "  → PCManFM-Qt File Manager (ELF, ~45 MB)"
	@./scripts/install-pcmanfm.sh $(APPS_DIR)/pcmanfm
	@echo "  → JDesk Terminal (ELF, ~2 MB)"
	@./scripts/install-terminal.sh $(APPS_DIR)/terminal
	@echo "[JDesk] Linux native applications installed (~877 MB)"

install-wine: install-dirs
	@echo "[JDesk] Installing Wine (Windows PE support)..."
	@./wine/install-wine.sh $(APPS_DIR)
	@echo "[JDesk] Wine installed (~400 MB)"

install-darling: install-dirs
	@echo "[JDesk] Installing Darling (macOS Mach-O support)..."
	@./darling/install-darling.sh $(APPS_DIR)
	@echo "[JDesk] Darling installed (~300 MB)"

install-provisioner:
	@echo "[JDesk] Installing provisioner..."
	@mkdir -p /opt/jdesk/native-apps
	@cp jdesk-provision /opt/jdesk/native-apps/jdesk-provision
	@chmod +x /opt/jdesk/native-apps/jdesk-provision
	@cp jdesk-packages.json /opt/jdesk/native-apps/jdesk-packages.json
	@cp -r icons/ /opt/jdesk/native-apps/icons/
	@cp -r manifests/ /opt/jdesk/native-apps/manifests/
	@cp -r profiles/ /opt/jdesk/native-apps/profiles/
	@cp scripts/predictive-install.sh /opt/jdesk/native-apps/predictive-install.sh
	@chmod +x /opt/jdesk/native-apps/predictive-install.sh
	@cp jdesk-provision.service /etc/systemd/system/jdesk-provision.service
	@systemctl daemon-reload
	@systemctl enable jdesk-provision.service
	@ln -sf /opt/jdesk/native-apps/jdesk-provision /usr/local/sbin/jdesk-provision
	@ln -sf /opt/jdesk/native-apps/predictive-install.sh /usr/local/sbin/jdesk-predictive-install
	@echo "[JDesk] Provisioner installed. Will run at first boot."
	@echo "  Standard:    sudo jdesk-provision"
	@echo "  Predictive:  sudo jdesk-predictive-install"
	@echo "  Check:       sudo jdesk-predictive-install --check"
	@echo "  Plan:        sudo jdesk-predictive-install --plan"

install: install-provisioner install-icons install-manifests install-profiles
	@echo ""
	@echo "═══════════════════════════════════════════════════════════"
	@echo "  JDesk Native Applications — Installation Complete"
	@echo "═══════════════════════════════════════════════════════════"
	@echo "  Provisioner: /usr/local/sbin/jdesk-provision"
	@echo "  Manifest:    /opt/jdesk/native-apps/jdesk-packages.json"
	@echo "  Service:     jdesk-provision.service (first-boot auto)"
	@echo "  Icons:       $(ICONS_DIR)/"
	@echo "  Manifests:   $(MANIFESTS_DIR)/"
	@echo "  Profiles:    $(PROFILES_DIR)/"
	@echo ""
	@echo "  The provisioner installs native apps from upstream repos"
	@echo "  at first boot (Grade 3 privilege). No binaries in git."
	@echo ""
	@echo "  Manual provision: sudo jdesk-provision"
	@echo "  Full (optional):  sudo jdesk-provision --full"
	@echo "  Offline cache:    sudo jdesk-provision --build-cache /media/usb/"
	@echo "═══════════════════════════════════════════════════════════"

install-all: install install-wine install-darling
	@echo ""
	@echo "═══════════════════════════════════════════════════════════"
	@echo "  JDesk Native Applications — Full Installation Complete"
	@echo "═══════════════════════════════════════════════════════════"
	@echo "  All three OS platforms installed:"
	@echo "    ✓ Linux ELF (native)"
	@echo "    ✓ Windows PE (via Wine)"
	@echo "    ✓ macOS Mach-O (via Darling)"
	@echo ""
	@echo "  Total disk usage: ~3 GB"
	@echo "  All governed by JVM Memory Proxy"
	@echo "═══════════════════════════════════════════════════════════"

# ===========================================================================
#  Space Verification
# ===========================================================================

check-space:
	@echo "[JDesk] Checking disk allocation..."
	@AVAIL=$$(df -BM $(DESTDIR) 2>/dev/null | tail -1 | awk '{print $$4}' | tr -d 'M'); \
	if [ -z "$$AVAIL" ]; then \
		echo "  Warning: Cannot determine available space for $(DESTDIR)"; \
	elif [ $$AVAIL -lt $(MIN_ALLOCATION_MB) ]; then \
		echo "  ERROR: Insufficient space. Need $(MIN_ALLOCATION_MB) MB, have $${AVAIL} MB"; \
		echo "  The 3 GB allocation covers:"; \
		echo "    Linux binaries:  ~900 MB"; \
		echo "    Wine runtime:    ~400 MB"; \
		echo "    Darling runtime: ~300 MB"; \
		echo "    Headroom:        ~550 MB"; \
		echo "  Total:             ~3 GB (increased from initial 2 GB estimate)"; \
		exit 1; \
	else \
		echo "  OK: $${AVAIL} MB available (need $(MIN_ALLOCATION_MB) MB)"; \
	fi

# ===========================================================================
#  Size Report
# ===========================================================================

.PHONY: size-report
size-report:
	@echo ""
	@echo "═══════════════════════════════════════════════"
	@echo "  JDesk Native Apps — Size Assessment"
	@echo "═══════════════════════════════════════════════"
	@echo ""
	@echo "  Linux Native Binaries (ELF):"
	@echo "    LibreOffice Writer     ~350 MB"
	@echo "    VSCodium IDE           ~300 MB"
	@echo "    Chromium Browser       ~180 MB"
	@echo "    PCManFM-Qt Files       ~ 45 MB"
	@echo "    JDesk Terminal         ~  2 MB"
	@echo "    ─────────────────────────────"
	@echo "    Subtotal:              ~877 MB"
	@echo ""
	@echo "  Windows Support (PE via Wine):"
	@echo "    Wine runtime           ~400 MB"
	@echo "    Windows apps space     ~450 MB"
	@echo "    ─────────────────────────────"
	@echo "    Subtotal:              ~850 MB"
	@echo ""
	@echo "  macOS Support (Mach-O via Darling):"
	@echo "    Darling runtime        ~300 MB"
	@echo "    macOS apps space       ~350 MB"
	@echo "    ─────────────────────────────"
	@echo "    Subtotal:              ~650 MB"
	@echo ""
	@echo "  Metadata & Icons:        ~ 50 MB"
	@echo "  Headroom:                ~550 MB"
	@echo ""
	@echo "  ═══════════════════════════════"
	@echo "  TOTAL:                   ~2977 MB (~3 GB)"
	@echo ""
	@echo "  VERDICT: 2 GB is NOT sufficient."
	@echo "  ALLOCATION INCREASED TO: 3 GB"
	@echo "  ═══════════════════════════════"
	@echo ""

# ===========================================================================
#  Clean
# ===========================================================================

clean:
	@rm -rf $(JAVA_OUT_DIR)
	@rm -f $(LIBHOST_BIN) $(DLLHOST_BIN)
	@echo "[JDesk] Cleaned build artifacts"

uninstall:
	@echo "[JDesk] Removing native applications from $(DESTDIR)..."
	@rm -rf $(APPS_DIR)
	@rm -rf $(ICONS_DIR)
	@rm -rf $(MANIFESTS_DIR)
	@rm -rf $(PROFILES_DIR)
	@echo "[JDesk] Uninstalled"
