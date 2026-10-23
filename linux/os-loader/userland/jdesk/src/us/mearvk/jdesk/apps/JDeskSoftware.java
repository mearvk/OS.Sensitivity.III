/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Software — Package Manager / Software Center.
 *
 * A graphical package manager that presents installed and available
 * software as a browseable catalog. Backend: apt (dpkg) on Ubuntu.
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

/**
 * JDeskSoftware — A JavaFX software center / package manager GUI.
 */
public class JDeskSoftware extends BorderPane {

    // Theme (white/light for software browsing)
    private static final String BG_MAIN    = "#FFFFFF";
    private static final String BG_SIDEBAR = "#F7F8FA";
    private static final String BG_CARD    = "#FFFFFF";
    private static final String BORDER     = "#DADCE0";
    private static final String TEXT_PRI   = "#202124";
    private static final String TEXT_SEC   = "#5F6368";
    private static final String ACCENT     = "#1A73E8";
    private static final String SUCCESS    = "#1E8E3E";
    private static final String FONT_UI    = "Inter, system-ui, sans-serif";

    // State
    private ListView<PackageInfo> packageList;
    private VBox detailPane;
    private TextField searchField;
    private Label statusLabel;
    private String currentCategory = "installed";

    public JDeskSoftware() {
        setStyle("-fx-background-color: " + BG_MAIN + ";");

        // Toolbar (search)
        setTop(createToolbar());

        // Left: categories
        setLeft(createCategorySidebar());

        // Center: package list
        packageList = new ListView<>();
        packageList.setStyle(
            "-fx-background-color: " + BG_MAIN + ";" +
            "-fx-border-width: 0;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        packageList.setCellFactory(lv -> new PackageCell());
        packageList.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, pkg) -> { if (pkg != null) showPackageDetail(pkg); });

        // Right: detail pane
        detailPane = new VBox(12);
        detailPane.setPrefWidth(300);
        detailPane.setPadding(new Insets(16));
        detailPane.setStyle(
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-border-color: " + BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 0 0 0 1;"
        );
        showEmptyDetail();

        SplitPane split = new SplitPane(packageList, detailPane);
        split.setDividerPositions(0.6);
        split.setStyle("-fx-background-color: " + BG_MAIN + ";");
        setCenter(split);

        // Status bar
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setStyle(
            "-fx-background-color: " + BG_MAIN + ";" +
            "-fx-border-color: " + BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );
        statusLabel = new Label("Ready");
        statusLabel.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        statusBar.getChildren().add(statusLabel);
        setBottom(statusBar);

        // Load installed packages
        loadInstalledPackages();

        setFocusTraversable(true);
    }

    // === Public API ===

    public double getSoftwareWidth() { return 950; }
    public double getSoftwareHeight() { return 650; }

    // === Toolbar ===

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle(
            "-fx-background-color: " + BG_MAIN + ";" +
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label title = new Label("Software");
        title.setStyle(
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: 300;" +
            "-fx-font-family: " + FONT_UI + ";"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search packages...");
        searchField.setPrefWidth(260);
        searchField.setStyle(
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 18;" +
            "-fx-background-radius: 18;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        searchField.setOnAction(e -> searchPackages(searchField.getText()));

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setStyle(
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-background-radius: 4;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 4;" +
            "-fx-cursor: hand;"
        );
        refreshBtn.setOnAction(e -> loadInstalledPackages());

        toolbar.getChildren().addAll(title, spacer, searchField, refreshBtn);
        return toolbar;
    }

    // === Category Sidebar ===

    private VBox createCategorySidebar() {
        VBox sidebar = new VBox(2);
        sidebar.setPrefWidth(160);
        sidebar.setPadding(new Insets(8));
        sidebar.setStyle(
            "-fx-background-color: " + BG_SIDEBAR + ";" +
            "-fx-border-color: transparent " + BORDER + " transparent transparent;" +
            "-fx-border-width: 0 1 0 0;"
        );

        addCategory(sidebar, "📦 Installed", "installed");
        addCategory(sidebar, "⬆ Upgradable", "upgradable");
        addCategory(sidebar, "🔧 System", "system");

        sidebar.getChildren().add(new Separator());

        Label catHeader = new Label("Categories");
        catHeader.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 10px;" +
            "-fx-font-weight: bold;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 8 8 4 8;"
        );
        sidebar.getChildren().add(catHeader);

        addCategory(sidebar, "🌐 Internet", "internet");
        addCategory(sidebar, "💻 Development", "development");
        addCategory(sidebar, "🎨 Graphics", "graphics");
        addCategory(sidebar, "🎵 Multimedia", "multimedia");
        addCategory(sidebar, "📄 Office", "office");
        addCategory(sidebar, "🛠 Utilities", "utilities");
        addCategory(sidebar, "🔒 Security", "security");

        return sidebar;
    }

    private void addCategory(VBox sidebar, String label, String category) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 12px;" +
            "-fx-font-family: " + FONT_UI + ";" +
            "-fx-padding: 5 8 5 8;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;"
        );
        btn.setOnAction(e -> {
            currentCategory = category;
            loadInstalledPackages();
        });
        sidebar.getChildren().add(btn);
    }

    // === Detail Pane ===

    private void showEmptyDetail() {
        detailPane.getChildren().clear();
        Label hint = new Label("Select a package to view details");
        hint.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-font-family: " + FONT_UI + ";");
        detailPane.getChildren().add(hint);
    }

    private void showPackageDetail(PackageInfo pkg) {
        detailPane.getChildren().clear();

        Label name = new Label(pkg.name);
        name.setStyle(
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: 600;" +
            "-fx-font-family: " + FONT_UI + ";"
        );

        Label version = new Label("Version: " + pkg.version);
        version.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-font-family: " + FONT_UI + ";");

        Label size = new Label("Size: " + pkg.size);
        size.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-font-family: " + FONT_UI + ";");

        Label desc = new Label(pkg.description);
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 12px; -fx-font-family: " + FONT_UI + ";");

        Label status = new Label(pkg.installed ? "✓ Installed" : "○ Not installed");
        status.setStyle(
            "-fx-text-fill: " + (pkg.installed ? SUCCESS : TEXT_SEC) + ";" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 600;" +
            "-fx-font-family: " + FONT_UI + ";"
        );

        Button actionBtn;
        if (pkg.installed) {
            actionBtn = new Button("Remove");
            actionBtn.setStyle(
                "-fx-background-color: #FFF0F0;" +
                "-fx-text-fill: #D93025;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: " + FONT_UI + ";" +
                "-fx-background-radius: 4;" +
                "-fx-border-color: #FADBD8;" +
                "-fx-border-radius: 4;" +
                "-fx-cursor: hand;"
            );
        } else {
            actionBtn = new Button("Install");
            actionBtn.setStyle(
                "-fx-background-color: " + ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: " + FONT_UI + ";" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: hand;"
            );
        }
        actionBtn.setOnAction(e -> statusLabel.setText("Action requires sudo_gate authorization."));

        detailPane.getChildren().addAll(name, status, new Separator(), version, size, new Separator(), desc, actionBtn);
    }

    // === Package Loading (via dpkg/apt) ===

    private void loadInstalledPackages() {
        statusLabel.setText("Loading packages...");
        Thread loader = new Thread(() -> {
            List<PackageInfo> packages = queryPackages();
            Platform.runLater(() -> {
                packageList.getItems().setAll(packages);
                statusLabel.setText(packages.size() + " packages");
            });
        }, "jdesk-software-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void searchPackages(String query) {
        if (query == null || query.isBlank()) {
            loadInstalledPackages();
            return;
        }
        statusLabel.setText("Searching: " + query);
        Thread searcher = new Thread(() -> {
            List<PackageInfo> results = searchApt(query);
            Platform.runLater(() -> {
                packageList.getItems().setAll(results);
                statusLabel.setText(results.size() + " results for '" + query + "'");
            });
        }, "jdesk-software-search");
        searcher.setDaemon(true);
        searcher.start();
    }

    private List<PackageInfo> queryPackages() {
        List<PackageInfo> packages = new ArrayList<>();
        try {
            Process proc = new ProcessBuilder("dpkg-query", "-W",
                "-f=${Package}\t${Version}\t${Installed-Size}\t${Description}\n")
                .redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 500) {
                    String[] parts = line.split("\t", 4);
                    if (parts.length >= 2) {
                        String name = parts[0];
                        String version = parts.length > 1 ? parts[1] : "";
                        String size = parts.length > 2 ? formatSize(parts[2]) : "";
                        String desc = parts.length > 3 ? parts[3] : "";
                        packages.add(new PackageInfo(name, version, size, desc, true));
                        count++;
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            packages.add(new PackageInfo("(error)", e.getMessage(), "", "Failed to query packages", false));
        }
        return packages;
    }

    private List<PackageInfo> searchApt(String query) {
        List<PackageInfo> results = new ArrayList<>();
        try {
            Process proc = new ProcessBuilder("apt-cache", "search", query)
                .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 200) {
                    int dash = line.indexOf(" - ");
                    if (dash > 0) {
                        String name = line.substring(0, dash);
                        String desc = line.substring(dash + 3);
                        results.add(new PackageInfo(name, "", "", desc, false));
                        count++;
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            results.add(new PackageInfo("(error)", "", "", e.getMessage(), false));
        }
        return results;
    }

    private String formatSize(String kbStr) {
        try {
            long kb = Long.parseLong(kbStr.trim());
            if (kb < 1024) return kb + " KB";
            return String.format("%.1f MB", kb / 1024.0);
        } catch (NumberFormatException e) {
            return kbStr;
        }
    }

    // === Data Model ===

    private static class PackageInfo {
        final String name, version, size, description;
        final boolean installed;
        PackageInfo(String name, String version, String size, String description, boolean installed) {
            this.name = name;
            this.version = version;
            this.size = size;
            this.description = description;
            this.installed = installed;
        }
        @Override public String toString() { return name; }
    }

    // === Cell Renderer ===

    private class PackageCell extends ListCell<PackageInfo> {
        @Override
        protected void updateItem(PackageInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            VBox cell = new VBox(2);
            cell.setPadding(new Insets(6, 10, 6, 10));

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(item.name);
            name.setStyle(
                "-fx-text-fill: " + TEXT_PRI + ";" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 600;" +
                "-fx-font-family: " + FONT_UI + ";"
            );

            Label ver = new Label(item.version);
            ver.setStyle(
                "-fx-text-fill: " + TEXT_SEC + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-family: " + FONT_UI + ";"
            );

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label status = new Label(item.installed ? "✓" : "");
            status.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 12px;");

            header.getChildren().addAll(name, ver, spacer, status);

            Label desc = new Label(item.description.length() > 80 ?
                item.description.substring(0, 80) + "…" : item.description);
            desc.setStyle(
                "-fx-text-fill: " + TEXT_SEC + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-family: " + FONT_UI + ";"
            );

            cell.getChildren().addAll(header, desc);
            setGraphic(cell);
            setText(null);
        }
    }
}
