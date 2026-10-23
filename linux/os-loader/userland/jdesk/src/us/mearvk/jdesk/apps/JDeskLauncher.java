/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Launcher — Application launcher / run dialog.
 *
 * A searchable application launcher (like GNOME Activities or macOS Spotlight).
 * Shows all .desktop files, JDesk apps, and system commands. Type to filter.
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
import java.util.*;
import java.util.stream.*;

/**
 * JDeskLauncher — A searchable app launcher overlay.
 */
public class JDeskLauncher extends VBox {

    // Theme (translucent dark overlay)
    private static final String BG_OVERLAY = "rgba(20, 20, 24, 0.92)";
    private static final String BG_SEARCH  = "#2A2D33";
    private static final String BG_ITEM    = "transparent";
    private static final String BG_HOVER   = "rgba(255, 255, 255, 0.06)";
    private static final String BG_ACTIVE  = "rgba(255, 255, 255, 0.10)";
    private static final String BORDER     = "#3A3E48";
    private static final String TEXT_PRI   = "#E8E8EC";
    private static final String TEXT_SEC   = "#8A8E96";
    private static final String ACCENT     = "#4A88C7";
    private static final String FONT_UI    = "Inter, system-ui, sans-serif";

    // State
    private TextField searchField;
    private ListView<AppEntry> resultList;
    private List<AppEntry> allApps;
    private Runnable onClose;

    public JDeskLauncher() {
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(80, 0, 80, 0));
        setStyle("-fx-background-color: " + BG_OVERLAY + ";");

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search applications...");
        searchField.setPrefWidth(500);
        searchField.setStyle(
            "-fx-background-color: " + BG_SEARCH + ";" +
            "-fx-text-fill: " + TEXT_PRI + ";" +
            "-fx-prompt-text-fill: " + TEXT_SEC + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 24;" +
            "-fx-background-radius: 24;" +
            "-fx-padding: 12 20 12 20;" +
            "-fx-font-size: 16px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        searchField.textProperty().addListener((obs, old, text) -> filterApps(text));
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && onClose != null) onClose.run();
            if (e.getCode() == KeyCode.ENTER) launchSelected();
            if (e.getCode() == KeyCode.DOWN) resultList.requestFocus();
        });

        // Results list
        resultList = new ListView<>();
        resultList.setPrefSize(500, 400);
        resultList.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-width: 0;" +
            "-fx-font-family: " + FONT_UI + ";"
        );
        resultList.setCellFactory(lv -> new AppCell());
        resultList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) launchSelected();
            if (e.getCode() == KeyCode.ESCAPE && onClose != null) onClose.run();
        });
        resultList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) launchSelected();
        });

        VBox container = new VBox(16, searchField, resultList);
        container.setAlignment(Pos.TOP_CENTER);
        container.setMaxWidth(540);
        getChildren().add(container);

        // Load applications
        allApps = discoverApplications();
        resultList.getItems().setAll(allApps);

        // Focus search field
        Platform.runLater(searchField::requestFocus);

        setFocusTraversable(true);
    }

    // === Public API ===

    /** Set callback for when the launcher should close (Escape / after launch). */
    public void setOnClose(Runnable handler) { this.onClose = handler; }

    public double getLauncherWidth() { return 600; }
    public double getLauncherHeight() { return 560; }

    // === Filter ===

    private void filterApps(String query) {
        if (query == null || query.isBlank()) {
            resultList.getItems().setAll(allApps);
            return;
        }
        String lower = query.toLowerCase();
        List<AppEntry> filtered = allApps.stream()
            .filter(a -> a.name.toLowerCase().contains(lower) ||
                         a.description.toLowerCase().contains(lower) ||
                         a.command.toLowerCase().contains(lower))
            .collect(Collectors.toList());
        resultList.getItems().setAll(filtered);
        if (!filtered.isEmpty()) {
            resultList.getSelectionModel().selectFirst();
        }
    }

    // === Launch ===

    private void launchSelected() {
        AppEntry selected = resultList.getSelectionModel().getSelectedItem();
        if (selected == null && !resultList.getItems().isEmpty()) {
            selected = resultList.getItems().get(0);
        }
        if (selected == null) return;

        try {
            String cmd = selected.command;
            // Strip desktop file field codes
            cmd = cmd.replaceAll("%[fFuUdDnNickvm]", "").trim();
            new ProcessBuilder("bash", "-c", cmd)
                .redirectErrorStream(true).start();
        } catch (IOException e) {
            System.err.println("[Launcher] Failed: " + e.getMessage());
        }

        if (onClose != null) onClose.run();
    }

    // === Application Discovery ===

    private List<AppEntry> discoverApplications() {
        List<AppEntry> apps = new ArrayList<>();

        // JDesk built-in apps
        apps.add(new AppEntry("Terminal", "JDesk Terminal Emulator", "jdesk-terminal", "⌨", "system"));
        apps.add(new AppEntry("IDE", "JDesk IDE — IntelliJ IDEA", "jdesk-ide", "💻", "development"));
        apps.add(new AppEntry("Browser", "JDesk Web Browser", "jdesk-browser", "🌐", "internet"));
        apps.add(new AppEntry("Writer", "JDesk Word Processor", "jdesk-writer", "📄", "office"));
        apps.add(new AppEntry("Files", "JDesk File Manager", "jdesk-files", "📁", "system"));
        apps.add(new AppEntry("Settings", "JDesk Settings", "jdesk-settings", "⚙", "system"));
        apps.add(new AppEntry("Software", "JDesk Software Center", "jdesk-software", "📦", "system"));

        // Read .desktop files from standard paths
        String[] desktopDirs = {
            "/usr/share/applications",
            "/usr/local/share/applications",
            System.getProperty("user.home") + "/.local/share/applications"
        };

        for (String dir : desktopDirs) {
            Path dirPath = Path.of(dir);
            if (!Files.isDirectory(dirPath)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.desktop")) {
                for (Path file : stream) {
                    AppEntry entry = parseDesktopFile(file);
                    if (entry != null && !entry.noDisplay) {
                        apps.add(entry);
                    }
                }
            } catch (IOException ignored) {}
        }

        // Sort alphabetically
        apps.sort(Comparator.comparing(a -> a.name.toLowerCase()));
        return apps;
    }

    private AppEntry parseDesktopFile(Path file) {
        String name = null, comment = "", exec = null, icon = "", categories = "";
        boolean noDisplay = false;

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            boolean inDesktopEntry = false;
            while ((line = reader.readLine()) != null) {
                if (line.equals("[Desktop Entry]")) { inDesktopEntry = true; continue; }
                if (line.startsWith("[") && inDesktopEntry) break; // next section

                if (!inDesktopEntry) continue;
                if (line.startsWith("Name=")) name = line.substring(5);
                else if (line.startsWith("Comment=")) comment = line.substring(8);
                else if (line.startsWith("Exec=")) exec = line.substring(5);
                else if (line.startsWith("Icon=")) icon = line.substring(5);
                else if (line.startsWith("Categories=")) categories = line.substring(11);
                else if (line.equals("NoDisplay=true")) noDisplay = true;
            }
        } catch (IOException e) {
            return null;
        }

        if (name == null || exec == null) return null;

        AppEntry entry = new AppEntry(name, comment, exec, guessIcon(categories, icon), categories);
        entry.noDisplay = noDisplay;
        return entry;
    }

    private String guessIcon(String categories, String icon) {
        String cat = categories.toLowerCase();
        if (cat.contains("development") || cat.contains("ide")) return "💻";
        if (cat.contains("web") || cat.contains("browser") || cat.contains("network")) return "🌐";
        if (cat.contains("office") || cat.contains("wordprocessor")) return "📄";
        if (cat.contains("graphics") || cat.contains("image")) return "🎨";
        if (cat.contains("audio") || cat.contains("music") || cat.contains("video")) return "🎵";
        if (cat.contains("game")) return "🎮";
        if (cat.contains("system") || cat.contains("settings")) return "⚙";
        if (cat.contains("utility") || cat.contains("text")) return "🔧";
        if (cat.contains("security")) return "🔒";
        return "◆";
    }

    // === Data Model ===

    private static class AppEntry {
        final String name, description, command, icon, categories;
        boolean noDisplay = false;

        AppEntry(String name, String description, String command, String icon, String categories) {
            this.name = name;
            this.description = description;
            this.command = command;
            this.icon = icon;
            this.categories = categories;
        }
        @Override public String toString() { return name; }
    }

    // === Cell Renderer ===

    private class AppCell extends ListCell<AppEntry> {
        @Override
        protected void updateItem(AppEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setStyle("-fx-background-color: transparent;");
                return;
            }

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));

            Label iconLabel = new Label(item.icon);
            iconLabel.setMinWidth(28);
            iconLabel.setStyle("-fx-font-size: 20px;");

            VBox textBox = new VBox(2);
            Label nameLabel = new Label(item.name);
            nameLabel.setStyle(
                "-fx-text-fill: " + TEXT_PRI + ";" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 500;" +
                "-fx-font-family: " + FONT_UI + ";"
            );
            Label descLabel = new Label(item.description.length() > 60 ?
                item.description.substring(0, 60) + "…" : item.description);
            descLabel.setStyle(
                "-fx-text-fill: " + TEXT_SEC + ";" +
                "-fx-font-size: 11px;" +
                "-fx-font-family: " + FONT_UI + ";"
            );
            textBox.getChildren().addAll(nameLabel, descLabel);

            row.getChildren().addAll(iconLabel, textBox);
            setGraphic(row);
            setText(null);
            setStyle("-fx-background-color: transparent;");
        }
    }
}
