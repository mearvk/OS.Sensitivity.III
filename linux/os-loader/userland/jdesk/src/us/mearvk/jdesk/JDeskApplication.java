/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDeskApplication — Main entry point for the JDesk desktop environment.
 *
 * Starts a full-screen JavaFX application that serves as:
 *   - Desktop surface (wallpaper, icon grid)
 *   - Window compositor (frames native apps)
 *   - Panel/taskbar
 *   - Application launcher
 *
 * This can run:
 *   1. As a standalone desktop (replacing the existing WM)
 *   2. As a windowed application (for testing/development)
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.scene.effect.*;
import javafx.geometry.*;
import javafx.stage.*;
import javafx.animation.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;

/**
 * JDeskApplication — The JDesk desktop environment.
 *
 * Launch:
 *   java --module-path /usr/share/openjfx/lib \
 *        --add-modules javafx.controls,javafx.graphics \
 *        -cp /opt/jdesk/lib/jdesk.jar \
 *        us.mearvk.jdesk.JDeskApplication
 *
 * Or windowed mode (for testing):
 *   java ... us.mearvk.jdesk.JDeskApplication --windowed
 */
public class JDeskApplication extends Application {

    // Layout components
    private BorderPane root;
    private Pane desktopSurface;
    private HBox panel;
    private Label clockLabel;
    private GridPane iconGrid;

    // State
    private boolean windowedMode = false;

    @Override
    public void init() {
        List<String> params = getParameters().getRaw();
        windowedMode = params.contains("--windowed") || params.contains("-w");
    }

    @Override
    public void start(Stage primaryStage) {
        // === Root Layout ===
        root = new BorderPane();

        // Startup sentinel: write an early marker so external scripts know the JavaFX app started
        try {
            String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            Files.writeString(Paths.get("/tmp/jdesk-startup.log"), ts + " JDeskApplication.start invoked\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Best-effort only
        }

        // === Desktop Surface (center) ===
        desktopSurface = new Pane();
        desktopSurface.setStyle(
            "-fx-background-color: radial-gradient(" +
            "center 50% 45%, radius 70%, " +
            "#2A2A2E 0%, #1E1E22 50%, #141418 100%);"
        );

        // === Icon Grid on Desktop ===
        iconGrid = createDesktopIcons();
        desktopSurface.getChildren().add(iconGrid);

        // === Panel (bottom taskbar) ===
        panel = createPanel();

        // === Assemble ===
        root.setCenter(desktopSurface);
        root.setBottom(panel);

        // === Scene ===
        Scene scene;
        if (windowedMode) {
            scene = new Scene(root, 1280, 800);
            primaryStage.setTitle("JDesk — Galactic Cherry Marvell Edition 98");
        } else {
            // Full screen
            scene = new Scene(root);
            primaryStage.setFullScreen(true);
            primaryStage.setFullScreenExitHint("");
        }

        // Apply cool-white theme CSS
        Path themePath = Path.of("/opt/jdesk/themes/cool-white.css");
        if (Files.exists(themePath)) {
            scene.getStylesheets().add(themePath.toUri().toString());
        }
        // Also try local path (development)
        Path localTheme = Path.of("userland/jdesk/themes/cool-white.css");
        if (Files.exists(localTheme)) {
            scene.getStylesheets().add(localTheme.toUri().toString());
        }

        primaryStage.setScene(scene);
        primaryStage.show();

        // Start clock
        startClock();

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  JDesk — Galactic Cherry Marvell Edition 98");
        System.out.println("  Theme: Cool White (original)");
        System.out.println("  Mode:  " + (windowedMode ? "Windowed (development)" : "Full-screen"));
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    // =========================================================================
    //  Desktop Icons
    // =========================================================================

    private GridPane createDesktopIcons() {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(24);
        grid.setPadding(new Insets(48));
        grid.setLayoutX(40);
        grid.setLayoutY(40);

        // Load the single JDesk icon (Java cup, used for all apps)
        Image jdeskIcon = loadJDeskIcon();

        // Desktop icon entries: name, action
        String[][] icons = {
            {"Settings",  "settings"},
            {"Terminal",  "terminal"},
            {"IDE",       "ide"},
            {"VSCodium",  "vscodium"},
            {"Browser",   "browser"},
            {"Writer",    "writer"},
            {"Files",     "files"},
            {"Software",  "software"},
            {"Launcher",  "launcher"},
            {"Kali",      "kali"},
        };

        int col = 0, row = 0;
        for (String[] entry : icons) {
            VBox cell = createIconCell(entry[0], entry[1], jdeskIcon);
            grid.add(cell, col, row);
            col++;
            if (col >= 5) { col = 0; row++; }
        }

        // Enable drag-to-reorder on the grid
        enableGridDrag(grid);

        return grid;
    }

    /**
     * Load the JDesk icon PNG. Tries multiple paths.
     */
    private Image loadJDeskIcon() {
        String[] paths = {
            "userland/jdesk/native-apps/icons/jdesk-icon.png",
            "native-apps/icons/jdesk-icon.png",
            "/opt/jdesk/icons/jdesk-icon.png",
            "icons/jdesk-icon.png"
        };

        for (String path : paths) {
            java.io.File f = new java.io.File(path);
            if (f.exists()) {
                return new Image(f.toURI().toString(), 64, 64, true, true);
            }
        }

        // Fallback: return null (icon cell will be empty)
        System.err.println("[JDesk] Warning: jdesk-icon.png not found");
        return null;
    }

    /**
     * Create a desktop icon cell with the JDesk icon and gray/white label.
     */
    private VBox createIconCell(String name, String action, Image icon) {
        VBox cell = new VBox(8);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(100, 110);
        cell.setPadding(new Insets(8));
        cell.setStyle("-fx-background-color: transparent;");

        // Icon image (the Java cup PNG — same for all)
        ImageView iconView = new ImageView();
        iconView.setFitWidth(64);
        iconView.setFitHeight(64);
        iconView.setSmooth(true);
        iconView.setPreserveRatio(true);
        if (icon != null) {
            iconView.setImage(icon);
        }

        // Name label — gray and white
        Label nameLabel = new Label(name);
        nameLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-text-fill: #E8E8E8;" +
            "-fx-font-weight: 500;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 2, 0, 0, 1);"
        );
        nameLabel.setMaxWidth(92);
        nameLabel.setWrapText(true);
        nameLabel.setAlignment(Pos.CENTER);

        cell.getChildren().addAll(iconView, nameLabel);

        // Hover effect
        cell.setOnMouseEntered(e -> {
            cell.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.08);" +
                "-fx-background-radius: 12;"
            );
            nameLabel.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Inter', system-ui, sans-serif;" +
                "-fx-text-fill: #FFFFFF;" +
                "-fx-font-weight: 600;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 2, 0, 0, 1);"
            );
        });
        cell.setOnMouseExited(e -> {
            cell.setStyle("-fx-background-color: transparent;");
            nameLabel.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Inter', system-ui, sans-serif;" +
                "-fx-text-fill: #E8E8E8;" +
                "-fx-font-weight: 500;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 2, 0, 0, 1);"
            );
        });

        // Double-click to launch
        cell.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                launchDesktopApp(action, name);
            }
        });

        return cell;
    }

    // =========================================================================
    //  Grid Drag-to-Reorder (move icons freely on grid)
    // =========================================================================

    private double dragStartX, dragStartY;
    private VBox draggedCell = null;

    private void enableGridDrag(GridPane grid) {
        for (javafx.scene.Node node : grid.getChildren()) {
            if (node instanceof VBox) {
                VBox cell = (VBox) node;
                enableCellDrag(cell, grid);
            }
        }
    }

    private void enableCellDrag(VBox cell, GridPane grid) {
        cell.setOnMousePressed(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 1) {
                draggedCell = cell;
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();
                cell.setOpacity(0.7);
                cell.toFront();
            }
        });

        cell.setOnMouseDragged(e -> {
            if (draggedCell == cell) {
                double deltaX = e.getSceneX() - dragStartX;
                double deltaY = e.getSceneY() - dragStartY;
                cell.setTranslateX(deltaX);
                cell.setTranslateY(deltaY);
            }
        });

        cell.setOnMouseReleased(e -> {
            if (draggedCell == cell) {
                cell.setOpacity(1.0);

                // Determine new grid position from drop location
                double dropX = e.getSceneX() - grid.getLayoutX() - grid.getPadding().getLeft();
                double dropY = e.getSceneY() - grid.getLayoutY() - grid.getPadding().getTop();

                int newCol = Math.max(0, Math.min(3, (int)(dropX / 120)));
                int newRow = Math.max(0, (int)(dropY / 130));

                // Get current position
                Integer oldCol = GridPane.getColumnIndex(cell);
                Integer oldRow = GridPane.getRowIndex(cell);
                if (oldCol == null) oldCol = 0;
                if (oldRow == null) oldRow = 0;

                // Swap with whatever is at the new position
                for (javafx.scene.Node other : grid.getChildren()) {
                    Integer otherCol = GridPane.getColumnIndex(other);
                    Integer otherRow = GridPane.getRowIndex(other);
                    if (otherCol == null) otherCol = 0;
                    if (otherRow == null) otherRow = 0;

                    if (otherCol == newCol && otherRow == newRow && other != cell) {
                        GridPane.setColumnIndex(other, oldCol);
                        GridPane.setRowIndex(other, oldRow);
                        break;
                    }
                }

                GridPane.setColumnIndex(cell, newCol);
                GridPane.setRowIndex(cell, newRow);

                // Reset translation
                cell.setTranslateX(0);
                cell.setTranslateY(0);
                draggedCell = null;
            }
        });
    }

    // =========================================================================
    //  Panel (Taskbar)
    // =========================================================================

    private HBox createPanel() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefHeight(48);
        bar.setPadding(new Insets(0, 16, 0, 16));
        bar.setStyle(
            "-fx-background-color: rgba(30, 30, 34, 0.95);" +
            "-fx-border-color: #3A3A40 transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );

        // JDesk logo / activities button
        Label logo = new Label("◉ JDesk");
        logo.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #A0A0A8;"
        );

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Clock
        clockLabel = new Label(getCurrentTime());
        clockLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 500;" +
            "-fx-text-fill: #B0B0B8;"
        );

        // System indicators
        Label indicators = new Label("⚡ 🔊 🌐");
        indicators.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #888890;"
        );

        bar.getChildren().addAll(logo, spacer, indicators, clockLabel);
        return bar;
    }

    // =========================================================================
    //  Clock
    // =========================================================================

    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            clockLabel.setText(getCurrentTime());
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // =========================================================================
    //  Application Launching — ALL programs route through JDesk governance
    // =========================================================================

    /*
     * CRITICAL DESIGN DECISION:
     *
     * Programs do NOT call the underlying OS directly. Every executable launched
     * from JDesk goes through the JVM Memory Proxy governance layer:
     *
     *   java -memory-guard -Xguard:profile=<profile> <binary> [args...]
     *
     * This ensures:
     *   1. Memory budgets are enforced (RAM ceiling per-app)
     *   2. Disk I/O is rate-limited (prevents runaway writes)
     *   3. CPU time is accounted (no single app starves others)
     *   4. Thread/process count is bounded (no fork bombs)
     *   5. Crash telemetry is captured (JDesk knows what happened)
     *   6. The WindowCompositor can frame the native window
     *   7. Security: binary path validation against allowed roots
     *   8. The panel/taskbar shows live resource usage per-app
     *
     * The JDesk IS the interpretive layer. It stands between the user and the OS.
     */

    /** Resource profiles for desktop applications. */
    private static final Map<String, String[]> APP_PROFILES = Map.of(
        "terminal", new String[]{"-Xguard:ram=256m", "-Xguard:cpu=50", "-Xguard:threads=16"},
        "files",    new String[]{"-Xguard:ram=512m", "-Xguard:cpu=40", "-Xguard:threads=16"},
        "browser",  new String[]{"-Xguard:ram=4g",   "-Xguard:cpu=90", "-Xguard:threads=256"},
        "writer",   new String[]{"-Xguard:ram=2g",   "-Xguard:cpu=80", "-Xguard:threads=32"},
        "ide",      new String[]{"-Xguard:ram=4g",   "-Xguard:cpu=90", "-Xguard:threads=128"},
        "settings", new String[]{"-Xguard:ram=128m", "-Xguard:cpu=30", "-Xguard:threads=8"}
    );

    private void launchDesktopApp(String action, String displayName) {
        System.out.printf("[JDesk] Launching: %s (governed)%n", displayName);

        // --- JDesk-native apps (skinned in JavaFX, call native binary) ---
        if ("settings".equals(action)) {
            showSettingsDialog();
            return;
        }
        if ("terminal".equals(action)) {
            openJDeskTerminal();
            return;
        }
        if ("ide".equals(action)) {
            openJDeskIDE();
            return;
        }
        if ("browser".equals(action)) {
            openJDeskBrowser();
            return;
        }
        if ("writer".equals(action)) {
            openJDeskWriter();
            return;
        }
        if ("files".equals(action)) {
            openJDeskFiles();
            return;
        }
        if ("software".equals(action)) {
            openJDeskSoftware();
            return;
        }
        if ("launcher".equals(action)) {
            openJDeskLauncher();
            return;
        }

        try {
            // Resolve the binary path
            String binaryPath = resolveAppBinary(action);
            if (binaryPath == null) {
                System.err.printf("[JDesk] ERROR: No binary found for '%s'%n", action);
                return;
            }

            // Resolve extra arguments for the binary
            String[] binaryArgs = resolveAppArgs(action);

            // Build governed command: java -memory-guard -Xguard:... <binary> [args]
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-memory-guard");
            command.add("-Xguard:profile=" + action);
            command.add("-Xguard:status=5s");

            // Add profile-specific resource limits
            String[] limits = APP_PROFILES.getOrDefault(action,
                    new String[]{"-Xguard:ram=1g", "-Xguard:cpu=80", "-Xguard:threads=64"});
            for (String limit : limits) {
                command.add(limit);
            }

            // The binary itself
            command.add(binaryPath);

            // Binary-specific arguments
            if (binaryArgs != null) {
                for (String arg : binaryArgs) {
                    command.add(arg);
                }
            }

            System.out.printf("[JDesk]   Binary:  %s%n", binaryPath);
            System.out.printf("[JDesk]   Profile: %s%n", action);
            System.out.printf("[JDesk]   Limits:  %s%n", String.join(" ", limits));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.environment().put("JDESK_APP_NAME", displayName);
            pb.environment().put("JDESK_APP_PROFILE", action);
            pb.environment().put("JDESK_GOVERNED", "1");

            Process proc = pb.start();
            System.out.printf("[JDesk] ✓ Started: %s (PID via memory-guard)%n", displayName);

            // Monitor in background
            Thread monitor = new Thread(() -> {
                try {
                    int code = proc.waitFor();
                    System.out.printf("[JDesk] %s exited (code %d)%n", displayName, code);
                } catch (InterruptedException ignored) {}
            }, "jdesk-monitor-" + action);
            monitor.setDaemon(true);
            monitor.start();

        } catch (IOException e) {
            System.err.printf("[JDesk] Failed to launch %s: %s%n", displayName, e.getMessage());
        }
    }

    /**
     * Resolve the absolute binary path for a desktop action.
     * Searches JDesk app paths first, then system paths.
     * Returns null if no suitable binary found.
     */
    private String resolveAppBinary(String action) {
        // Priority: JDesk managed path → system path
        String[][] candidates;
        switch (action) {
            case "terminal":
                candidates = new String[][]{
                    {"/opt/jdesk/apps/terminal/jdesk-terminal"},
                    {"/usr/bin/gnome-terminal"},
                    {"/usr/bin/xfce4-terminal"},
                    {"/usr/bin/xterm"}
                };
                break;
            case "files":
                candidates = new String[][]{
                    {"/opt/jdesk/apps/pcmanfm/pcmanfm-qt"},
                    {"/usr/bin/pcmanfm-qt"},
                    {"/usr/bin/nautilus"},
                    {"/usr/bin/thunar"}
                };
                break;
            case "browser":
                candidates = new String[][]{
                    {"/opt/jdesk/apps/chromium/chrome"},
                    {"/usr/bin/chromium-browser"},
                    {"/usr/bin/chromium"},
                    {"/snap/bin/chromium"},
                    {"/usr/bin/firefox"}
                };
                break;
            case "writer":
                candidates = new String[][]{
                    {"/opt/jdesk/apps/libreoffice/soffice"},
                    {"/usr/bin/soffice"},
                    {"/usr/bin/libreoffice"}
                };
                break;
            case "ide":
                candidates = new String[][]{
                    {"/opt/jdesk/apps/vscodium/bin/codium"},
                    {"/opt/jdesk/bin/codium"},
                    {"/usr/bin/codium"},
                    {"/usr/bin/code"}
                };
                break;
            default:
                return null;
        }

        for (String[] candidate : candidates) {
            java.io.File f = new java.io.File(candidate[0]);
            if (f.exists() && f.canExecute()) {
                return candidate[0];
            }
            // Also check if it's a symlink that resolves
            if (Files.isSymbolicLink(Path.of(candidate[0]))) {
                try {
                    Path real = Files.readSymbolicLink(Path.of(candidate[0]));
                    if (Files.exists(real)) return candidate[0];
                } catch (IOException ignored) {}
            }
        }

        // Last resort: check PATH via 'which'
        String simpleName = getSimpleName(action);
        if (simpleName != null) {
            try {
                Process which = new ProcessBuilder("which", simpleName).start();
                if (which.waitFor() == 0) {
                    try (var reader = new BufferedReader(new InputStreamReader(which.getInputStream()))) {
                        String path = reader.readLine();
                        if (path != null && !path.isBlank()) return path.trim();
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private String getSimpleName(String action) {
        switch (action) {
            case "terminal": return "gnome-terminal";
            case "files": return "nautilus";
            case "browser": return "chromium-browser";
            case "writer": return "soffice";
            case "ide": return "codium";
            default: return null;
        }
    }

    /**
     * Resolve extra arguments for a specific application.
     */
    private String[] resolveAppArgs(String action) {
        switch (action) {
            case "writer": return new String[]{"--writer"};
            case "browser": return new String[]{"--no-sandbox"};
            default: return null;
        }
    }

    // =========================================================================
    //  JDesk Terminal (JavaFX GUI, native shell backend)
    // =========================================================================

    /**
     * Open a JDesk Terminal window — JavaFX renders the GUI,
     * /bin/bash runs as a governed subprocess.
     * This is the model for unified JDesk app skinning.
     */
    private void openJDeskTerminal() {
        // Create terminal widget
        us.mearvk.jdesk.apps.JDeskTerminal terminal = new us.mearvk.jdesk.apps.JDeskTerminal(100, 28);

        // Wrap in a floating window on the desktop
        VBox termWindow = new VBox(0);
        termWindow.setPrefSize(terminal.getTerminalWidth() + 2, terminal.getTerminalHeight() + 40);
        termWindow.setLayoutX(120);
        termWindow.setLayoutY(80);
        termWindow.setStyle(
            "-fx-background-color: #1A1D23;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #3A3E48;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 6);"
        );

        // Title bar with circular close button
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(8, 12, 8, 12));
        titleBar.setStyle("-fx-background-color: #22252B; -fx-background-radius: 10 10 0 0;");

        // Circular close button (pearl style)
        Button closeBtn = new Button("");
        closeBtn.setMinSize(13, 13);
        closeBtn.setMaxSize(13, 13);
        closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: #FF5F56;" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #E04840;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnAction(e -> {
            terminal.stop();
            desktopSurface.getChildren().remove(termWindow);
        });

        Label titleLabel = new Label("Terminal");
        titleLabel.setStyle(
            "-fx-text-fill: #A0A8B0;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 500;"
        );

        titleBar.getChildren().addAll(closeBtn, titleLabel);

        // Enable dragging the window
        final double[] dragOffset = new double[2];
        titleBar.setOnMousePressed(e -> {
            dragOffset[0] = e.getSceneX() - termWindow.getLayoutX();
            dragOffset[1] = e.getSceneY() - termWindow.getLayoutY();
        });
        titleBar.setOnMouseDragged(e -> {
            termWindow.setLayoutX(e.getSceneX() - dragOffset[0]);
            termWindow.setLayoutY(e.getSceneY() - dragOffset[1]);
        });

        termWindow.getChildren().addAll(titleBar, terminal);
        desktopSurface.getChildren().add(termWindow);

        // Enable edge/corner resize
        enableWindowResize(termWindow, 400, 200);

        // Set terminal close callback to close the window when shell exits
        terminal.setOnTerminalClose(() -> {
            terminal.stop();
            desktopSurface.getChildren().remove(termWindow);
        });

        // Start the shell
        terminal.start();

        // Focus the terminal for keyboard input
        Platform.runLater(terminal::requestFocus);

        System.out.println("[JDesk] ✓ Terminal opened (JavaFX GUI → /bin/bash native)");

        // Write guaranteed startup sentinels for diagnostics so external runs can detect
        try {
            String ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            Path startupLog = Paths.get("/tmp/jdesk-startup.log");
            Files.writeString(startupLog, ts + " TERMINAL_OPENED - JDeskApplication\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            Path mouseLog = Paths.get("/tmp/jdesk-mouse.log");
            Files.writeString(mouseLog, ts + " JDeskApplication: terminal opened (sentinel)\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ioe) {
            System.err.println("[JDesk] Failed to write startup sentinel: " + ioe.getMessage());
        }
    }

    // =========================================================================
    //  JDesk IDE (JavaFX GUI, IntelliJ backend)
    // =========================================================================

    /**
     * Open a JDesk IDE window — JavaFX renders the full IDE chrome
     * (project tree, tabs, editor, build/run, terminal).
     * IntelliJ IDEA runs as a governed subprocess for code intelligence.
     */
    private void openJDeskIDE() {
        // Create IDE widget
        us.mearvk.jdesk.apps.JDeskIDE ide = new us.mearvk.jdesk.apps.JDeskIDE();

        // Wrap in a floating window on the desktop
        VBox ideWindow = new VBox(0);
        ideWindow.setPrefSize(ide.getIDEWidth() + 2, ide.getIDEHeight() + 40);
        ideWindow.setLayoutX(60);
        ideWindow.setLayoutY(30);
        ideWindow.setStyle(
            "-fx-background-color: #1E1F22;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #393B3D;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0, 0, 8);"
        );

        // Title bar with traffic light buttons
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(8, 12, 8, 12));
        titleBar.setStyle("-fx-background-color: #1E1F22; -fx-background-radius: 10 10 0 0;");

        // Close button (red circle)
        Button closeBtn = new Button("");
        closeBtn.setMinSize(13, 13);
        closeBtn.setMaxSize(13, 13);
        closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: #FF5F56;" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #E04840;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnAction(e -> {
            ide.stopIntelliJ();
            desktopSurface.getChildren().remove(ideWindow);
        });

        // Minimize button (yellow circle)
        Button minBtn = new Button("");
        minBtn.setMinSize(13, 13);
        minBtn.setMaxSize(13, 13);
        minBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        minBtn.setOnMouseEntered(e -> minBtn.setStyle(
            "-fx-background-color: #FFBD2E;" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #E0A820;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        minBtn.setOnMouseExited(e -> minBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #3A3E48 0%, #2A2E38 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        minBtn.setOnAction(e -> ideWindow.setVisible(false));

        Label titleLabel = new Label("JDesk IDE — IntelliJ IDEA");
        titleLabel.setStyle(
            "-fx-text-fill: #A0A8B0;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 500;"
        );

        titleBar.getChildren().addAll(closeBtn, minBtn, titleLabel);

        // Enable dragging the window
        final double[] dragOffset = new double[2];
        titleBar.setOnMousePressed(e -> {
            dragOffset[0] = e.getSceneX() - ideWindow.getLayoutX();
            dragOffset[1] = e.getSceneY() - ideWindow.getLayoutY();
        });
        titleBar.setOnMouseDragged(e -> {
            ideWindow.setLayoutX(e.getSceneX() - dragOffset[0]);
            ideWindow.setLayoutY(e.getSceneY() - dragOffset[1]);
        });

        // Assemble window
        VBox.setVgrow(ide, Priority.ALWAYS);
        ideWindow.getChildren().addAll(titleBar, ide);
        desktopSurface.getChildren().add(ideWindow);

        // Enable edge/corner resize
        enableWindowResize(ideWindow, 640, 400);

        // Try to open the user's home IdeaProjects as default
        String home = System.getProperty("user.home");
        if (home != null) {
            Path projects = Path.of(home, "IdeaProjects");
            if (Files.isDirectory(projects)) {
                try (java.nio.file.DirectoryStream<Path> stream =
                        Files.newDirectoryStream(projects)) {
                    for (Path p : stream) {
                        if (Files.isDirectory(p)) {
                            ide.openProject(p);
                            break;
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        // Start IntelliJ backend (falls back to built-in if not installed)
        ide.startIntelliJ(null);

        // Focus
        Platform.runLater(ide::requestFocus);

        System.out.println("[JDesk] ✓ IDE opened (JavaFX GUI → IntelliJ IDEA native)");
    }

    // =========================================================================
    //  JDesk Browser (JavaFX WebView, tabbed)
    // =========================================================================

    private void openJDeskBrowser() {
        us.mearvk.jdesk.apps.JDeskBrowser browser = new us.mearvk.jdesk.apps.JDeskBrowser();

        VBox browserWindow = new VBox(0);
        browserWindow.setPrefSize(browser.getBrowserWidth() + 2, browser.getBrowserHeight() + 40);
        browserWindow.setLayoutX(80);
        browserWindow.setLayoutY(40);
        browserWindow.setStyle(
            "-fx-background-color: #1E1F22;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #393B3D;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 6);"
        );

        HBox titleBar = createDarkTitleBar("Browser", browserWindow, () -> {
            desktopSurface.getChildren().remove(browserWindow);
        });

        VBox.setVgrow(browser, Priority.ALWAYS);
        browserWindow.getChildren().addAll(titleBar, browser);
        desktopSurface.getChildren().add(browserWindow);
        enableWindowResize(browserWindow, 500, 300);

        Platform.runLater(browser::requestFocus);
        System.out.println("[JDesk] ✓ Browser opened (JavaFX WebView)");
    }

    // =========================================================================
    //  JDesk Writer (JavaFX HTMLEditor, word processor)
    // =========================================================================

    private void openJDeskWriter() {
        us.mearvk.jdesk.apps.JDeskWriter writer = new us.mearvk.jdesk.apps.JDeskWriter();

        VBox writerWindow = new VBox(0);
        writerWindow.setPrefSize(writer.getWriterWidth() + 2, writer.getWriterHeight() + 40);
        writerWindow.setLayoutX(100);
        writerWindow.setLayoutY(50);
        writerWindow.setStyle(
            "-fx-background-color: #F7F8FA;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #DADCE0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,50,80,0.15), 20, 0, 0, 6);"
        );

        HBox titleBar = createLightTitleBar("Writer", writerWindow, () -> {
            desktopSurface.getChildren().remove(writerWindow);
        });

        VBox.setVgrow(writer, Priority.ALWAYS);
        writerWindow.getChildren().addAll(titleBar, writer);
        desktopSurface.getChildren().add(writerWindow);
        enableWindowResize(writerWindow, 500, 350);

        Platform.runLater(writer::requestFocus);
        System.out.println("[JDesk] ✓ Writer opened (JavaFX HTMLEditor)");
    }

    // =========================================================================
    //  JDesk Files (JavaFX file manager)
    // =========================================================================

    private void openJDeskFiles() {
        us.mearvk.jdesk.apps.JDeskFiles files = new us.mearvk.jdesk.apps.JDeskFiles();

        VBox filesWindow = new VBox(0);
        filesWindow.setPrefSize(files.getFilesWidth() + 2, files.getFilesHeight() + 40);
        filesWindow.setLayoutX(120);
        filesWindow.setLayoutY(60);
        filesWindow.setStyle(
            "-fx-background-color: #FFFFFF;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #DADCE0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,50,80,0.15), 20, 0, 0, 6);"
        );

        HBox titleBar = createLightTitleBar("Files", filesWindow, () -> {
            desktopSurface.getChildren().remove(filesWindow);
        });

        VBox.setVgrow(files, Priority.ALWAYS);
        filesWindow.getChildren().addAll(titleBar, files);
        desktopSurface.getChildren().add(filesWindow);
        enableWindowResize(filesWindow, 450, 300);

        Platform.runLater(files::requestFocus);
        System.out.println("[JDesk] ✓ Files opened (JavaFX file manager)");
    }

    // =========================================================================
    //  JDesk Software (JavaFX package manager)
    // =========================================================================

    private void openJDeskSoftware() {
        us.mearvk.jdesk.apps.JDeskSoftware software = new us.mearvk.jdesk.apps.JDeskSoftware();

        VBox softwareWindow = new VBox(0);
        softwareWindow.setPrefSize(software.getSoftwareWidth() + 2, software.getSoftwareHeight() + 40);
        softwareWindow.setLayoutX(90);
        softwareWindow.setLayoutY(45);
        softwareWindow.setStyle(
            "-fx-background-color: #FFFFFF;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: #DADCE0;" +
            "-fx-border-radius: 10;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,50,80,0.15), 20, 0, 0, 6);"
        );

        HBox titleBar = createLightTitleBar("Software", softwareWindow, () -> {
            desktopSurface.getChildren().remove(softwareWindow);
        });

        VBox.setVgrow(software, Priority.ALWAYS);
        softwareWindow.getChildren().addAll(titleBar, software);
        desktopSurface.getChildren().add(softwareWindow);
        enableWindowResize(softwareWindow, 500, 350);

        Platform.runLater(software::requestFocus);
        System.out.println("[JDesk] ✓ Software Center opened (JavaFX → apt/dpkg)");
    }

    // =========================================================================
    //  JDesk Launcher (overlay search)
    // =========================================================================

    private void openJDeskLauncher() {
        us.mearvk.jdesk.apps.JDeskLauncher launcher = new us.mearvk.jdesk.apps.JDeskLauncher();

        // Launcher is a full-desktop overlay
        launcher.setPrefSize(desktopSurface.getWidth(), desktopSurface.getHeight());
        launcher.setLayoutX(0);
        launcher.setLayoutY(0);
        launcher.setOnClose(() -> desktopSurface.getChildren().remove(launcher));

        // Click outside results to close
        launcher.setOnMouseClicked(e -> {
            if (e.getTarget() == launcher) {
                desktopSurface.getChildren().remove(launcher);
            }
        });

        desktopSurface.getChildren().add(launcher);
        System.out.println("[JDesk] ✓ Launcher overlay opened");
    }

    // =========================================================================
    //  Window Title Bar Helpers (reusable dark/light title bars)
    // =========================================================================

    private HBox createDarkTitleBar(String title, Region window, Runnable onClose) {
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(8, 12, 8, 12));
        titleBar.setStyle("-fx-background-color: #1E1F22; -fx-background-radius: 10 10 0 0;");

        Button closeBtn = trafficLightBtn("#3A3E48", "#FF5F56", "#E04840");
        closeBtn.setOnAction(e -> onClose.run());

        Button minBtn = trafficLightBtn("#3A3E48", "#FFBD2E", "#E0A820");
        minBtn.setOnAction(e -> window.setVisible(false));

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-text-fill: #A0A8B0;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 500;"
        );

        titleBar.getChildren().addAll(closeBtn, minBtn, titleLabel);
        enableTitleBarDrag(titleBar, window);
        return titleBar;
    }

    private HBox createLightTitleBar(String title, Region window, Runnable onClose) {
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(8, 12, 8, 12));
        titleBar.setStyle("-fx-background-color: #F7F8FA; -fx-background-radius: 10 10 0 0;");

        Button closeBtn = trafficLightBtn("#E8E8EC", "#FF5F56", "#E04840");
        closeBtn.setOnAction(e -> onClose.run());

        Button minBtn = trafficLightBtn("#E8E8EC", "#FFBD2E", "#E0A820");
        minBtn.setOnAction(e -> window.setVisible(false));

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-text-fill: #5C6B7A;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 500;"
        );

        titleBar.getChildren().addAll(closeBtn, minBtn, titleLabel);
        enableTitleBarDrag(titleBar, window);
        return titleBar;
    }

    private Button trafficLightBtn(String restColor, String hoverColor, String borderColor) {
        Button btn = new Button("");
        btn.setMinSize(13, 13);
        btn.setMaxSize(13, 13);
        btn.setStyle(
            "-fx-background-color: " + restColor + ";" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + hoverColor + ";" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + restColor + ";" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #4A4E58;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        return btn;
    }

    private void enableTitleBarDrag(HBox titleBar, Region window) {
        final double[] dragOffset = new double[2];
        titleBar.setOnMousePressed(e -> {
            dragOffset[0] = e.getSceneX() - window.getLayoutX();
            dragOffset[1] = e.getSceneY() - window.getLayoutY();
        });
        titleBar.setOnMouseDragged(e -> {
            window.setLayoutX(e.getSceneX() - dragOffset[0]);
            window.setLayoutY(e.getSceneY() - dragOffset[1]);
        });
    }

    // =========================================================================
    //  Settings Dialog (placeholder)
    // =========================================================================

    private void showSettingsDialog() {
        // Create a simple floating settings window on the desktop
        VBox dialog = new VBox(12);
        dialog.setPrefSize(400, 300);
        dialog.setPadding(new Insets(24));
        dialog.setLayoutX(200);
        dialog.setLayoutY(100);
        dialog.setStyle(
            "-fx-background-color: #FFFFFF;" +
            "-fx-background-radius: 14;" +
            "-fx-border-color: #D8E0EA;" +
            "-fx-border-radius: 14;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(30,50,80,0.16), 24, 0, 0, 8);"
        );

        // Title bar with circular close button
        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Button closeBtn = new Button("");
        closeBtn.setMinSize(13, 13);
        closeBtn.setMaxSize(13, 13);
        closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #FFFFFF 0%, #F7F7F7 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #D0D8E4;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(
            "-fx-background-color: #FF5F56;" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #E04840;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #FFFFFF 0%, #F7F7F7 100%);" +
            "-fx-background-radius: 50%;" +
            "-fx-border-color: #D0D8E4;" +
            "-fx-border-radius: 50%;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        ));
        closeBtn.setOnAction(e -> desktopSurface.getChildren().remove(dialog));

        Label title = new Label("JDesk Settings");
        title.setStyle(
            "-fx-font-size: 15px;" +
            "-fx-font-family: 'Inter', system-ui, sans-serif;" +
            "-fx-font-weight: 600;" +
            "-fx-text-fill: #1B2433;"
        );

        titleBar.getChildren().addAll(closeBtn, title);

        // Content
        Label themeLabel = new Label("Theme: Cool White (default)");
        themeLabel.setStyle("-fx-text-fill: #5C6B7A; -fx-font-size: 13px; -fx-font-family: 'Inter';");

        Label versionLabel = new Label("Edition: Galactic Cherry Marvell 98");
        versionLabel.setStyle("-fx-text-fill: #5C6B7A; -fx-font-size: 13px; -fx-font-family: 'Inter';");

        Label kernelLabel = new Label("Kernel: Linux 5.15.204");
        kernelLabel.setStyle("-fx-text-fill: #5C6B7A; -fx-font-size: 13px; -fx-font-family: 'Inter';");

        Label compositorLabel = new Label("Compositor: JDesk WindowCompositor (JavaFX)");
        compositorLabel.setStyle("-fx-text-fill: #5C6B7A; -fx-font-size: 13px; -fx-font-family: 'Inter';");

        Separator sep = new Separator();

        Label ethicsLabel = new Label("◉ White Ethics Installer Grade — Active");
        ethicsLabel.setStyle("-fx-text-fill: #4A90D9; -fx-font-size: 12px; -fx-font-family: 'Inter'; -fx-font-weight: 500;");

        dialog.getChildren().addAll(titleBar, sep, themeLabel, versionLabel, kernelLabel, compositorLabel,
                new Separator(), ethicsLabel);

        // Enable resize on settings dialog
        enableWindowResize(dialog, 300, 200);

        desktopSurface.getChildren().add(dialog);
    }

    // =========================================================================
    //  Window Resize (shared helper for all floating JDesk windows)
    // =========================================================================

    /**
     * Enable edge/corner drag-to-resize on any Region placed on the desktop.
     * Detects mouse position within 6px of edges and allows dragging to resize.
     *
     * @param window The Region to make resizable
     * @param minW   Minimum width
     * @param minH   Minimum height
     */
    private void enableWindowResize(Region window, double minW, double minH) {
        final int EDGE = 6;
        final double[] resizeState = new double[6];
        // [0]=startX, [1]=startY, [2]=origX, [3]=origY, [4]=origW, [5]=origH
        final int[] dir = {0};
        // dir encoding: 1=N, 2=S, 4=E, 8=W (combined for corners)

        window.setOnMouseMoved(e -> {
            int d = getEdgeDirection(e.getX(), e.getY(), window.getWidth(), window.getHeight(), EDGE);
            switch (d) {
                case 1: case 2:     window.setCursor(Cursor.N_RESIZE); break;
                case 4: case 8:     window.setCursor(Cursor.E_RESIZE); break;
                case 5: case 10:    window.setCursor(Cursor.NE_RESIZE); break;  // N+E=5, S+W=10
                case 9: case 6:     window.setCursor(Cursor.NW_RESIZE); break;  // N+W=9, S+E=6
                default:            window.setCursor(Cursor.DEFAULT); break;
            }
        });

        window.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            int d = getEdgeDirection(e.getX(), e.getY(), window.getWidth(), window.getHeight(), EDGE);
            if (d != 0) {
                dir[0] = d;
                resizeState[0] = e.getScreenX();
                resizeState[1] = e.getScreenY();
                resizeState[2] = window.getLayoutX();
                resizeState[3] = window.getLayoutY();
                resizeState[4] = window.getWidth();
                resizeState[5] = window.getHeight();
                e.consume();
            }
        });

        window.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (dir[0] == 0) return;

            double dx = e.getScreenX() - resizeState[0];
            double dy = e.getScreenY() - resizeState[1];
            double newX = resizeState[2];
            double newY = resizeState[3];
            double newW = resizeState[4];
            double newH = resizeState[5];

            // East (grow right)
            if ((dir[0] & 4) != 0) {
                newW = resizeState[4] + dx;
            }
            // West (grow left, move origin)
            if ((dir[0] & 8) != 0) {
                newW = resizeState[4] - dx;
                newX = resizeState[2] + dx;
            }
            // South (grow down)
            if ((dir[0] & 2) != 0) {
                newH = resizeState[5] + dy;
            }
            // North (grow up, move origin)
            if ((dir[0] & 1) != 0) {
                newH = resizeState[5] - dy;
                newY = resizeState[3] + dy;
            }

            // Enforce minimums
            if (newW < minW) {
                if (newX != resizeState[2]) newX = resizeState[2] + resizeState[4] - minW;
                newW = minW;
            }
            if (newH < minH) {
                if (newY != resizeState[3]) newY = resizeState[3] + resizeState[5] - minH;
                newH = minH;
            }

            window.setLayoutX(newX);
            window.setLayoutY(newY);
            window.setPrefWidth(newW);
            window.setPrefHeight(newH);
            e.consume();
        });

        window.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (dir[0] != 0) {
                dir[0] = 0;
                e.consume();
            }
        });
    }

    /**
     * Determine which edge/corner of a window the mouse is near.
     * Returns a bitmask: 1=N, 2=S, 4=E, 8=W (combined for corners).
     * Returns 0 if not near any edge.
     */
    private int getEdgeDirection(double x, double y, double w, double h, int edge) {
        boolean top    = y < edge;
        boolean bottom = y > h - edge;
        boolean left   = x < edge;
        boolean right  = x > w - edge;

        int d = 0;
        if (top) d |= 1;
        if (bottom) d |= 2;
        if (right) d |= 4;
        if (left) d |= 8;
        return d;
    }

    // =========================================================================
    //  Main
    // =========================================================================

    public static void main(String[] args) {
        launch(args);
    }
}
