# `linux/desktop-loader` — GNOME desktop source

Free, improvable GNOME desktop-stack source warehoused for the SLeeLa OS Creator.

## Contents

`gnome-source/` — the GNOME desktop stack, copied from
`mearvk/Ubuntu.Determinant.Beta.Restricted` `gnome-source`, source commit
`0f2694e5f7fe09da1f61ab1cc48e56079cc3336b`.

Components (each free software; see each tree's own `COPYING`/`LICENSES`):

| Component | Role | Typical license |
|---|---|---|
| `gnome-shell/` | the GNOME Shell (session UI) | GPL-2.0-or-later |
| `mutter/` | the compositor / window manager | GPL-2.0-or-later |
| `gtk/` | the GTK widget toolkit | LGPL-2.1-or-later |
| `glib/` | the GLib core library | LGPL-2.1-or-later |
| `cairo/` | 2D graphics library | LGPL-2.1 / MPL-1.1 |
| `pango/` | text layout and rendering | LGPL-2.1-or-later |
| `gdk-pixbuf/` | image loading | LGPL-2.1-or-later |
| `gnome-control-center/` | system settings | GPL-2.0-or-later |
| `gnome-software/` | the software store | GPL-2.0-or-later |
| `gnome-terminal/` | terminal emulator | GPL-3.0-or-later |
| `gvfs/` | virtual filesystem | LGPL-2.0-or-later |
| `glib-networking/` | TLS/network for GIO | LGPL-2.1-or-later |
| `orca/` | screen reader (accessibility) | LGPL-2.1-or-later |
| `vala/` | the Vala compiler | LGPL-2.1-or-later |

These are pieces that can be freely used and improved as the desktop layer of an
OS rollout. The desktop is a replaceable, optional layer — it is not the OS.

> Folder names are provisional and may be renamed later. The SLeeLa contract
> (`SLOSSensitivitySource` / `SLOSSensitivityFetch`) resolves `desktop-loader`
> to this directory; a rename updates there.
