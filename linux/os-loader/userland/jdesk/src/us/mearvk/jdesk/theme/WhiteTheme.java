/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * WhiteTheme — JDesk default visual theme specification.
 *
 * Mirrors the jdesk_theme_colors / jdesk_theme_typography structs in jdesk.h.
 * All colors expressed as JavaFX CSS strings and as 0xAARRGGBB int values.
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.theme;

import javafx.scene.paint.Color;

/**
 * White theme — clean, modern light theme for the JDesk desktop environment.
 *
 * Usage:
 *   WhiteTheme theme = WhiteTheme.getInstance();
 *   node.setStyle(theme.getBaseStyle());
 */
public final class WhiteTheme {

    // =========================================================================
    //  Singleton
    // =========================================================================

    private static final WhiteTheme INSTANCE = new WhiteTheme();

    private WhiteTheme() {}

    public static WhiteTheme getInstance() {
        return INSTANCE;
    }

    // =========================================================================
    //  Theme CSS File
    // =========================================================================

    /** Path to the default theme stylesheet. */
    public static final String DEFAULT_THEME_CSS = "/opt/jdesk/themes/cool-white.css";
    public static final String THEME_DIR = "/opt/jdesk/themes/";

    /**
     * Get the CSS file URL for applying the theme to a JavaFX Scene.
     * Usage: scene.getStylesheets().add(WhiteTheme.getInstance().getStylesheetUrl());
     */
    public String getStylesheetUrl() {
        java.io.File f = new java.io.File(DEFAULT_THEME_CSS);
        if (f.exists()) {
            return f.toURI().toString();
        }
        // Fallback: use internal resource
        return getClass().getResource("/themes/cool-white.css") != null
            ? getClass().getResource("/themes/cool-white.css").toExternalForm()
            : "";
    }

    // =========================================================================
    //  Colors — hex strings (CSS)
    // =========================================================================

    /** Main background: pure white */
    public static final String COLOR_BACKGROUND       = "#FFFFFF";
    /** Card/panel surface: off-white */
    public static final String COLOR_SURFACE          = "#F8F9FA";
    /** Primary accent: Google Blue */
    public static final String COLOR_PRIMARY          = "#1A73E8";
    /** Secondary: medium grey */
    public static final String COLOR_SECONDARY        = "#5F6368";
    /** Main text: near-black */
    public static final String COLOR_TEXT_PRIMARY     = "#202124";
    /** Subtitle/caption text */
    public static final String COLOR_TEXT_SECONDARY   = "#5F6368";
    /** Borders and dividers */
    public static final String COLOR_BORDER           = "#DADCE0";
    /** Hover fill */
    public static final String COLOR_HOVER            = "#F1F3F4";
    /** Active/pressed fill */
    public static final String COLOR_ACTIVE           = "#E8EAED";
    /** Error red */
    public static final String COLOR_ERROR            = "#D93025";
    /** Success green */
    public static final String COLOR_SUCCESS          = "#1E8E3E";
    /** Warning amber */
    public static final String COLOR_WARNING          = "#F9AB00";
    /** Drop shadow: 10% black */
    public static final String COLOR_SHADOW           = "rgba(0,0,0,0.1)";

    // =========================================================================
    //  Colors — JavaFX Color objects
    // =========================================================================

    public static final Color FX_BACKGROUND     = Color.web(COLOR_BACKGROUND);
    public static final Color FX_SURFACE        = Color.web(COLOR_SURFACE);
    public static final Color FX_PRIMARY        = Color.web(COLOR_PRIMARY);
    public static final Color FX_SECONDARY      = Color.web(COLOR_SECONDARY);
    public static final Color FX_TEXT_PRIMARY   = Color.web(COLOR_TEXT_PRIMARY);
    public static final Color FX_TEXT_SECONDARY = Color.web(COLOR_TEXT_SECONDARY);
    public static final Color FX_BORDER         = Color.web(COLOR_BORDER);
    public static final Color FX_HOVER          = Color.web(COLOR_HOVER);
    public static final Color FX_ACTIVE         = Color.web(COLOR_ACTIVE);
    public static final Color FX_ERROR          = Color.web(COLOR_ERROR);
    public static final Color FX_SUCCESS        = Color.web(COLOR_SUCCESS);
    public static final Color FX_WARNING        = Color.web(COLOR_WARNING);

    // =========================================================================
    //  Typography
    // =========================================================================

    public static final String FONT_FAMILY      = "Inter, system-ui, sans-serif";
    public static final int    FONT_SIZE_TITLE  = 20;   // px
    public static final int    FONT_SIZE_BODY   = 14;   // px
    public static final int    FONT_SIZE_CAPTION= 12;   // px
    public static final int    FONT_SIZE_PANEL  = 11;   // px
    public static final int    FONT_WEIGHT_NORMAL = 400;
    public static final int    FONT_WEIGHT_MEDIUM = 500;
    public static final int    FONT_WEIGHT_BOLD   = 700;

    // =========================================================================
    //  Geometry
    // =========================================================================

    public static final int    CORNER_RADIUS    = 8;    // px
    public static final int    SHADOW_ELEVATION = 2;    // material dp
    public static final int    ANIMATION_MS     = 200;  // transition duration

    // =========================================================================
    //  CSS style strings
    // =========================================================================

    /**
     * Base inline style for the root desktop surface.
     */
    public String getBaseStyle() {
        return "-fx-background-color: " + COLOR_BACKGROUND + ";";
    }

    /**
     * Inline style for a surface card (panel, dialog, tooltip).
     */
    public String getSurfaceStyle() {
        return "-fx-background-color: " + COLOR_SURFACE + ";"
             + "-fx-background-radius: " + CORNER_RADIUS + ";"
             + "-fx-border-color: " + COLOR_BORDER + ";"
             + "-fx-border-radius: " + CORNER_RADIUS + ";"
             + "-fx-border-width: 1;";
    }

    /**
     * Inline style for a desktop icon label.
     */
    public String getIconLabelStyle() {
        return "-fx-text-fill: " + COLOR_TEXT_PRIMARY + ";"
             + "-fx-font-size: " + FONT_SIZE_PANEL + "px;"
             + "-fx-font-family: '" + FONT_FAMILY + "';";
    }

    /**
     * Inline style applied on icon cell hover.
     */
    public String getHoverStyle() {
        return "-fx-background-color: " + COLOR_HOVER + ";"
             + "-fx-background-radius: " + CORNER_RADIUS + ";";
    }

    /**
     * Inline style for the taskbar panel.
     */
    public String getPanelStyle() {
        return "-fx-background-color: " + COLOR_SURFACE + ";"
             + "-fx-border-color: " + COLOR_BORDER + ";"
             + "-fx-border-width: 0 0 1 0;";
    }

    @Override
    public String toString() {
        return "WhiteTheme{background=" + COLOR_BACKGROUND
             + ", primary=" + COLOR_PRIMARY
             + ", font=" + FONT_FAMILY + "}";
    }
}
