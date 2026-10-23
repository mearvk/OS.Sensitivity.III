/*
 * Copyright (C) 2026 MEARVK LLC
 * Author: Maximilian Eric Alexander Rupplin von Keffikon
 *
 * JDesk Writer — JavaFX Word Processor (GUI skin over LibreOffice Writer).
 *
 * A rich text editor with toolbar formatting, page-style editing area,
 * and document structure. LibreOffice runs as a governed subprocess for
 * advanced features (export, mail merge, spell check).
 *
 * License: GPL-2.0
 */

package us.mearvk.jdesk.apps;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.scene.web.*;
import javafx.geometry.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * JDeskWriter — A JavaFX word processor.
 *
 * Uses an HTMLEditor for rich text editing (WYSIWYG).
 * Documents can be saved as HTML or plain text.
 * LibreOffice integration available for .odt/.docx export.
 */
public class JDeskWriter extends BorderPane {

    // Theme (white/light for document editing)
    private static final String BG_CHROME  = "#F7F8FA";
    private static final String BG_PAGE    = "#FFFFFF";
    private static final String BORDER     = "#DADCE0";
    private static final String TEXT_PRI   = "#202124";
    private static final String TEXT_SEC   = "#5F6368";
    private static final String ACCENT     = "#1A73E8";
    private static final String FONT_UI    = "Inter, system-ui, sans-serif";

    // State
    private HTMLEditor editor;
    private Label statusLabel;
    private Path currentFile;
    private boolean modified = false;
    private String documentTitle = "Untitled Document";

    public JDeskWriter() {
        setStyle("-fx-background-color: " + BG_CHROME + ";");

        // Menu bar
        setTop(createMenuBar());

        // Editor area (centered page)
        VBox editorContainer = new VBox();
        editorContainer.setAlignment(Pos.TOP_CENTER);
        editorContainer.setPadding(new Insets(24));
        editorContainer.setStyle("-fx-background-color: #ECEEF1;");

        editor = new HTMLEditor();
        editor.setPrefSize(816, 1056); // A4-ish (8.5x11 at 96dpi)
        editor.setStyle(
            "-fx-background-color: " + BG_PAGE + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        editor.setHtmlText(getDefaultDocument());

        ScrollPane scroll = new ScrollPane(editor);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #ECEEF1; -fx-border-width: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        editorContainer.getChildren().add(scroll);
        VBox.setVgrow(editorContainer, Priority.ALWAYS);
        setCenter(editorContainer);

        // Status bar
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setStyle(
            "-fx-background-color: " + BG_CHROME + ";" +
            "-fx-border-color: " + BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );

        statusLabel = new Label("Ready — " + documentTitle);
        statusLabel.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label pageInfo = new Label("Page 1 of 1 | English (US)");
        pageInfo.setStyle(
            "-fx-text-fill: " + TEXT_SEC + ";" +
            "-fx-font-size: 11px;" +
            "-fx-font-family: " + FONT_UI + ";"
        );

        statusBar.getChildren().addAll(statusLabel, spacer, pageInfo);
        setBottom(statusBar);

        setFocusTraversable(true);
    }

    // === Public API ===

    public void openDocument(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (path.toString().endsWith(".html") || path.toString().endsWith(".htm")) {
                editor.setHtmlText(content);
            } else {
                editor.setHtmlText("<html><body><pre>" + escapeHtml(content) + "</pre></body></html>");
            }
            currentFile = path;
            documentTitle = path.getFileName().toString();
            statusLabel.setText("Opened: " + documentTitle);
            modified = false;
        } catch (IOException e) {
            statusLabel.setText("ERROR: " + e.getMessage());
        }
    }

    public void saveDocument() {
        if (currentFile == null) return;
        try {
            Files.writeString(currentFile, editor.getHtmlText(), StandardCharsets.UTF_8);
            statusLabel.setText("Saved: " + documentTitle);
            modified = false;
        } catch (IOException e) {
            statusLabel.setText("ERROR saving: " + e.getMessage());
        }
    }

    public double getWriterWidth() { return 900; }
    public double getWriterHeight() { return 700; }

    // === Menu Bar ===

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle("-fx-background-color: " + BG_CHROME + ";");

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
            mi("New", this::newDocument),
            mi("Open...", this::openDialog),
            mi("Save", this::saveDocument),
            mi("Save As...", this::saveAsDialog),
            new SeparatorMenuItem(),
            mi("Export PDF...", this::exportPdf)
        );

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
            mi("Undo", () -> {}),
            mi("Redo", () -> {}),
            new SeparatorMenuItem(),
            mi("Cut", () -> {}),
            mi("Copy", () -> {}),
            mi("Paste", () -> {})
        );

        Menu formatMenu = new Menu("Format");
        formatMenu.getItems().addAll(
            mi("Bold", () -> editor.setHtmlText(editor.getHtmlText())),
            mi("Italic", () -> {}),
            mi("Underline", () -> {}),
            new SeparatorMenuItem(),
            mi("Font...", () -> {}),
            mi("Paragraph...", () -> {})
        );

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
            mi("Zoom In", () -> {}),
            mi("Zoom Out", () -> {}),
            mi("Page Width", () -> {})
        );

        menuBar.getMenus().addAll(fileMenu, editMenu, formatMenu, viewMenu);
        return menuBar;
    }

    private MenuItem mi(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    // === Actions ===

    private void newDocument() {
        editor.setHtmlText(getDefaultDocument());
        currentFile = null;
        documentTitle = "Untitled Document";
        statusLabel.setText("New document");
        modified = false;
    }

    private void openDialog() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Open Document");
        chooser.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("All Documents", "*.html", "*.htm", "*.txt", "*.md"),
            new javafx.stage.FileChooser.ExtensionFilter("HTML", "*.html", "*.htm"),
            new javafx.stage.FileChooser.ExtensionFilter("Text", "*.txt"),
            new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
        );
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) openDocument(file.toPath());
    }

    private void saveAsDialog() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save Document As");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("HTML Document", "*.html")
        );
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            currentFile = file.toPath();
            documentTitle = currentFile.getFileName().toString();
            saveDocument();
        }
    }

    private void exportPdf() {
        statusLabel.setText("PDF export requires LibreOffice backend.");
    }

    // === Helpers ===

    private String getDefaultDocument() {
        return "<html><head><style>" +
            "body { font-family: 'Georgia', serif; font-size: 12pt; " +
            "line-height: 1.6; color: #202124; margin: 72px; }" +
            "h1 { font-size: 24pt; font-weight: normal; color: #1A73E8; }" +
            "</style></head><body>" +
            "<h1>Untitled Document</h1>" +
            "<p>Start typing here...</p>" +
            "</body></html>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\n", "<br>");
    }
}
