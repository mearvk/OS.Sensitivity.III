package com.mearvk.installer;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Ubuntu White Edition professional installer control surface.
 *
 * The GUI plans operations and delegates execution to typed platform adapters.
 * Privileged and destructive operations are intentionally not implemented as
 * arbitrary shell execution in this first control layer.
 */
public final class WhiteInstaller extends Application {
    private final Label host = new Label("Detecting host…");
    private final Label iso = new Label("No ISO selected");
    private final Label target = new Label("No installation target selected");
    private final TextArea plan = new TextArea();
    private final ComboBox<String> operation = new ComboBox<>();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Ubuntu White Edition — Professional Installer");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color:#ffffff;");

        Label title = new Label("Ubuntu White Edition");
        title.setStyle("-fx-font-size:28px;-fx-font-weight:bold;-fx-text-fill:#202124;");
        Label subtitle = new Label("Professional Installation & Virtualization");
        subtitle.setStyle("-fx-font-size:15px;-fx-text-fill:#5f6368;");
        VBox header = new VBox(4, title, subtitle, host);
        header.setPadding(new Insets(0, 0, 24, 0));
        root.setTop(header);

        operation.getItems().addAll(
                "Build ISO",
                "Install to root directory",
                "Install to named partition",
                "Run existing ISO",
                "Run ISO in virtual machine",
                "Inspect system"
        );
        operation.getSelectionModel().selectFirst();
        operation.setMaxWidth(Double.MAX_VALUE);

        Button chooseIso = new Button("Choose ISO…");
        chooseIso.setOnAction(e -> chooseIso(stage));
        Button chooseTarget = new Button("Choose target…");
        chooseTarget.setOnAction(e -> chooseTarget(stage));

        GridPane facts = new GridPane();
        facts.setHgap(12); facts.setVgap(12);
        facts.add(new Label("Operation"), 0, 0);
        facts.add(operation, 1, 0);
        facts.add(new Label("Source"), 0, 1);
        facts.add(iso, 1, 1);
        facts.add(chooseIso, 2, 1);
        facts.add(new Label("Target"), 0, 2);
        facts.add(target, 1, 2);
        facts.add(chooseTarget, 2, 2);
        ColumnConstraints first = new ColumnConstraints();
        ColumnConstraints second = new ColumnConstraints(); second.setHgrow(Priority.ALWAYS);
        facts.getColumnConstraints().addAll(first, second, new ColumnConstraints());

        plan.setEditable(false);
        plan.setPrefRowCount(9);
        plan.setStyle("-fx-control-inner-background:#fafafa;-fx-text-fill:#202124;");
        plan.setText("Select an operation. The installer will construct a reviewable plan before execution.\n\n"
                + "No disk or partition changes occur from this screen alone.");

        Button review = primary("Review plan");
        review.setOnAction(e -> buildPlan());
        Button execute = new Button("Execute reviewed plan");
        execute.setDisable(true);
        execute.setOnAction(e -> showInfo("Execution boundary", "Execution is delegated to the platform adapter after explicit review and elevation."));
        Button cancel = new Button("Close");
        cancel.setOnAction(e -> stage.close());

        HBox actions = new HBox(10, review, execute, cancel);
        actions.setPadding(new Insets(18, 0, 0, 0));

        VBox content = new VBox(22, facts, new Label("Plan / verification"), plan, actions);
        root.setCenter(content);

        host.setText("Host: " + HostInfo.describe());
        Scene scene = new Scene(root, 900, 620);
        stage.setScene(scene);
        stage.show();
    }

    private void chooseIso(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Ubuntu White Edition ISO");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ISO images", "*.iso"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) iso.setText(selected.getAbsolutePath());
    }

    private void chooseTarget(Stage stage) {
        var chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select installation root directory");
        File selected = chooser.showDialog(stage);
        if (selected != null) target.setText(selected.getAbsolutePath());
    }

    private void buildPlan() {
        plan.setText("Operation: " + operation.getValue() + "\n"
                + "Source: " + iso.getText() + "\n"
                + "Target: " + target.getText() + "\n\n"
                + "1. Validate host capabilities.\n"
                + "2. Validate source ISO/rootfs where required.\n"
                + "3. Resolve the platform adapter.\n"
                + "4. Display all privileged/destructive effects.\n"
                + "5. Require explicit confirmation.\n"
                + "6. Execute only the allow-listed adapter operation.\n"
                + "7. Verify result and produce an audit report.");
    }

    private Button primary(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:#202124;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:9 18 9 18;");
        return b;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) { launch(args); }

    private static final class HostInfo {
        static String describe() {
            String os = System.getProperty("os.name", "Unknown");
            String arch = System.getProperty("os.arch", "Unknown");
            return os + " / " + arch;
        }
    }
}
