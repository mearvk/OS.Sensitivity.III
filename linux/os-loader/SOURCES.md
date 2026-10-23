# `linux/os-loader` — userland source (lean, free set)

Free, improvable Linux userland pieces warehoused for the SLeeLa OS Creator.
This is the lean set (the genuinely free/improvable userland), copied from
`mearvk/Ubuntu.Determinant.Beta.Restricted` `userland`, source commit
`0f2694e5f7fe09da1f61ab1cc48e56079cc3336b`. The large bundled applications and
vendor JDKs in the upstream `userland` (chromium, java, java-web-server,
intellij-community, semeru-openjdk-8, wine) are intentionally NOT included here.

## Contents

| Folder | Role | Typical license |
|---|---|---|
| `darling/` | Darling — run macOS binaries on Linux | GPL-3.0 |
| `openjdk/` | OpenJDK | GPL-2.0-with-classpath-exception |
| `x11/` | X11 userland pieces | MIT / X11 |
| `drm/` | DRM userland (libdrm) | MIT |
| `gui/` | GUI userland glue | (per file) |
| `jdesk/` | desktop glue | (per file) |
| `bin/` | userland binaries/launchers | (per file) |
| `wallpapers/` | desktop wallpapers | (per file) |

These are pieces that can be freely used and improved as the userland layer of
an OS rollout.

> Folder names are provisional and may be renamed later. The SLeeLa contract
> (`SLOSSensitivitySource` / `SLOSSensitivityFetch`) resolves `os-loader` to
> this directory; a rename updates there.
