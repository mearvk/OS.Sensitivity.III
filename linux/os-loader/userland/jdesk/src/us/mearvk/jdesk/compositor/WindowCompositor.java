/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Window Compositor — JavaFX GUI frame for ALL native programs.
 *
 * Provides a unified themed window frame (title bar, borders, shadow, controls)
 * rendered in JavaFX, with native application content embedded inside via
 * X11 window reparenting (XReparentWindow).
 *
 * Architecture:
 *   - JDesk runs as a full-screen JavaFX application (the desktop)
 *   - When a native app creates a top-level window, JDesk intercepts MapRequest
 *   - JDesk creates a JavaFX "frame" (title bar + border) around the native window
 *   - The native window is reparented into a SwingNode/X11 embed area
 *   - The app renders normally inside; JDesk owns the chrome
 *
 * This means ALL programs — GTK, Qt, EFL, Wine, terminal, whatever — get the
 * same WhiteTheme title bar, the same window controls, the same shadow, the
 * same animations. One theme for the desktop.
 *
 * Done once at program install? No — done at RUNTIME. The compositor does it
 * live for every window that appears. Install-time just registers the app
 * manifest (icon, profile, name). The theming is automatic and instant.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.compositor;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.geometry.*;
import javafx.stage.*;
import javafx.animation.*;
import javafx.util.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import us.mearvk.jdesk.theme.WhiteTheme;

/**
 * WindowCompositor — The heart of JDesk's unified GUI treatment.
 *
 * Manages all windows on the desktop:
 *   1. Intercepts new top-level windows (via X11 SubstructureRedirect)
 *   2. Creates a JavaFX frame around each native window
 *   3. Handles window move, resize, minimize, maximize, close
 *   4. Provides window animations (open, close, minimize)
 *   5. Manages z-order and focus
 *   6. Renders drop shadows and rounded corners
 *
 * The compositor does NOT modify native applications. It wraps them.
 *
 * Flow:
 *   Native App creates X11 Window
 *       │
 *       ▼  (SubstructureRedirect)
 *   WindowCompositor intercepts MapRequest
 *       │
 *       ▼
 *   Creates JDeskFrame (JavaFX VBox):
 *       ┌─────────────────────────────────────────┐
 *       │  TitleBar (HBox):  Icon  Title  [_][□][×]│ ← JavaFX rendered
 *       ├─────────────────────────────────────────┤
 *       │                                         │
 *       │  EmbedArea: XReparentWindow(native, ←)  │ ← Native renders here
 *       │                                         │
 *       └─────────────────────────────────────────┘
 *       │  DropShadow effect on outer container   │ ← JavaFX effect
 *       └─────────────────────────────────────────┘
 *
 *   Native window is reparented INTO the embed area via JNI → X11.
 */
public class WindowCompositor {

    // =========================================================================
    //  State
    // =========================================================================

    private final WhiteTheme theme = WhiteTheme.getInstance();
    private final Map<Long, JDeskFrame> managedWindows = new ConcurrentHashMap<>();
    private final Pane desktopSurface;  // The full-screen desktop pane
    private JDeskFrame focusedWindow = null;

    // Window stacking order (front = end of list)
    private final List<JDeskFrame> zOrder = new ArrayList<>();

    // Snap zones
    private static final int SNAP_MARGIN = 12;
    private static final int TITLE_BAR_HEIGHT = 36;
    private static final int BORDER_WIDTH = 1;
    private static final int SHADOW_RADIUS = 16;
    private static final int MIN_WINDOW_WIDTH = 200;
    private static final int MIN_WINDOW_HEIGHT = 100;

    // =========================================================================
    //  Constructor
    // =========================================================================

    /**
     * Create the compositor attached to the desktop surface.
     * @param desktopSurface The main desktop Pane (full-screen JavaFX layout)
     */
    public WindowCompositor(Pane desktopSurface) {
        this.desktopSurface = desktopSurface;
    }

    // =========================================================================
    //  Window Management API (called by JNI native bridge on X11 events)
    // =========================================================================

    /**
     * Called when a new top-level window requests to be mapped.
     * Creates a JDesk frame around the native window.
     *
     * @param xWindowId  The X11 Window ID of the native window
     * @param title      Window title (from _NET_WM_NAME or WM_NAME)
     * @param appName    Application name (from manifest or WM_CLASS)
     * @param iconPath   Path to app icon (from manifest or _NET_WM_ICON)
     * @param width      Requested window width
     * @param height     Requested window height
     * @param x          Requested x position (or -1 for auto)
     * @param y          Requested y position (or -1 for auto)
     */
    public void onWindowMapRequest(long xWindowId, String title, String appName,
                                   String iconPath, int width, int height, int x, int y) {
        Platform.runLater(() -> {
            JDeskFrame frame = new JDeskFrame(xWindowId, title, appName, iconPath, width, height);

            // Position: use requested or auto-cascade
            if (x >= 0 && y >= 0) {
                frame.setLayoutX(x);
                frame.setLayoutY(y);
            } else {
                autoCascade(frame);
            }

            managedWindows.put(xWindowId, frame);
            zOrder.add(frame);
            desktopSurface.getChildren().add(frame);

            // Open animation
            frame.setScaleX(0.95);
            frame.setScaleY(0.95);
            frame.setOpacity(0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(150), frame);
            scale.setToX(1.0);
            scale.setToY(1.0);
            FadeTransition fade = new FadeTransition(Duration.millis(150), frame);
            fade.setToValue(1.0);
            scale.play();
            fade.play();

            // Focus the new window
            setFocus(frame);

            // Reparent the native X11 window into our embed area
            // This call goes through JNI to xReparentWindow()
            nativeReparent(xWindowId, frame.getEmbedAreaNativeId(), width, height);

            System.out.printf("[JDesk:Compositor] Framed: '%s' (%s) [%dx%d] xid=%d%n",
                    title, appName, width, height, xWindowId);
        });
    }

    /**
     * Called when a managed window's title changes.
     */
    public void onTitleChanged(long xWindowId, String newTitle) {
        Platform.runLater(() -> {
            JDeskFrame frame = managedWindows.get(xWindowId);
            if (frame != null) frame.setTitle(newTitle);
        });
    }

    /**
     * Called when the native window requests to be unmapped (closed).
     */
    public void onWindowUnmap(long xWindowId) {
        Platform.runLater(() -> {
            JDeskFrame frame = managedWindows.remove(xWindowId);
            if (frame != null) {
                zOrder.remove(frame);

                // Close animation
                ScaleTransition scale = new ScaleTransition(Duration.millis(120), frame);
                scale.setToX(0.95);
                scale.setToY(0.95);
                FadeTransition fade = new FadeTransition(Duration.millis(120), frame);
                fade.setToValue(0);
                fade.setOnFinished(e -> desktopSurface.getChildren().remove(frame));
                scale.play();
                fade.play();

                // Update focus
                if (frame == focusedWindow) {
                    focusedWindow = null;
                    if (!zOrder.isEmpty()) {
                        setFocus(zOrder.get(zOrder.size() - 1));
                    }
                }
            }
        });
    }

    /**
     * Called when a managed window requests focus (e.g., user clicks on it).
     */
    public void onWindowFocusRequest(long xWindowId) {
        Platform.runLater(() -> {
            JDeskFrame frame = managedWindows.get(xWindowId);
            if (frame != null) setFocus(frame);
        });
    }

    // =========================================================================
    //  Focus Management
    // =========================================================================

    private void setFocus(JDeskFrame frame) {
        if (focusedWindow != null && focusedWindow != frame) {
            focusedWindow.setFrameFocused(false);
        }
        focusedWindow = frame;
        frame.setFrameFocused(true);

        // Raise to top of z-order
        zOrder.remove(frame);
        zOrder.add(frame);
        frame.toFront();

        // Tell X11 to send input focus to the embedded native window
        nativeSetInputFocus(frame.getXWindowId());
    }

    // =========================================================================
    //  Auto-Cascade Positioning
    // =========================================================================

    private int cascadeOffset = 0;

    private void autoCascade(JDeskFrame frame) {
        double x = 80 + (cascadeOffset * 28);
        double y = 60 + (cascadeOffset * 28);

        // Wrap around if off-screen
        if (x + frame.getFrameWidth() > desktopSurface.getWidth() - 100) {
            cascadeOffset = 0;
            x = 80;
            y = 60;
        }

        frame.setLayoutX(x);
        frame.setLayoutY(y);
        cascadeOffset++;
    }

    // =========================================================================
    //  Window List (for Alt+Tab, panel, etc.)
    // =========================================================================

    /**
     * Get all managed windows in focus order (most recent first).
     */
    public List<WindowInfo> getWindowList() {
        List<WindowInfo> list = new ArrayList<>();
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            JDeskFrame f = zOrder.get(i);
            list.add(new WindowInfo(f.getXWindowId(), f.getTitle(), f.getAppName(),
                    f.getIconPath(), f == focusedWindow));
        }
        return list;
    }

    public static class WindowInfo {
        public final long xWindowId;
        public final String title;
        public final String appName;
        public final String iconPath;
        public final boolean focused;

        WindowInfo(long xid, String title, String app, String icon, boolean focused) {
            this.xWindowId = xid;
            this.title = title;
            this.appName = app;
            this.iconPath = icon;
            this.focused = focused;
        }
    }

    // =========================================================================
    //  JDeskFrame — The JavaFX window frame that wraps native content
    // =========================================================================

    /**
     * A single framed window. Contains:
     *   - Title bar (icon + title text + window controls)
     *   - Embed area (where the native X11 window is reparented)
     *   - Border and drop shadow
     *   - Drag/resize handlers
     */
    public class JDeskFrame extends StackPane {

        private final long xWindowId;
        private String title;
        private final String appName;
        private final String iconPath;
        private int frameWidth;
        private int frameHeight;
        private boolean focused = false;

        // UI components
        private final Label titleLabel;
        private final HBox titleBar;
        private final Pane embedArea;
        private final VBox contentBox;

        // Drag state
        private double dragStartX, dragStartY;
        private double dragOffsetX, dragOffsetY;
        private boolean dragging = false;

        // Resize state
        private boolean resizing = false;
        private ResizeDirection resizeDir = ResizeDirection.NONE;

        enum ResizeDirection { NONE, N, S, E, W, NE, NW, SE, SW }

        public JDeskFrame(long xWindowId, String title, String appName,
                          String iconPath, int width, int height) {
            this.xWindowId = xWindowId;
            this.title = title;
            this.appName = appName;
            this.iconPath = iconPath;
            this.frameWidth = width;
            this.frameHeight = height + TITLE_BAR_HEIGHT;

            // === Title Bar ===
            titleBar = createTitleBar();

            // === Embed Area (native content goes here) ===
            embedArea = new Pane();
            embedArea.setPrefSize(width, height);
            embedArea.setMinSize(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT);
            embedArea.setStyle("-fx-background-color: #000000;"); // Black until native renders

            // === Content Layout ===
            contentBox = new VBox();
            contentBox.getChildren().addAll(titleBar, embedArea);
            VBox.setVgrow(embedArea, Priority.ALWAYS);

            // === Outer frame (border + shadow) ===
            contentBox.setStyle(
                "-fx-background-color: " + WhiteTheme.COLOR_SURFACE + ";" +
                "-fx-border-color: " + WhiteTheme.COLOR_BORDER + ";" +
                "-fx-border-width: " + BORDER_WIDTH + ";" +
                "-fx-border-radius: " + WhiteTheme.CORNER_RADIUS + ";" +
                "-fx-background-radius: " + WhiteTheme.CORNER_RADIUS + ";"
            );

            // Drop shadow
            DropShadow shadow = new DropShadow();
            shadow.setRadius(SHADOW_RADIUS);
            shadow.setOffsetY(4);
            shadow.setColor(Color.rgb(0, 0, 0, 0.15));
            contentBox.setEffect(shadow);

            // Clip to rounded corners
            Rectangle clip = new Rectangle(width + BORDER_WIDTH * 2,
                    frameHeight + BORDER_WIDTH * 2);
            clip.setArcWidth(WhiteTheme.CORNER_RADIUS * 2);
            clip.setArcHeight(WhiteTheme.CORNER_RADIUS * 2);
            contentBox.setClip(clip);

            getChildren().add(contentBox);
            setPrefSize(frameWidth + SHADOW_RADIUS * 2, frameHeight + SHADOW_RADIUS * 2);

            // === Interaction ===
            setupDragHandlers();
            setupResizeHandlers();

            // Click to focus
            setOnMousePressed(e -> {
                setFocus(this);
                e.consume();
            });

            // Title label reference for updates
            titleLabel = (Label) titleBar.lookup("#jdesk-title-label");
        }

        // === Title Bar Construction ===

        private HBox createTitleBar() {
            HBox bar = new HBox(8);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setPrefHeight(TITLE_BAR_HEIGHT);
            bar.setMaxHeight(TITLE_BAR_HEIGHT);
            bar.setPadding(new Insets(0, 12, 0, 14));
            bar.getStyleClass().add("jdesk-titlebar");

            // === Circular window control buttons (left side, macOS-style) ===
            HBox controls = createCircularControls();

            // Spacer
            Region spacer1 = new Region();
            spacer1.setPrefWidth(10);

            // App icon (16x16)
            ImageView icon = new ImageView();
            icon.setFitWidth(16);
            icon.setFitHeight(16);
            if (iconPath != null && Files.exists(Path.of(iconPath))) {
                try {
                    icon.setImage(new Image(new FileInputStream(iconPath), 16, 16, true, true));
                } catch (FileNotFoundException ignored) {}
            }

            // Title text
            Label titleLbl = new Label(title);
            titleLbl.setId("jdesk-title-label");
            titleLbl.getStyleClass().add("jdesk-title");
            titleLbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(titleLbl, Priority.ALWAYS);

            bar.getChildren().addAll(controls, spacer1, icon, titleLbl);
            return bar;
        }

        /**
         * Create the signature circular white window control buttons.
         *
         * Three pearl-like circles:
         *   ● Close (red on hover)    — leftmost
         *   ● Minimize (amber on hover) — middle
         *   ● Maximize (green on hover) — rightmost
         *
         * At rest: pure white circles with subtle border and inner shadow.
         * On hover: fills with semantic color, symbol (×, ─, □) appears.
         */
        private HBox createCircularControls() {
            HBox box = new HBox(7);
            box.setAlignment(Pos.CENTER);
            box.getStyleClass().add("jdesk-controls-box");

            Button btnClose = createCircleButton("×", "jdesk-close");
            Button btnMin   = createCircleButton("─", "jdesk-minimize");
            Button btnMax   = createCircleButton("□", "jdesk-maximize");

            btnClose.setOnAction(e -> requestClose());
            btnMin.setOnAction(e -> minimize());
            btnMax.setOnAction(e -> toggleMaximize());

            box.getChildren().addAll(btnClose, btnMin, btnMax);

            // Show symbols only when hovering the control area
            box.setOnMouseEntered(e -> {
                btnClose.setText("×");
                btnMin.setText("─");
                btnMax.setText("□");
            });
            box.setOnMouseExited(e -> {
                btnClose.setText("");
                btnMin.setText("");
                btnMax.setText("");
            });

            return box;
        }

        /**
         * Create a single circular window control button.
         * 13px diameter, round, white with border — pearl aesthetic.
         */
        private Button createCircleButton(String symbol, String styleClass) {
            Button btn = new Button("");  // Empty at rest — symbol on hover only
            btn.getStyleClass().addAll("jdesk-window-control", styleClass);
            btn.setMinSize(13, 13);
            btn.setMaxSize(13, 13);
            btn.setPrefSize(13, 13);

            // Inline fallback style (CSS file overrides this)
            btn.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #FFFFFF 0%, #F7F7F7 100%);" +
                "-fx-background-radius: 50%;" +
                "-fx-border-color: #D0D8E4;" +
                "-fx-border-radius: 50%;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 0;" +
                "-fx-font-size: 8px;" +
                "-fx-font-weight: 900;" +
                "-fx-text-fill: transparent;" +
                "-fx-cursor: hand;" +
                "-fx-effect: innershadow(gaussian, rgba(0,0,0,0.06), 3, 0, 0, 1);"
            );

            // Hover colors (inline fallback — CSS normally handles this)
            btn.setOnMouseEntered(e -> {
                String hoverColor;
                String textColor;
                switch (styleClass) {
                    case "jdesk-close":
                        hoverColor = "#FF5F56"; textColor = "rgba(80,0,0,0.75)"; break;
                    case "jdesk-minimize":
                        hoverColor = "#FFBD2E"; textColor = "rgba(80,50,0,0.75)"; break;
                    case "jdesk-maximize":
                        hoverColor = "#27C93F"; textColor = "rgba(0,60,0,0.75)"; break;
                    default:
                        hoverColor = "#E8EEF5"; textColor = "#1B2433"; break;
                }
                btn.setStyle(
                    "-fx-background-color: " + hoverColor + ";" +
                    "-fx-background-radius: 50%;" +
                    "-fx-border-color: derive(" + hoverColor + ", -15%);" +
                    "-fx-border-radius: 50%;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 0;" +
                    "-fx-font-size: 8px;" +
                    "-fx-font-weight: 900;" +
                    "-fx-text-fill: " + textColor + ";" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: innershadow(gaussian, rgba(0,0,0,0.12), 2, 0, 0, 0.5);"
                );
            });

            btn.setOnMouseExited(e -> {
                btn.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #FFFFFF 0%, #F7F7F7 100%);" +
                    "-fx-background-radius: 50%;" +
                    "-fx-border-color: #D0D8E4;" +
                    "-fx-border-radius: 50%;" +
                    "-fx-border-width: 1;" +
                    "-fx-padding: 0;" +
                    "-fx-font-size: 8px;" +
                    "-fx-font-weight: 900;" +
                    "-fx-text-fill: transparent;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: innershadow(gaussian, rgba(0,0,0,0.06), 3, 0, 0, 1);"
                );
            });

            return btn;
        }

        // === Window Actions ===

        private void minimize() {
            // Animate to taskbar
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), this);
            scale.setToX(0.1);
            scale.setToY(0.1);
            FadeTransition fade = new FadeTransition(Duration.millis(200), this);
            fade.setToValue(0);
            fade.setOnFinished(e -> setVisible(false));
            scale.play();
            fade.play();

            nativeUnmapWindow(xWindowId);
        }

        private boolean maximized = false;
        private double restoreX, restoreY, restoreW, restoreH;

        private void toggleMaximize() {
            if (!maximized) {
                // Save current bounds for restore
                restoreX = getLayoutX();
                restoreY = getLayoutY();
                restoreW = contentBox.getPrefWidth();
                restoreH = contentBox.getPrefHeight();

                // Maximize to desktop area (minus panel)
                setLayoutX(0);
                setLayoutY(0);
                double dw = desktopSurface.getWidth();
                double dh = desktopSurface.getHeight() - 48; // Subtract panel height
                contentBox.setPrefSize(dw, dh);
                embedArea.setPrefSize(dw, dh - TITLE_BAR_HEIGHT);

                nativeResizeWindow(xWindowId, (int) dw, (int) (dh - TITLE_BAR_HEIGHT));
                maximized = true;
            } else {
                // Restore
                setLayoutX(restoreX);
                setLayoutY(restoreY);
                contentBox.setPrefSize(restoreW, restoreH);
                embedArea.setPrefSize(restoreW, restoreH - TITLE_BAR_HEIGHT);

                nativeResizeWindow(xWindowId, (int) restoreW, (int) (restoreH - TITLE_BAR_HEIGHT));
                maximized = false;
            }
        }

        private void requestClose() {
            // Send WM_DELETE_WINDOW to the native app
            nativeSendClose(xWindowId);
        }

        // === Focus Appearance ===

        void setFrameFocused(boolean focused) {
            this.focused = focused;
            if (focused) {
                titleBar.setStyle(
                    "-fx-background-color: " + WhiteTheme.COLOR_SURFACE + ";" +
                    "-fx-border-color: transparent transparent " + WhiteTheme.COLOR_PRIMARY + " transparent;" +
                    "-fx-border-width: 0 0 2 0;"
                );
                ((DropShadow) contentBox.getEffect()).setRadius(SHADOW_RADIUS);
                ((DropShadow) contentBox.getEffect()).setColor(Color.rgb(26, 115, 232, 0.2));
            } else {
                titleBar.setStyle(
                    "-fx-background-color: " + WhiteTheme.COLOR_SURFACE + ";" +
                    "-fx-border-color: transparent transparent " + WhiteTheme.COLOR_BORDER + " transparent;" +
                    "-fx-border-width: 0 0 1 0;"
                );
                ((DropShadow) contentBox.getEffect()).setRadius(8);
                ((DropShadow) contentBox.getEffect()).setColor(Color.rgb(0, 0, 0, 0.1));
            }
        }

        // === Drag (Title Bar) ===

        private void setupDragHandlers() {
            titleBar.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    dragStartX = e.getScreenX();
                    dragStartY = e.getScreenY();
                    dragOffsetX = getLayoutX();
                    dragOffsetY = getLayoutY();
                    dragging = true;
                    e.consume();
                }
            });

            titleBar.setOnMouseDragged(e -> {
                if (dragging) {
                    double newX = dragOffsetX + (e.getScreenX() - dragStartX);
                    double newY = dragOffsetY + (e.getScreenY() - dragStartY);
                    setLayoutX(newX);
                    setLayoutY(Math.max(0, newY)); // Don't drag above desktop
                    e.consume();
                }
            });

            titleBar.setOnMouseReleased(e -> {
                dragging = false;
                // Edge snapping
                if (getLayoutX() < SNAP_MARGIN) setLayoutX(0);
                if (getLayoutY() < SNAP_MARGIN) setLayoutY(0);
            });

            // Double-click title bar = maximize/restore
            titleBar.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    toggleMaximize();
                }
            });
        }

        // === Resize (edges/corners) ===

        private void setupResizeHandlers() {
            setOnMouseMoved(e -> {
                ResizeDirection dir = getResizeDirection(e.getX(), e.getY());
                switch (dir) {
                    case N: case S: setCursor(Cursor.N_RESIZE); break;
                    case E: case W: setCursor(Cursor.E_RESIZE); break;
                    case NE: case SW: setCursor(Cursor.NE_RESIZE); break;
                    case NW: case SE: setCursor(Cursor.NW_RESIZE); break;
                    default: setCursor(Cursor.DEFAULT); break;
                }
            });

            setOnMousePressed(e -> {
                ResizeDirection dir = getResizeDirection(e.getX(), e.getY());
                if (dir != ResizeDirection.NONE) {
                    resizing = true;
                    resizeDir = dir;
                    dragStartX = e.getScreenX();
                    dragStartY = e.getScreenY();
                    dragOffsetX = getLayoutX();
                    dragOffsetY = getLayoutY();
                    frameWidth = (int) getPrefWidth();
                    frameHeight = (int) getPrefHeight();
                    e.consume();
                }
            });

            setOnMouseDragged(e -> {
                if (!resizing) return;

                double dx = e.getScreenX() - dragStartX;
                double dy = e.getScreenY() - dragStartY;
                double newX = dragOffsetX;
                double newY = dragOffsetY;
                double newW = frameWidth;
                double newH = frameHeight;

                switch (resizeDir) {
                    case E:
                        newW = frameWidth + dx;
                        break;
                    case W:
                        newW = frameWidth - dx;
                        newX = dragOffsetX + dx;
                        break;
                    case S:
                        newH = frameHeight + dy;
                        break;
                    case N:
                        newH = frameHeight - dy;
                        newY = dragOffsetY + dy;
                        break;
                    case SE:
                        newW = frameWidth + dx;
                        newH = frameHeight + dy;
                        break;
                    case SW:
                        newW = frameWidth - dx;
                        newX = dragOffsetX + dx;
                        newH = frameHeight + dy;
                        break;
                    case NE:
                        newW = frameWidth + dx;
                        newH = frameHeight - dy;
                        newY = dragOffsetY + dy;
                        break;
                    case NW:
                        newW = frameWidth - dx;
                        newH = frameHeight - dy;
                        newX = dragOffsetX + dx;
                        newY = dragOffsetY + dy;
                        break;
                    default:
                        break;
                }

                // Enforce minimum size
                if (newW < MIN_WINDOW_WIDTH) {
                    if (newX != dragOffsetX) newX = dragOffsetX + frameWidth - MIN_WINDOW_WIDTH;
                    newW = MIN_WINDOW_WIDTH;
                }
                if (newH < MIN_WINDOW_HEIGHT + TITLE_BAR_HEIGHT) {
                    if (newY != dragOffsetY) newY = dragOffsetY + frameHeight - MIN_WINDOW_HEIGHT - TITLE_BAR_HEIGHT;
                    newH = MIN_WINDOW_HEIGHT + TITLE_BAR_HEIGHT;
                }

                setLayoutX(newX);
                setLayoutY(newY);
                setPrefSize(newW, newH);
                contentBox.setPrefSize(newW, newH);
                embedArea.setPrefSize(newW - BORDER_WIDTH * 2, newH - TITLE_BAR_HEIGHT - BORDER_WIDTH * 2);

                // Notify native window of resize
                if (xWindowId != 0) {
                    nativeResizeWindow(xWindowId,
                        (int)(newW - BORDER_WIDTH * 2),
                        (int)(newH - TITLE_BAR_HEIGHT - BORDER_WIDTH * 2));
                }

                e.consume();
            });

            setOnMouseReleased(e -> {
                if (resizing) {
                    resizing = false;
                    resizeDir = ResizeDirection.NONE;
                    e.consume();
                }
            });
        }

        private ResizeDirection getResizeDirection(double x, double y) {
            double w = getWidth();
            double h = getHeight();
            int edge = 6;

            boolean top = y < edge;
            boolean bottom = y > h - edge;
            boolean left = x < edge;
            boolean right = x > w - edge;

            if (top && left) return ResizeDirection.NW;
            if (top && right) return ResizeDirection.NE;
            if (bottom && left) return ResizeDirection.SW;
            if (bottom && right) return ResizeDirection.SE;
            if (top) return ResizeDirection.N;
            if (bottom) return ResizeDirection.S;
            if (left) return ResizeDirection.W;
            if (right) return ResizeDirection.E;
            return ResizeDirection.NONE;
        }

        // === Accessors ===

        public long getXWindowId() { return xWindowId; }
        public String getTitle() { return title; }
        public String getAppName() { return appName; }
        public String getIconPath() { return iconPath; }
        public int getFrameWidth() { return frameWidth; }
        public int getFrameHeight() { return frameHeight; }

        public void setTitle(String newTitle) {
            this.title = newTitle;
            if (titleLabel != null) titleLabel.setText(newTitle);
        }

        /**
         * Get the native X11 window ID of the embed area.
         * This is where native apps get reparented into.
         * Obtained via JNI from the JavaFX node's underlying X11 window.
         */
        public long getEmbedAreaNativeId() {
            // This would be obtained via JNI: jdesk_get_embed_xid(embedArea)
            // For now, return a placeholder — real implementation goes through libjdesk.so
            return 0;
        }
    }

    // =========================================================================
    //  Native JNI Bridge (implemented in libjdesk.so / jdesk_linux.c)
    // =========================================================================

    /**
     * Reparent a native X11 window into our embed container.
     * Calls XReparentWindow(display, child, parent, 0, 0) and
     * XResizeWindow to fit the embed area.
     */
    private native void nativeReparent(long childXid, long parentXid, int width, int height);

    /**
     * Send WM_DELETE_WINDOW client message to request graceful close.
     */
    private native void nativeSendClose(long xid);

    /**
     * Set X11 input focus to a window.
     */
    private native void nativeSetInputFocus(long xid);

    /**
     * Resize the embedded native window.
     */
    private native void nativeResizeWindow(long xid, int width, int height);

    /**
     * Unmap (hide) a native window.
     */
    private native void nativeUnmapWindow(long xid);

    /**
     * Start SubstructureRedirect on root window to intercept MapRequests.
     * This is what makes JDesk a window manager.
     */
    public native void startWindowManagement();

    // =========================================================================
    //  Static: Load native library
    // =========================================================================

    static {
        try {
            System.loadLibrary("jdesk");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[JDesk:Compositor] WARNING: libjdesk.so not loaded — " +
                    "window management will not function. " + e.getMessage());
        }
    }
}
