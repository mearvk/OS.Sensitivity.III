# JDesk Native Application Launcher

Runs native binaries from all three major operating systems (Linux ELF, Windows PE, macOS Mach-O) under the JVM Memory Proxy governance layer. Desktop icons for core productivity applications appear on JDesk startup.

## Supported Binary Formats

| OS | Format | Detection | Execution Method |
|----|--------|-----------|-----------------|
| Linux | ELF 64-bit | `\x7fELF` magic | Direct exec under `java -memory-guard` |
| Linux | ELF 32-bit | `\x7fELF` magic | Direct exec under `java -memory-guard` |
| Windows | PE/PE32+ | `MZ` magic | Wine + `java -memory-guard` |
| macOS | Mach-O 64 | `\xFE\xED\xFA\xCF` magic | Darling + `java -memory-guard` |
| Any | Shell (#!) | `#!` shebang | Direct exec under `java -memory-guard` |

## Default Desktop Applications

| Icon | Application | Binary | Format | Approx Size |
|------|-------------|--------|--------|-------------|
| 📝 | Writer (Word Processor) | LibreOffice Writer | ELF (Linux native) | ~350 MB |
| 💻 | IDE (Full IntelliJ Parity) | IntelliJ IDEA + JDesk GUI | ELF + JavaFX | ~300 MB |
| 🌐 | Browser | Chromium | ELF (Linux native) | ~180 MB |
| 🖥️ | Terminal | JDesk Terminal (built-in) | Java + JNI | ~2 MB |
| 📁 | Files | PCManFM-Qt (file manager) | ELF (Linux native) | ~45 MB |
| ⚙️ | Settings | JDesk Settings (built-in) | Java | ~1 MB |

## JDesk IDE — Full IntelliJ IDEA Feature Parity

The IDE application (`us.mearvk.jdesk.apps.JDeskIDE`) provides a complete IntelliJ IDEA-equivalent development environment with a JavaFX GUI surface. IntelliJ IDEA runs as the backend for code intelligence; the built-in editor handles all operations when IntelliJ is not installed.

### Menu Bar (13 Menus — matches IntelliJ 2024/2025)

| Menu | Key Actions |
|------|-------------|
| **File** | New File, New Project, Open File/Project, Open Recent, Save/Save As/Save All, Reload from Disk, Close Tab/All/Others, Settings, Project Structure, Exit |
| **Edit** | Undo/Redo, Cut/Copy/Paste, Paste from History, Select All/Word/Extend/Shrink, Duplicate Line, Delete Line, Move Line Up/Down, Indent/Unindent, Toggle Case, Join Lines, Find/Replace, Find in Path, Replace in Path, Column Selection Mode |
| **View** | Toggle: Project, Structure, Terminal, Problems, TODO, Version Control, Database, Event Log. Navigation Bar, Breadcrumbs, Line Numbers, Whitespace, Indent Guides, Word Wrap, Zoom In/Out/Reset, Full Screen, Distraction Free, Zen Mode |
| **Navigate** | Search Everywhere, Go to Class/File/Symbol/Line, Back/Forward, Recent Files/Locations, Last Edit Location, Go to Declaration/Implementation/Type/Super, File Structure, Type/Call Hierarchy, Next/Previous Method, Next/Previous Error, Bookmarks |
| **Code** | Generate, Override/Implement Methods, Surround With, Unwrap, Line/Block Comment, Reformat Code/File, Auto-Indent, Optimize Imports, Rearrange Code, Code Completion (basic + smart), Complete Statement, Code Folding (fold/unfold/all), Live Templates |
| **Refactor** | Refactor This, Rename, Change Signature, Extract Method/Variable/Constant/Field/Parameter/Interface/Superclass, Inline, Move, Copy, Safe Delete, Pull Members Up, Push Members Down |
| **Build** | Build Project, Rebuild Project, Build/Rebuild Module, Clean Project, Build Artifacts, Generate Sources |
| **Run** | Run, Run..., Debug, Debug..., Coverage, Profile, Stop, Edit Configurations, Step Over/Into/Out/Force, Run to Cursor, Resume, Evaluate Expression, Toggle/View Breakpoints |
| **Tools** | Terminal, Tasks & Contexts, HTTP Client, Database, CLI Launcher, Generate JavaDoc, External Tools |
| **Git** | Commit, Push, Pull, Fetch, Update, Merge, Rebase, Branches, New Branch, Checkout, History, Log, Annotate (Blame), Diff, Compare with Branch, Stash/Unstash, Reset, Rollback |
| **Window** | Split Vertically/Horizontally, Unsplit, Next/Previous Tab, Move Tab, Pin Tab, Store/Restore Layout |
| **Analyze** | Inspect Code, Code Cleanup, Run Inspection by Name, Dependencies (forward/backward/module/cyclic), Data Flow To/From Here, Coverage Data, Stack Trace |
| **Help** | Find Action, Keymap Reference, Getting Started, Tip of the Day, About, Register, Check Updates, Feedback, Collect Diagnostics |

### Main Toolbar Buttons

```
◀ ▶ │ 🔍 │ [Run Configuration ▾] ▶Run 🪲Debug 📊Profile ◎Coverage ■Stop │ 🔨Build │ ✓Commit ↑Push ↓Pull ⏱History │ ⚙Settings │                    ● IDE Backend
```

| Button | Shortcut | Action |
|--------|----------|--------|
| ◀ Back | Alt+Left | Navigate to previous location |
| ▶ Forward | Alt+Right | Navigate to next location |
| 🔍 Search | Shift+Shift | Search Everywhere (classes, files, symbols, actions) |
| ▶ Run | Shift+F10 | Run current configuration |
| 🪲 Debug | Shift+F9 | Debug current configuration |
| 📊 Profile | — | Profile current configuration |
| ◎ Coverage | — | Run with code coverage |
| ■ Stop | Ctrl+F2 | Stop running process |
| 🔨 Build | Ctrl+F9 | Build project (auto-detects Maven/Gradle/Make/Cargo/npm) |
| ✓ Commit | Ctrl+K | Git commit |
| ↑ Push | Ctrl+Shift+K | Git push |
| ↓ Pull | — | Git pull |
| ⏱ History | — | Git history |
| ⚙ Settings | Ctrl+Alt+S | Open IDE settings |

### Tool Windows (9 Windows — matches IntelliJ)

| Window | Shortcut | Purpose |
|--------|----------|---------|
| Terminal | Alt+F12 | Embedded terminal (JDeskTerminal) |
| Build | — | Build output (compile errors, progress) |
| Run | — | Application output when running |
| Debug | — | Debugger console, step controls, variables, watches |
| Problems | Alt+6 | Compiler errors, warnings, inspections |
| TODO | Alt+0 | TODO/FIXME items found across project |
| Git | Alt+9 | Version control log, changes, branches |
| Database | — | Database console (MySQL/PostgreSQL connections) |
| Event Log | — | IDE event history and notifications |

### Debug Panel

The Debug tool window includes its own toolbar with step controls:

```
▶Resume  ⤵Step Over  ↓Step Into  ↑Step Out  │  🖩Evaluate
```

| Button | Shortcut | Action |
|--------|----------|--------|
| ▶ Resume | F9 | Resume program execution |
| ⤵ Step Over | F8 | Execute current line, step to next |
| ↓ Step Into | F7 | Step into method call |
| ↑ Step Out | Shift+F8 | Step out of current method |
| 🖩 Evaluate | Alt+F8 | Evaluate expression in current context |

### Navigation Bar (Breadcrumbs)

Shows the current file path as clickable breadcrumb trail:

```
▸ src ▸ us ▸ mearvk ▸ jdesk ▸ apps ▸ JDeskIDE.java
```

### Status Bar

```
  JDeskIDE.java                    ⎇ main | UTF-8 | LF | Ln 42, Col 15 | 🧠 256/4096 MB
```

| Element | Content |
|---------|---------|
| Left | Current file name |
| Branch | Git branch from .git/HEAD |
| Encoding | File encoding (UTF-8) |
| Line Separator | LF / CRLF |
| Position | Line and column of cursor |
| Memory | JVM heap usage / max (updates every 5s) |

### Editor Features

| Feature | Description |
|---------|-------------|
| Tabs | Multiple files, modified indicator (●), closeable |
| Line numbers | Auto-updating gutter |
| Caret tracking | Position updates status bar in real-time |
| Syntax highlighting | Keyword/string/comment/type/method colors (Darcula) |
| Find/Replace | In-editor bar with wrap-around |
| Find in Path | Project-wide text search with file:line results |
| Go to Line | Direct line number navigation |
| Go to File | Fuzzy filename search across project |
| Duplicate line | Ctrl+D |
| Delete line | Ctrl+Y |
| Move line | Alt+Shift+Up/Down |
| Toggle comment | Ctrl+/ (line), Ctrl+Shift+/ (block) |
| Reformat | Basic indent normalization |
| Optimize imports | Sort and organize import statements |
| Rename | In-file rename with preview |
| Word wrap | Toggleable |
| Code folding | Fold/unfold regions |
| Bookmarks | Toggle and navigate between bookmarks |
| Split editor | Vertical and horizontal splits |

### Build System Auto-Detection

| File Present | Build Command | Clean Command | Run Command | Test Command |
|-------------|--------------|---------------|-------------|-------------|
| pom.xml | `mvn compile` | `mvn clean` | `mvn exec:java` | `mvn test` |
| build.gradle(.kts) | `./gradlew build` | `./gradlew clean` | `./gradlew run` | `./gradlew test` |
| Makefile | `make` | `make clean` | `make run` | `make test` |
| Cargo.toml | `cargo build` | `cargo clean` | `cargo run` | `cargo test` |
| package.json | `npm run build` | `rm -rf dist` | `npm start` | `npm test` |

### Theme (Darcula-adjacent, JDesk Dark)

| Element | Color | Hex |
|---------|-------|-----|
| Background | Dark charcoal | #1E1F22 |
| Editor | Slightly lighter | #2B2D30 |
| Sidebar | Panel dark | #26282E |
| Text | Light grey | #BCBEC4 |
| Keywords | Orange | #CF8E6D |
| Strings | Green | #6AAB73 |
| Comments | Grey | #7A7E85 |
| Types/Classes | Blue | #5E97D0 |
| Methods | Bright blue | #56A8F5 |
| Fields | Purple | #C77DBB |
| Annotations | Yellow | #BBB529 |
| Numbers | Teal | #2AACB8 |
| Line numbers | Dim | #4E5157 |
| Selection | Accent blue | #4A88C7 |
| Font | JetBrains Mono, 13px | — |

---

## Disk Allocation

| Category | Allocation |
|----------|-----------|
| Linux native binaries | 900 MB |
| Windows PE binaries (Wine layer) | 450 MB |
| macOS Mach-O binaries (Darling layer) | 350 MB |
| Wine runtime | 400 MB |
| Darling runtime | 300 MB |
| Icon assets + metadata | 50 MB |
| Headroom / updates | 550 MB |
| **Total** | **3 GB** |

> **Note:** 2 GB is insufficient for all three OS native stacks. Base allocation increased to **3 GB** to accommodate Wine (~400 MB), Darling (~300 MB), and the native binaries with headroom for updates.

## Launch Flow

```
User double-clicks desktop icon
        │
        ▼
┌────────────────────────────────────┐
│ JDesk Icon Handler                 │
│ Reads .jdesk-app manifest          │
│ Detects binary format (magic bytes)│
└───────────────┬────────────────────┘
                │
                ▼
┌────────────────────────────────────┐
│ Format Router                      │
│ ELF → direct exec                  │
│ PE  → wine prefix + exec           │
│ Mach-O → darling prefix + exec     │
│ Script → interpreter dispatch      │
└───────────────┬────────────────────┘
                │
                ▼
┌────────────────────────────────────────────────────────────┐
│ java -memory-guard -Xguard:profile=<app-profile> <binary>  │
│                                                            │
│ Memory Proxy governs:                                      │
│   • RAM budget (per-app configured)                        │
│   • Disk I/O rate limiting                                 │
│   • CPU time accounting                                    │
│   • Thread/process count ceiling                           │
│   • Telemetry to JDesk status bar                          │
└────────────────────────────────────────────────────────────┘
```

## Application Profiles (jvm-config.xml)

Each desktop application has a tuned resource profile:

| Application | RAM Soft | RAM Hard | CPU | Threads | Disk Write |
|-------------|----------|----------|-----|---------|-----------|
| Writer | 512 MB | 2 GB | 80% | 32 | 200 MB/s |
| IDE | 2 GB | 4 GB | 90% | 128 | 500 MB/s |
| Browser | 1 GB | 4 GB | 90% | 256 | 200 MB/s |
| Terminal | 64 MB | 256 MB | 50% | 16 | 50 MB/s |
| Files | 128 MB | 512 MB | 40% | 16 | 100 MB/s |

## Cross-Platform Execution

### Windows Binaries (PE)
Wine provides Win32/Win64 API translation. The Memory Proxy wraps the Wine process itself, governing the Windows binary indirectly:
```
java -memory-guard -Xguard:profile=windows-app wine /path/to/app.exe
```

### macOS Binaries (Mach-O)
Darling provides macOS API translation (similar to Wine for Windows). Still experimental but functional for CLI tools and some GUI apps:
```
java -memory-guard -Xguard:profile=macos-app darling shell /path/to/app
```

### Native Linux (ELF)
Direct execution — the primary path. Full Memory Proxy governance with zero translation overhead:
```
java -memory-guard -Xguard:profile=writer /opt/jdesk/apps/libreoffice/soffice --writer
```

## Installation

```bash
cd userland/jdesk/native-apps
make install-linux       # Install Linux native applications
make install-wine        # Install Wine + Windows app support
make install-darling     # Install Darling + macOS app support
make install-icons       # Install desktop icons and manifests
make install-vfs         # Create and mount JDesk VFS (EXT4 + NTFS images)
make install-all         # Everything
```

## JDesk Virtual File System (VFS)

The Files application provides access to JDesk's own internal file structure via two loop-mounted disk images, plus direct access to the native OS filesystem.

### Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  JDesk Files Application (Sidebar)                                   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ ╔═══════════════════════════════════════════╗                 │   │
│  │ ║ JDesk                                    ║                 │   │
│  │ ║   📂 JDesk Home    → /opt/jdesk/home/   ║  EXT4 image     │   │
│  │ ║   💾 JDesk Share   → /opt/jdesk/share/  ║  NTFS image     │   │
│  │ ║   📁 JDesk Desktop                      ║  EXT4 subdir    │   │
│  │ ║   📂 JDesk Projects                     ║  EXT4 subdir    │   │
│  │ ╠═══════════════════════════════════════════╣                 │   │
│  │ ║ Folders (within EXT4 VFS)                ║                 │   │
│  │ ║   📄 Documents                           ║                 │   │
│  │ ║   ⬇ Downloads                            ║                 │   │
│  │ ║   🖼 Pictures / 🎵 Music / 🎬 Videos      ║                 │   │
│  │ ╠═══════════════════════════════════════════╣                 │   │
│  │ ║ Share (NTFS — Windows-compatible)        ║                 │   │
│  │ ║   🔄 Exchange                            ║                 │   │
│  │ ║   📤 Transfer                            ║                 │   │
│  │ ║   📋 Shared Docs                         ║                 │   │
│  │ ║   🔌 USB Import                          ║                 │   │
│  │ ╠═══════════════════════════════════════════╣                 │   │
│  │ ║ Native System (host OS)                  ║                 │   │
│  │ ║   🏠 Home (~)                            ║  Native FS      │   │
│  │ ║   💻 Computer (/)                        ║  Native FS      │   │
│  │ ║   📦 /opt                                ║  Native FS      │   │
│  │ ║   💿 /media (USB)                        ║  Native FS      │   │
│  │ ║   📌 /mnt                                ║  Native FS      │   │
│  │ ╚═══════════════════════════════════════════╝                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Disk Images

| Image | Mount Point | Filesystem | Size | Purpose |
|-------|-------------|-----------|------|---------|
| `jdesk-ext4.img` | `/opt/jdesk/home/` | EXT4 | 2 GB (expandable to 8 GB) | Primary JDesk internal storage |
| `jdesk-ntfs.img` | `/opt/jdesk/share/` | NTFS 3.1 | 1 GB (expandable to 4 GB) | Cross-platform file exchange |

### Why Both EXT4 and NTFS?

| Feature | EXT4 (JDesk Home) | NTFS (JDesk Share) |
|---------|-------------------|-------------------|
| Linux-native performance | ✓ Fast (journaled, extents) | Slower (FUSE translation) |
| POSIX permissions | ✓ Full (rwx, ACLs, xattrs) | Partial (mapped) |
| Windows-readable | ✗ Needs ext2fsd/WSL | ✓ Native on Windows |
| macOS-readable | ✗ Needs driver | ✓ Via Paragon/macFUSE |
| USB drive exchange | Manual copy | ✓ Direct (NTFS on USB) |
| Journaling | ✓ Metadata + data | ✓ Metadata |
| Sparse files | ✓ Efficient | ✓ Supported |
| Hard links | ✓ Supported | ✓ Supported |
| Max file size | 16 TB | 16 EB |

### EXT4 Directory Structure (/opt/jdesk/home/)

```
/opt/jdesk/home/
├── Desktop/                 - Desktop files
├── Documents/               - User documents
├── Downloads/               - Downloaded files
├── Music/                   - Audio files
├── Pictures/                - Image files
├── Videos/                  - Video files
├── Projects/                - Development workspace
│   ├── java/
│   ├── web/
│   └── scripts/
├── Templates/               - File templates
├── Public/                  - Publicly shared files
├── .jdesk/                  - JDesk configuration
│   ├── settings/            - Application settings
│   ├── cache/               - Temporary cache
│   ├── themes/              - Custom themes
│   └── plugins/             - JDesk plugins
└── .config/                 - XDG config
```

### NTFS Directory Structure (/opt/jdesk/share/)

```
/opt/jdesk/share/
├── Exchange/                - Quick file exchange (Windows ↔ Linux)
├── Transfer/                - Outbound file transfers
├── Shared Documents/        - Documents readable on all platforms
└── USB Import/              - Files imported from USB drives
```

### Setup

```bash
# Create and mount VFS images (requires root for loop mount)
sudo ./native-apps/scripts/jdesk-vfs-setup.sh

# Enable auto-mount at boot (systemd)
sudo cp native-apps/scripts/opt-jdesk-home.mount /etc/systemd/system/
sudo cp native-apps/scripts/opt-jdesk-share.mount /etc/systemd/system/
sudo systemctl enable opt-jdesk-home.mount opt-jdesk-share.mount

# Expand later if needed
sudo truncate -s 8G /opt/jdesk/vfs/jdesk-ext4.img && sudo resize2fs /opt/jdesk/vfs/jdesk-ext4.img
sudo truncate -s 4G /opt/jdesk/vfs/jdesk-ntfs.img && sudo ntfsresize /opt/jdesk/vfs/jdesk-ntfs.img
```

### VFS Files

```
userland/jdesk/native-apps/scripts/
├── jdesk-vfs-setup.sh          - VFS provisioner (creates + mounts images)
├── opt-jdesk-home.mount         - Systemd mount unit (EXT4)
└── opt-jdesk-share.mount        - Systemd mount unit (NTFS)
```

## Files

```
userland/jdesk/native-apps/
├── README.md                       - This file
├── Makefile                        - Build/install targets
├── manifests/                      - .jdesk-app manifest files
│   ├── writer.jdesk-app
│   ├── ide.jdesk-app              - Full IntelliJ feature parity (130+ actions)
│   ├── browser.jdesk-app
│   ├── terminal.jdesk-app
│   ├── files.jdesk-app
│   └── kali.jdesk-app
├── icons/                          - SVG desktop icons
│   ├── writer.svg
│   ├── ide.svg
│   ├── browser.svg
│   ├── terminal.svg
│   ├── files.svg
│   ├── settings.svg
│   ├── software.svg
│   ├── launcher.svg
│   └── kali.svg
├── profiles/                       - Memory Proxy resource profiles
│   └── jdesk-apps.xml
├── launcher/
│   ├── NativeAppLauncher.java      - Format detection + launch logic
│   ├── DesktopIconGrid.java        - Icon grid rendering
│   ├── LibraryLinker.java          - Native library linking
│   ├── jdesk-libhost.c            - Native library host (C)
│   └── jdesk-dllhost.c            - DLL host for Wine (C)
├── scripts/
│   ├── install-natives.sh          - Master installer (3 GB allocation)
│   ├── install-vscodium.sh         - VSCodium IDE installer
│   ├── install-chromium.sh         - Chromium browser installer
│   ├── install-libreoffice.sh      - LibreOffice installer
│   ├── install-pcmanfm.sh          - File manager installer
│   └── install-terminal.sh         - JDesk Terminal build
├── wine/
│   └── install-wine.sh             - Wine installation script
├── darling/
│   └── install-darling.sh          - Darling installation script
└── kali-tools/
    ├── kali-provision              - Kali Linux security tools provisioner
    └── kali-packages.json          - Security tool package list
```

## License

GPL-2.0

## Copyright

Copyright (C) 2026 MEARVK LLC
Author: Maximilian Eric Alexander Rupplin von Keffikon
