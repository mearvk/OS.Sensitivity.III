/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Files — JavaFX File Manager with Virtual Filesystem Support.
 *
 * A dual-pane file manager with:
 *   - Sidebar (JDesk VFS, user bookmarks, native OS)
 *   - Main area (icon view or list view)
 *   - Path breadcrumb bar
 *   - File operations (copy, move, delete, rename, create)
 *   - Thumbnail previews for images
 *
 * Virtual Filesystem Architecture:
 *   JDesk maintains its own internal file structure via two loop-mounted
 *   disk images — one EXT4 (primary, journaled) and one NTFS (cross-platform
 *   exchange). The Files app provides access to:
 *
 *   1. JDesk Home (/opt/jdesk/home/)  — EXT4 image, journaled, Linux-native
 *      Desktop, Documents, Downloads, Music, Pictures, Videos, Projects
 *      .jdesk/{settings, cache, themes, plugins}
 *
 *   2. JDesk Share (/opt/jdesk/share/) — NTFS image, Windows-compatible
 *      Exchange, Transfer, Shared Documents, USB Import
 *      Files here are readable on Windows/macOS without conversion.
 *
 *   3. Native OS (/)                   — Host operating system filesystem
 *      Full access to the underlying Linux filesystem (EXT4, XFS, Btrfs)
 *      Includes /home, /etc, /opt, /mnt, /media (USB), etc.
 *
 * Why both EXT4 and NTFS?
 *   - EXT4: Optimal for Linux operations — inodes, permissions, xattrs,
 *     hardlinks, sparse files, journaling, fast fsync
 *   - NTFS: Cross-platform exchange — files readable on Windows, USB drives,
 *     dual-boot systems. MFT, streams, compression-ready.
 *
 * Native backend: PCManFM-Qt or Nautilus for advanced operations.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.apps;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.geometry.*;
import javafx.collections.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.text.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

/**
 * JDeskFiles — A JavaFX file manager.
 */
public class JDeskFiles extends BorderPane {

    // Theme (light theme for file manager — matches JDesk white)
    private static final String BG_MAIN    = "#FFFFFF";
    private static final String BG_SIDEBAR = "#F7F8FA";
    private static final String BG_TOOLBAR = "#FFFFFF";
    private static final String BORDER     = "#DADCE0";
    private static final String TEXT_PRI   = "#202124";
    private static final String TEXT_SEC   = "#5F6368";
    private static final String ACCENT     = "#1A73E8";
    private static final String HOVER_BG   = "#E8F0FE";
    private static final String FONT_UI    = "Inter, system-ui, sans-serif";

    // State
    private Path currentPath;
    private ListView<FileEntry> fileList;
    private Label pathLabel;
    private Label statusLabel;
    private boolean showHidden = false;

    public JDeskFiles() {
        // Default to JDesk Home (EXT4 VFS) if available, fall back to native home
        Path jdeskHome = Path.of("/opt/jdesk/home");
        this(Files.isDirectory(jdeskHome) ? jdeskHome : Path.of(System.getProperty("user.home")));
    }

    public JDeskFiles(Path startPath) {
        setStyle("-fx-background-color: " + BG_MAIN + ";");
        currentPath = startPath;

        // Toolbar
        setTop(createToolbar());

        // Sidebar
        setLeft(createSidebar());

        // File list (center)
        fileList = new ListView<>();
        fileList.setStyle(
            "-fx-background-color: " + BG_MAIN + ";" +
            "-fx-border-width: 0;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        fileList.setCellFactory(lv -> new FileListCell());
        fileList.setOnMouseClicked(this::handleFileClick);
        fileList.setOnKeyPressed(this::handleFileKey);
        setCenter(fileList);

        // Status bar
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setStyle(
            "-fx-background-color: " + BG_MAIN + ";" +
            "-fx-border-color: " + BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );
        statusLabel = new Label("");
        statusLabel.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        statusBar.getChildren().add(statusLabel);
        setBottom(statusBar);

        // Load initial directory
        navigateTo(currentPath);

        setFocusTraversable(true);
    }

    // === Public API ===

    public void navigateTo(Path path) {
        if (!Files.isDirectory(path)) return;
        currentPath = path;
        pathLabel.setText("  " + currentPath.toString());
        loadDirectory();
    }

    public double getFilesWidth() { return 900; }
    public double getFilesHeight() { return 600; }

    // === Toolbar ===

    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 12, 6, 12));
        toolbar.setStyle(
            "-fx-background-color: " + BG_TOOLBAR + ";" +
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Button backBtn = toolBtn("←", "Back", () -> goUp());
        Button upBtn = toolBtn("↑", "Parent Directory", () -> goUp());
        Button homeBtn = toolBtn("⌂", "Home", () -> navigateTo(Path.of(System.getProperty("user.home"))));
        Button refreshBtn = toolBtn("⟳", "Refresh", () -> loadDirectory());

        pathLabel = new Label("  " + currentPath.toString());
        pathLabel.setStyle(
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-padding: 4 12 4 12;" +
            "-fx-background-radius: 4;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 4;"
        );
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        CheckBox hiddenCb = new CheckBox("Show Hidden");
        hiddenCb.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px;");
        hiddenCb.setOnAction(e -> { showHidden = hiddenCb.isSelected(); loadDirectory(); });

        toolbar.getChildren().addAll(backBtn, upBtn, homeBtn, refreshBtn, pathLabel, hiddenCb);
        return toolbar;
    }

    private Button toolBtn(String text, String tooltip, Runnable action) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMinSize(28, 28);
        btn.setMaxSize(28, 28);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 14px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + HOVER_BG + ";" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 14px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 14px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        ));
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // === Sidebar (Bookmarks) ===

    private VBox createSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.setPrefWidth(200);
        sidebar.setPadding(new Insets(8));
        sidebar.setStyle(
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-border-color: transparent " + BORDER + " transparent transparent;" +
            "-fx-border-width: 0 1 0 0;"
        );

        // === JDesk Virtual Filesystem ===
        Label vfsHeader = sidebarHeader("JDesk");
        sidebar.getChildren().add(vfsHeader);

        addBookmark(sidebar, "📂 JDesk Home", Path.of("/opt/jdesk/home"),
            "EXT4 — Internal storage (journaled, Linux-native)");
        addBookmark(sidebar, "💾 JDesk Share", Path.of("/opt/jdesk/share"),
            "NTFS — Cross-platform exchange (Windows-compatible)");
        addBookmark(sidebar, "📁 JDesk Desktop", Path.of("/opt/jdesk/home/Desktop"),
            "EXT4 — Desktop files");
        addBookmark(sidebar, "📂 JDesk Projects", Path.of("/opt/jdesk/home/Projects"),
            "EXT4 — Project workspace");

        sidebar.getChildren().add(new Separator());

        // === User Folders (within JDesk VFS) ===
        Label userHeader = sidebarHeader("Folders");
        sidebar.getChildren().add(userHeader);

        addBookmark(sidebar, "📄 Documents", Path.of("/opt/jdesk/home/Documents"),
            "EXT4 — User documents");
        addBookmark(sidebar, "⬇ Downloads", Path.of("/opt/jdesk/home/Downloads"),
            "EXT4 — Downloaded files");
        addBookmark(sidebar, "🖼 Pictures", Path.of("/opt/jdesk/home/Pictures"),
            "EXT4 — Image files");
        addBookmark(sidebar, "🎵 Music", Path.of("/opt/jdesk/home/Music"),
            "EXT4 — Audio files");
        addBookmark(sidebar, "🎬 Videos", Path.of("/opt/jdesk/home/Videos"),
            "EXT4 — Video files");

        sidebar.getChildren().add(new Separator());

        // === NTFS Exchange Folders ===
        Label ntfsHeader = sidebarHeader("Share (NTFS)");
        sidebar.getChildren().add(ntfsHeader);

        addBookmark(sidebar, "🔄 Exchange", Path.of("/opt/jdesk/share/Exchange"),
            "NTFS — Quick file exchange");
        addBookmark(sidebar, "📤 Transfer", Path.of("/opt/jdesk/share/Transfer"),
            "NTFS — Outbound transfers");
        addBookmark(sidebar, "📋 Shared Docs", Path.of("/opt/jdesk/share/Shared Documents"),
            "NTFS — Shared documents (Windows-readable)");
        addBookmark(sidebar, "🔌 USB Import", Path.of("/opt/jdesk/share/USB Import"),
            "NTFS — Files imported from USB drives");

        sidebar.getChildren().add(new Separator());

        // === Native OS Filesystem ===
        Label nativeHeader = sidebarHeader("Native System");
        sidebar.getChildren().add(nativeHeader);

        String home = System.getProperty("user.home");
        addBookmark(sidebar, "🏠 Home (" + Path.of(home).getFileName() + ")", Path.of(home),
            "Native OS — User home directory");
        addBookmark(sidebar, "💻 Computer (/)", Path.of("/"),
            "Native OS — Root filesystem (EXT4/XFS/Btrfs)");
        addBookmark(sidebar, "📦 /opt", Path.of("/opt"),
            "Native OS — Optional software");
        addBookmark(sidebar, "💿 /media", Path.of("/media"),
            "Native OS — Removable media (USB, CD-ROM)");
        addBookmark(sidebar, "📌 /mnt", Path.of("/mnt"),
            "Native OS — Manual mount points");
        addBookmark(sidebar, "🗑 Trash", Path.of(home, ".local/share/Trash/files"),
            "Native OS — Deleted files");

        sidebar.getChildren().add(new Separator());

        // === VFS Info ===
        Label infoLabel = new Label(
            "  EXT4: /opt/jdesk/home (2 GB)\n" +
            "  NTFS: /opt/jdesk/share (1 GB)"
        );
        infoLabel.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 9px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 8 4 4 4;"
        );
        sidebar.getChildren().add(infoLabel);

        // Wrap in scroll pane for small screens
        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + BG_SIDEBAR + "; -fx-border-width: 0;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // We return a VBox wrapping the scroll pane
        VBox container = new VBox(scroll);
        container.setPrefWidth(200);
        container.setStyle("-fx-background-color: " + BG_SIDEBAR + ";");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return container;
    }

    private Label sidebarHeader(String text) {
        Label header = new Label(text);
        header.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 8 8 4 8;"
        );
        return header;
    }

    private void addBookmark(VBox sidebar, String label, Path path, String tooltip) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + HOVER_BG + ";" +
            "-fx-text-fill: " + ACCENT + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnAction(e -> {
            if (Files.isDirectory(path)) navigateTo(path);
            else statusLabel.setText("Directory not available: " + path +
                " (run jdesk-vfs-setup.sh to create VFS)");
        });
        sidebar.getChildren().add(btn);
    }

    // === File List ===

    private void loadDirectory() {
        List<FileEntry> entries = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!showHidden && name.startsWith(".")) continue;

                boolean isDir = Files.isDirectory(entry);
                long size = 0;
                Instant modified = Instant.EPOCH;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    size = attrs.size();
                    modified = attrs.lastModifiedTime().toInstant();
                } catch (IOException ignored) {}

                entries.add(new FileEntry(name, entry, isDir, size, modified));
            }
        } catch (IOException e) {
            statusLabel.setText("Error reading directory: " + e.getMessage());
            return;
        }

        // Sort: directories first, then by name
        entries.sort((a, b) -> {
            if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        fileList.getItems().setAll(entries);
        statusLabel.setText(entries.size() + " items in " + currentPath.getFileName());
    }

    private void goUp() {
        Path parent = currentPath.getParent();
        if (parent != null) navigateTo(parent);
    }

    private void handleFileClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            FileEntry selected = fileList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                if (selected.isDirectory) {
                    navigateTo(selected.path);
                } else {
                    openWithDefault(selected.path);
                }
            }
        }
    }

    private void handleFileKey(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            FileEntry selected = fileList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                if (selected.isDirectory) navigateTo(selected.path);
                else openWithDefault(selected.path);
            }
        } else if (event.getCode() == KeyCode.BACK_SPACE) {
            goUp();
        }
    }

    private void openWithDefault(Path file) {
        // Try xdg-open
        try {
            new ProcessBuilder("xdg-open", file.toString())
                .redirectErrorStream(true).start();
        } catch (IOException e) {
            statusLabel.setText("Cannot open: " + file.getFileName());
        }
    }

    // === Data Model ===

    private static class FileEntry {
        final String name;
        final Path path;
        final boolean isDirectory;
        final long size;
        final Instant modified;

        FileEntry(String name, Path path, boolean isDirectory, long size, Instant modified) {
            this.name = name;
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
            this.modified = modified;
        }

        String getIcon() {
            if (isDirectory) return "📁";
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            switch (ext) {
                case "java": case "kt": case "py": case "c": case "cpp": case "h":
                case "js": case "ts": case "rs": case "go": return "📝";
                case "txt": case "md": case "log": return "📄";
                case "html": case "htm": case "xml": case "json": case "yaml": return "🌐";
                case "png": case "jpg": case "jpeg": case "gif": case "svg": case "bmp": return "🖼";
                case "mp3": case "wav": case "flac": case "ogg": return "🎵";
                case "mp4": case "mkv": case "avi": case "mov": return "🎬";
                case "pdf": return "📕";
                case "zip": case "tar": case "gz": case "xz": case "7z": return "📦";
                case "sh": case "bash": return "⚙";
                case "deb": case "rpm": return "📥";
                default: return "📄";
            }
        }

        String getSizeStr() {
            if (isDirectory) return "—";
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return (size / 1024) + " KB";
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }

        String getDateStr() {
            if (modified.equals(Instant.EPOCH)) return "—";
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(modified);
        }

        @Override
        public String toString() { return name; }
    }

    // === Cell Renderer ===

    private class FileListCell extends ListCell<FileEntry> {
        @Override
        protected void updateItem(FileEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 8, 3, 8));

            Label icon = new Label(item.getIcon());
            icon.setMinWidth(24);
            icon.setStyle("-fx-font-size: 16px;");

            Label name = new Label(item.name);
            name.setStyle(
                "-fx-text-fill: " + TEXT_PRI + ";" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: " + FONT_UI + ";" +
                (item.isDirectory ? "-fx-font-weight: 600;" : "")
            );
            name.setPrefWidth(300);

            Label size = new Label(item.getSizeStr());
            size.setStyle(
                "-fx-text-fill: " + TEXT_SEC + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-family: " + FONT_UI + ";"
            );
            size.setPrefWidth(80);
            size.setAlignment(Pos.CENTER_RIGHT);

            Label date = new Label(item.getDateStr());
            date.setStyle(
                "-fx-text-fill: " + TEXT_SEC + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-family: " + FONT_UI + ";"
            );

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(icon, name, spacer, size, date);
            setGraphic(row);
            setText(null);
        }
    }
}
