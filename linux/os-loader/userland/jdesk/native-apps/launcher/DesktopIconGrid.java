/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Desktop Startup — Places native application icons on the desktop
 * and registers them with the panel at JDesk startup.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.desktop;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.layout.*;
import javafx.scene.image.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.geometry.*;

import us.mearvk.jdesk.launcher.NativeAppLauncher;
import us.mearvk.jdesk.launcher.NativeAppLauncher.AppManifest;
import us.mearvk.jdesk.launcher.NativeAppLauncher.BinaryFormat;
import us.mearvk.jdesk.launcher.LibraryLinker;
import us.mearvk.jdesk.theme.WhiteTheme;

/**
 * DesktopIconGrid — Manages desktop icon placement and interaction.
 *
 * At startup:
 *   1. Loads all .jdesk-app manifests from /opt/jdesk/manifests/
 *   2. Filters for desktop=true applications
 *   3. Places icons in a grid on the desktop surface
 *   4. Registers double-click → launch via NativeAppLauncher
 *
 * Align-to-Grid:
 *   The entire desktop surface is treated as a grid of cells. Icons can
 *   be freely dragged and will snap to the nearest unoccupied grid cell
 *   on drop. This works for any number of icons across the full desktop
 *   resolution, not just the initially populated rows.
 *
 * Default startup icons (in grid order):
 *   [Writer]   [IDE]      [Browser]   [Terminal]
 *   [Files]    [Settings] [Software]  [Launcher]
 */
public class DesktopIconGrid extends Pane {

    // Grid configuration
    private static final int ICON_SIZE = 64;
    private static final int CELL_WIDTH = 96;
    private static final int CELL_HEIGHT = 96;
    private static final int GRID_PADDING = 24;
    private static final int LABEL_MAX_WIDTH = 80;

    // Computed grid dimensions (based on desktop size)
    private int gridColumns;
    private int gridRows;

    // Cell occupancy map: grid position → icon cell node
    private final Map<GridPosition, VBox> occupiedCells = new LinkedHashMap<>();

    // Reverse lookup: icon cell → grid position
    private final Map<VBox, GridPosition> cellPositions = new LinkedHashMap<>();

    // Running processes (tracked for panel status)
    private final Map<String, Process> runningApps = new LinkedHashMap<>();

    // Panel reference for status updates
    private Pane panel;

    // Snap-to-grid enabled flag
    private boolean alignToGrid = true;

    /**
     * Simple immutable grid coordinate.
     */
    public record GridPosition(int col, int row) {}

    public DesktopIconGrid() {
        setPadding(new Insets(GRID_PADDING));

        // Recompute grid dimensions when desktop resizes
        widthProperty().addListener((obs, oldW, newW) -> recomputeGrid());
        heightProperty().addListener((obs, oldH, newH) -> recomputeGrid());
    }

    /**
     * Recompute how many columns/rows fit the current desktop size.
     * Called on desktop resize.
     */
    private void recomputeGrid() {
        double usableWidth = getWidth() - (2 * GRID_PADDING);
        double usableHeight = getHeight() - (2 * GRID_PADDING);

        gridColumns = Math.max(1, (int) (usableWidth / CELL_WIDTH));
        gridRows = Math.max(1, (int) (usableHeight / CELL_HEIGHT));
    }

    /**
     * Get the number of grid columns for the current desktop size.
     */
    public int getGridColumns() {
        return gridColumns;
    }

    /**
     * Get the number of grid rows for the current desktop size.
     */
    public int getGridRows() {
        return gridRows;
    }

    /**
     * Enable or disable snap-to-grid alignment.
     */
    public void setAlignToGrid(boolean enabled) {
        this.alignToGrid = enabled;
    }

    /**
     * Query whether align-to-grid is active.
     */
    public boolean isAlignToGrid() {
        return alignToGrid;
    }

    /**
     * Load and place all desktop application icons.
     * Called once at JDesk startup.
     */
    public void loadDesktopApps() {
        try {
            List<AppManifest> apps = NativeAppLauncher.getDesktopApps();

            int col = 0;
            int row = 0;

            for (AppManifest app : apps) {
                VBox iconCell = createIconCell(app);
                placeIconAt(iconCell, col, row);

                col++;
                if (col >= Math.max(gridColumns, 6)) {
                    col = 0;
                    row++;
                }
            }

            System.out.printf("[JDesk] Desktop: %d application icons placed (%dx%d grid)%n",
                    apps.size(), gridColumns, gridRows);

        } catch (IOException e) {
            System.err.printf("[JDesk] Error loading desktop apps: %s%n", e.getMessage());
        }
    }

    /**
     * Place an icon cell at a specific grid position.
     * Computes pixel coordinates from the grid cell and updates the occupancy map.
     */
    private void placeIconAt(VBox iconCell, int col, int row) {
        GridPosition pos = new GridPosition(col, row);

        // Remove from old position if re-placing
        GridPosition oldPos = cellPositions.get(iconCell);
        if (oldPos != null) {
            occupiedCells.remove(oldPos);
        }

        // Compute pixel position from grid coordinates
        double x = GRID_PADDING + (col * CELL_WIDTH);
        double y = GRID_PADDING + (row * CELL_HEIGHT);

        iconCell.setLayoutX(x);
        iconCell.setLayoutY(y);

        // Register occupancy
        occupiedCells.put(pos, iconCell);
        cellPositions.put(iconCell, pos);

        // Add to scene if not already present
        if (!getChildren().contains(iconCell)) {
            getChildren().add(iconCell);
        }
    }

    /**
     * Snap a pixel coordinate to the nearest grid cell.
     * Returns the grid position (col, row).
     */
    public GridPosition snapToGrid(double pixelX, double pixelY) {
        int col = (int) Math.round((pixelX - GRID_PADDING) / (double) CELL_WIDTH);
        int row = (int) Math.round((pixelY - GRID_PADDING) / (double) CELL_HEIGHT);

        // Clamp to valid range
        col = Math.max(0, Math.min(col, gridColumns - 1));
        row = Math.max(0, Math.min(row, gridRows - 1));

        return new GridPosition(col, row);
    }

    /**
     * Find the nearest unoccupied grid cell to the given position.
     * Uses spiral search outward from the target cell.
     */
    public GridPosition findNearestFreeCell(GridPosition target) {
        if (!occupiedCells.containsKey(target)) {
            return target;
        }

        // Spiral outward from target to find free cell
        for (int radius = 1; radius < Math.max(gridColumns, gridRows); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.abs(dx) != radius && Math.abs(dy) != radius) continue; // perimeter only
                    int c = target.col() + dx;
                    int r = target.row() + dy;
                    if (c >= 0 && c < gridColumns && r >= 0 && r < gridRows) {
                        GridPosition candidate = new GridPosition(c, r);
                        if (!occupiedCells.containsKey(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        // Fallback: return target anyway (overlap)
        return target;
    }

    /**
     * Realign all icons to the grid. Useful after desktop resize
     * or when toggling align-to-grid on.
     */
    public void realignAll() {
        // Collect current icons in order
        List<VBox> icons = new ArrayList<>(cellPositions.keySet());
        occupiedCells.clear();
        cellPositions.clear();

        int col = 0;
        int row = 0;
        for (VBox icon : icons) {
            placeIconAt(icon, col, row);
            col++;
            if (col >= gridColumns) {
                col = 0;
                row++;
            }
        }

        System.out.printf("[JDesk] Desktop icons realigned to %dx%d grid%n", gridColumns, gridRows);
    }

    /**
     * Create a single desktop icon cell (icon + label).
     * Supports drag-and-drop with snap-to-grid alignment.
     */
    private VBox createIconCell(AppManifest app) {
        VBox cell = new VBox(4);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(CELL_WIDTH, CELL_HEIGHT);

        // Load SVG icon
        ImageView icon = loadIcon(app.iconPath, ICON_SIZE);

        // Application name label
        Label label = new Label(app.name);
        label.setMaxWidth(LABEL_MAX_WIDTH);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-text-fill: #202124; -fx-font-size: 11px; -fx-font-family: 'Inter';");

        cell.getChildren().addAll(icon, label);

        // Hover effect
        cell.setOnMouseEntered(e -> cell.setStyle("-fx-background-color: #F1F3F4; -fx-background-radius: 8;"));
        cell.setOnMouseExited(e -> cell.setStyle(""));

        // --- Drag-and-drop for align-to-grid ---
        enableDragToReposition(cell);

        // Double-click to launch
        cell.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                launchApp(app);
            }
        });

        return cell;
    }

    /**
     * Enable drag-to-reposition on a desktop icon cell.
     * On drag release, the icon snaps to the nearest free grid cell.
     */
    private void enableDragToReposition(VBox cell) {
        final double[] dragOffset = new double[2];
        final double[] dragStart = new double[2];

        cell.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                dragOffset[0] = event.getSceneX() - cell.getLayoutX();
                dragOffset[1] = event.getSceneY() - cell.getLayoutY();
                dragStart[0] = cell.getLayoutX();
                dragStart[1] = cell.getLayoutY();
                cell.toFront();
            }
        });

        cell.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                double newX = event.getSceneX() - dragOffset[0];
                double newY = event.getSceneY() - dragOffset[1];
                cell.setLayoutX(newX);
                cell.setLayoutY(newY);
            }
        });

        cell.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                double dropX = cell.getLayoutX();
                double dropY = cell.getLayoutY();

                // Only snap if drag distance exceeds threshold (avoid snapping on click)
                double dist = Math.hypot(dropX - dragStart[0], dropY - dragStart[1]);
                if (dist < 5) return; // too small to be a drag

                if (alignToGrid) {
                    // Snap to nearest free grid cell
                    GridPosition target = snapToGrid(dropX, dropY);
                    GridPosition freeCell = findNearestFreeCell(target);
                    placeIconAt(cell, freeCell.col(), freeCell.row());
                }
                // If align-to-grid is off, icon stays at free-form position
            }
        });
    }

    /**
     * Launch an application via the NativeAppLauncher.
     * Automatically routes shared libraries (.so/.dll) to LibraryLinker.
     */
    private void launchApp(AppManifest app) {
        try {
            // Check if already running
            if (runningApps.containsKey(app.name)) {
                Process existing = runningApps.get(app.name);
                if (existing.isAlive()) {
                    System.out.printf("[JDesk] %s is already running (PID active)%n", app.name);
                    // TODO: bring window to front via NativeBridge
                    return;
                }
                runningApps.remove(app.name);
            }

            // Detect binary format and determine if it's a library
            Path binaryPath = Path.of(app.binaryPath);
            Process proc;

            if (Files.exists(binaryPath)) {
                if (NativeAppLauncher.isSharedLibrary(binaryPath)) {
                    // Shared library (.so / .dll / .dylib) — use LibraryLinker
                    System.out.printf("[JDesk] Launching %s via LibraryLinker (shared library)%n", app.name);
                    proc = NativeAppLauncher.launchLibrary(app);
                } else {
                    // Standard executable — use direct launcher
                    BinaryFormat fmt = NativeAppLauncher.detectFormat(binaryPath);
                    System.out.printf("[JDesk] Launching %s (%s)%n", app.name, fmt.getDescription());
                    proc = NativeAppLauncher.launch(app);
                }
            } else {
                // Binary not found — attempt launch anyway (might be in PATH)
                proc = NativeAppLauncher.launch(app);
            }

            if (proc != null) {
                runningApps.put(app.name, proc);

                // Monitor process in background thread
                Thread monitor = new Thread(() -> {
                    try {
                        int exitCode = proc.waitFor();
                        runningApps.remove(app.name);
                        System.out.printf("[JDesk] %s exited (code %d)%n", app.name, exitCode);
                    } catch (InterruptedException ignored) {}
                }, "jdesk-monitor-" + app.name);
                monitor.setDaemon(true);
                monitor.start();
            }

        } catch (IOException e) {
            System.err.printf("[JDesk] Failed to launch %s: %s%n", app.name, e.getMessage());
            showErrorDialog(app.name, e.getMessage());
        }
    }

    /**
     * Load an SVG icon at the specified size.
     */
    private ImageView loadIcon(String iconPath, int size) {
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setSmooth(true);

        if (iconPath != null && Files.exists(Path.of(iconPath))) {
            try {
                Image img = new Image(new FileInputStream(iconPath), size, size, true, true);
                iv.setImage(img);
            } catch (FileNotFoundException e) {
                setPlaceholderIcon(iv, size);
            }
        } else {
            setPlaceholderIcon(iv, size);
        }

        return iv;
    }

    /**
     * Fallback: generate a colored placeholder icon.
     */
    private void setPlaceholderIcon(ImageView iv, int size) {
        // Simple colored square as placeholder
        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(size, size);
        rect.setFill(javafx.scene.paint.Color.web("#1A73E8"));
        rect.setArcWidth(12);
        rect.setArcHeight(12);
        // Convert to image via snapshot would go here in full implementation
    }

    /**
     * Show error dialog when launch fails.
     */
    private void showErrorDialog(String appName, String errorMessage) {
        // In full JDesk, this would show a themed dialog.
        // For now, stdout logging.
        System.err.printf("""
            ═══════════════════════════════════════════════
              JDesk Launch Error
            ═══════════════════════════════════════════════
              Application: %s
              Error: %s
            
              Check:
                • Binary exists at declared path
                • Binary has +x permission
                • Memory Proxy profile is valid
                • Wine/Darling installed (for PE/Mach-O)
            ═══════════════════════════════════════════════
            %n""", appName, errorMessage);
    }

    /**
     * Get list of currently running applications (for panel display).
     */
    public List<String> getRunningApps() {
        List<String> running = new ArrayList<>();
        for (Map.Entry<String, Process> entry : runningApps.entrySet()) {
            if (entry.getValue().isAlive()) {
                running.add(entry.getKey());
            }
        }
        return running;
    }

    /**
     * Kill a running application by name.
     */
    public boolean killApp(String appName) {
        Process proc = runningApps.get(appName);
        if (proc != null && proc.isAlive()) {
            proc.destroyForcibly();
            runningApps.remove(appName);
            return true;
        }
        return false;
    }

    /**
     * Shutdown all running applications (called on JDesk exit).
     */
    public void shutdownAll() {
        for (Map.Entry<String, Process> entry : runningApps.entrySet()) {
            if (entry.getValue().isAlive()) {
                System.out.printf("[JDesk] Shutting down: %s%n", entry.getKey());
                entry.getValue().destroy();
            }
        }
        runningApps.clear();
    }
}
