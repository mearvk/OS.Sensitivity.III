# SPDX-License-Identifier: GPL-2.0
#
# Makefile for drm (Deferred Remove) userspace tool
#
# Copyright (C) 2026 MEARVK LLC
#

CC      := gcc
CFLAGS  := -Wall -Wextra -O2 -D_GNU_SOURCE $(WARN_CFLAGS)
LDFLAGS :=

PREFIX  ?= /usr/local
BINDIR  := $(PREFIX)/bin
MANDIR  := $(PREFIX)/share/man/man1

TARGET  := drm
SRCS    := drm.c
OBJS    := $(SRCS:.c=.o)

.PHONY: all clean install uninstall

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $^

%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

install: $(TARGET)
	install -d $(DESTDIR)$(BINDIR)
	install -m 755 $(TARGET) $(DESTDIR)$(BINDIR)/$(TARGET)

uninstall:
	rm -f $(DESTDIR)$(BINDIR)/$(TARGET)

clean:
	rm -f $(TARGET) $(OBJS)
