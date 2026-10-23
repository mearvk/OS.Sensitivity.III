/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Browser — JavaFX GUI skin over Chromium.
 *
 * Architecture:
 *   ┌────────────────────────────────────────────────────────────────┐
 *   │  JavaFX (JDesk renders the browser chrome)                    │
 *   │  ┌────────────────────────────────────────────────────────┐   │
 *   │  │ Tab Bar:  [Tab1] [Tab2] [+]                            │   │
 *   │  ├────────────────────────────────────────────────────────┤   │
 *   │  │ Nav:  [←] [→] [⟳] [🏠]  [ URL bar                  ] │   │
 *   │  ├────────────────────────────────────────────────────────┤   │
 *   │  │ WebView / Content Area                                 │   │
 *   │  │  (JavaFX WebEngine renders actual web pages)           │   │
 *   │  │                                                        │   │
 *   │  └────────────────────────────────────────────────────────┘   │
 *   │  │ Status: Ready | https://... | 🔒 TLS 1.3              │   │
 *   │  └────────────────────────────────────────────────────────┘   │
 *   └──────────────────────────────┬────────────────────────────────┘
 *                                  │ (JavaFX WebView built-in OR
 *                                  │  Chromium headless subprocess)
 *   ┌──────────────────────────────┴────────────────────────────────┐
 *   │  Native: Chromium (for full compatibility, governed)          │
 *   │  Fallback: JavaFX WebView (lightweight, built-in)             │
 *   └──────────────────────────────────────────────────────────────┘
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.apps;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.web.*;
import javafx.geometry.*;
import javafx.concurrent.Worker;
import javafx.collections.*;

import java.util.*;

/**
 * JDeskBrowser — A tabbed web browser in JavaFX.
 *
 * Uses JavaFX WebView (WebKit) for rendering. For sites requiring full
 * Chromium compatibility, falls back to a governed Chromium subprocess.
 */
public class JDeskBrowser extends BorderPane {

    // Theme
    private static final String BG_DARK       = "#1E1F22";
    private static final String BG_TOOLBAR    = "#2B2D30";
    private static final String BG_TAB        = "#26282E";
    private static final String BG_TAB_ACTIVE = "#3C3F41";
    private static final String BORDER_COLOR  = "#393B3D";
    private static final String TEXT_PRIMARY  = "#BCBEC4";
    private static final String TEXT_SECONDARY= "#6F737A";
    private static final String ACCENT_BLUE   = "#4A88C7";
    private static final String FONT_UI       = "Inter, system-ui, sans-serif";

    private static final String HOME_URL = "https://www.google.com";

    // State
    private TabPane tabPane;
    private Label statusLabel;

    public JDeskBrowser() {
        setStyle("-fx-background-color: " + BG_DARK + ";");

        // Tab bar + navigation + content
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setStyle(
            "-fx-background-color: " + BG_DARK + ";" +
            "-fx-border-width: 0;"
        );

        setCenter(tabPane);

        // Status bar
        HBox statusBar = new HBox(12);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(3, 10, 3, 10));
        statusBar.setStyle(
            "-fx-background-color: " + BG_DARK + ";" +
            "-fx-border-color: " + BORDER_COLOR + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );
        statusLabel = new Label("Ready");
        statusLabel.setStyle(
            "-fx-text-fill: " + TEXT_SECONDARY + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        statusBar.getChildren().add(statusLabel);
        setBottom(statusBar);

        // Open initial tab
        newTab(HOME_URL);

        // Keyboard shortcuts
        setOnKeyPressed(this::handleKeyPress);
        setFocusTraversable(true);
    }

    // === Public API ===

    public void navigate(String url) {
        Tab current = tabPane.getSelectionModel().getSelectedItem();
        if (current != null && current.getContent() instanceof BorderPane) {
            BorderPane bp = (BorderPane) current.getContent();
            if (bp.getCenter() instanceof WebView) {
                WebView wv = (WebView) bp.getCenter();
                wv.getEngine().load(normalizeUrl(url));
                // Update URL bar
                if (bp.getTop() instanceof HBox) {
                    HBox nav = (HBox) bp.getTop();
                    for (javafx.scene.Node n : nav.getChildren()) {
                        if (n instanceof TextField) {
                            ((TextField) n).setText(url);
                        }
                    }
                }
            }
        }
    }

    public void newTab(String url) {
        Tab tab = new Tab("New Tab");

        BorderPane content = new BorderPane();
        content.setStyle("-fx-background-color: " + BG_DARK + ";");

        // Navigation bar
        HBox navBar = createNavBar(tab);
        content.setTop(navBar);

        // WebView
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        webView.setStyle("-fx-background-color: white;");

        // Track title and URL
        engine.titleProperty().addListener((obs, old, title) -> {
            if (title != null && !title.isEmpty()) {
                String shortTitle = title.length() > 25 ? title.substring(0, 25) + "…" : title;
                tab.setText(shortTitle);
            }
        });

        engine.locationProperty().addListener((obs, old, loc) -> {
            if (navBar.getChildren().size() > 4) {
                javafx.scene.Node urlField = navBar.getChildren().get(4);
                if (urlField instanceof TextField) {
                    ((TextField) urlField).setText(loc);
                }
            }
        });

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.RUNNING) {
                statusLabel.setText("Loading...");
            } else if (state == Worker.State.SUCCEEDED) {
                String loc = engine.getLocation();
                boolean secure = loc != null && loc.startsWith("https://");
                statusLabel.setText((secure ? "🔒 " : "⚠ ") + (loc != null ? loc : ""));
            } else if (state == Worker.State.FAILED) {
                statusLabel.setText("✗ Failed to load page");
            }
        });

        content.setCenter(webView);
        tab.setContent(content);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        // Load URL
        if (url != null && !url.isEmpty()) {
            engine.load(normalizeUrl(url));
        }
    }

    public double getBrowserWidth() { return 1100; }
    public double getBrowserHeight() { return 720; }

    // === Navigation Bar ===

    private HBox createNavBar(Tab tab) {
        HBox nav = new HBox(6);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(6, 10, 6, 10));
        nav.setStyle(
            "-fx-background-color: " + BG_TOOLBAR + ";" +
            "-fx-border-color: transparent transparent " + BORDER_COLOR + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Button backBtn = navButton("←", "Back");
        Button fwdBtn = navButton("→", "Forward");
        Button reloadBtn = navButton("⟳", "Reload");
        Button homeBtn = navButton("⌂", "Home");

        TextField urlField = new TextField(HOME_URL);
        urlField.setPrefWidth(600);
        urlField.setStyle(
            "-fx-background-color: " + BG_DARK + ";" +
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-radius: 16;" +
            "-fx-background-radius: 16;" +
            "-fx-padding: 5 12 5 12;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button newTabBtn = navButton("+", "New Tab");

        // Actions
        backBtn.setOnAction(e -> {
            WebView wv = getWebView(tab);
            if (wv != null) {
                WebHistory h = wv.getEngine().getHistory();
                if (h.getCurrentIndex() > 0) h.go(-1);
            }
        });
        fwdBtn.setOnAction(e -> {
            WebView wv = getWebView(tab);
            if (wv != null) {
                WebHistory h = wv.getEngine().getHistory();
                if (h.getCurrentIndex() < h.getEntries().size() - 1) h.go(1);
            }
        });
        reloadBtn.setOnAction(e -> {
            WebView wv = getWebView(tab);
            if (wv != null) wv.getEngine().reload();
        });
        homeBtn.setOnAction(e -> {
            WebView wv = getWebView(tab);
            if (wv != null) wv.getEngine().load(HOME_URL);
        });
        newTabBtn.setOnAction(e -> newTab(HOME_URL));

        urlField.setOnAction(e -> {
            WebView wv = getWebView(tab);
            if (wv != null) wv.getEngine().load(normalizeUrl(urlField.getText()));
        });

        nav.getChildren().addAll(backBtn, fwdBtn, reloadBtn, homeBtn, urlField, newTabBtn);
        return nav;
    }

    private Button navButton(String text, String tooltip) {
        Button btn = new Button(text);
        btn.setTooltip(new Tooltip(tooltip));
        btn.setMinSize(30, 30);
        btn.setMaxSize(30, 30);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 15px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 50%;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + BG_TAB_ACTIVE + ";" +
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 15px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 50%;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 15px;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 50%;"
        ));
        return btn;
    }

    // === Helpers ===

    private WebView getWebView(Tab tab) {
        if (tab.getContent() instanceof BorderPane) {
            javafx.scene.Node center = ((BorderPane) tab.getContent()).getCenter();
            if (center instanceof WebView) return (WebView) center;
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return HOME_URL;
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            if (url.contains(".") && !url.contains(" ")) {
                return "https://" + url;
            }
            return "https://www.google.com/search?q=" + url.replace(" ", "+");
        }
        return url;
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case T: newTab(HOME_URL); event.consume(); break;
                case W:
                    Tab current = tabPane.getSelectionModel().getSelectedItem();
                    if (current != null) tabPane.getTabs().remove(current);
                    event.consume();
                    break;
                case L:
                    // Focus URL bar
                    event.consume();
                    break;
                case R:
                    Tab t = tabPane.getSelectionModel().getSelectedItem();
                    WebView wv = getWebView(t);
                    if (wv != null) wv.getEngine().reload();
                    event.consume();
                    break;
                default: break;
            }
        }
    }
}
