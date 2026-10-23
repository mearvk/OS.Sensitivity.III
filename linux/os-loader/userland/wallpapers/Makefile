# SPDX-License-Identifier: CC0-1.0
#
# Makefile for Desktop Wallpapers - Galactic Cherry Marvell Edition 98
#
# Copyright (C) 2026 MEARVK LLC
#

SHELL := /bin/bash

PREFIX  ?= /usr
DESTDIR ?=

WALLPAPER_DIR := $(DESTDIR)$(PREFIX)/share/backgrounds/galactic-cherry
PROPERTIES_DIR := $(DESTDIR)$(PREFIX)/share/gnome-background-properties

WALLPAPERS := \
	galactic-cherry-default.svg \
	cherry-dawn.svg \
	galaxy-core.svg \
	cherry-blossom-night.svg \
	minimal-dark.svg \
	aurora-cherry.svg \
	moonlit-ocean.svg \
	cherry-light.svg \
	cherry-nebula.svg

WALLPAPERS_JPEG := \
	ubuntu_marvell_001.jpeg \
	ubuntu_marvell_002.jpeg \
	ubuntu_marvell_003.jpeg \
	ubuntu_marvell_004.jpeg \
	ubuntu_marvell_005.jpeg \
	ubuntu_marvell_006.jpeg \
	ubuntu_marvell_007.jpeg \
	ubuntu_marvell_008.jpeg \
	ubuntu_marvell_009.jpeg \
	ubuntu_marvell_010.jpeg

.PHONY: all install uninstall clean help

all:
	@echo "Wallpapers are SVG + JPEG — no build needed."
	@echo "Run 'make install' to install to $(WALLPAPER_DIR)"

help:
	@echo "Galactic Cherry Marvell Edition 98 - Wallpapers"
	@echo ""
	@echo "Targets:"
	@echo "  install    - Install wallpapers to DESTDIR"
	@echo "  uninstall  - Remove installed wallpapers"
	@echo ""
	@echo "Wallpapers ($(words $(WALLPAPERS)) SVG + $(words $(WALLPAPERS_JPEG)) JPEG):"
	@for w in $(WALLPAPERS); do echo "  $$w"; done
	@for w in $(WALLPAPERS_JPEG); do echo "  $$w"; done

install: $(WALLPAPERS) $(WALLPAPERS_JPEG)
	@echo "Installing wallpapers to $(WALLPAPER_DIR)..."
	install -d $(WALLPAPER_DIR)
	install -d $(PROPERTIES_DIR)
	install -m 644 $(WALLPAPERS) $(WALLPAPER_DIR)/
	install -m 644 $(WALLPAPERS_JPEG) $(WALLPAPER_DIR)/
	install -m 644 galactic-cherry.xml $(PROPERTIES_DIR)/ 2>/dev/null || true
	@echo "Installed $(words $(WALLPAPERS)) SVG + $(words $(WALLPAPERS_JPEG)) JPEG wallpapers"

uninstall:
	rm -rf $(WALLPAPER_DIR)
	rm -f $(PROPERTIES_DIR)/galactic-cherry.xml

clean:
	@echo "Nothing to clean (SVG sources)"
