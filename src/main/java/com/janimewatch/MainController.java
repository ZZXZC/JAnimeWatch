package com.janimewatch;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.util.List;

public class MainController {

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ListView<String> resultsList;
    @FXML private Spinner<Integer> episodeSpinner;
    @FXML private ToggleGroup playerGroup;
    @FXML private RadioButton mpvRadio;
    @FXML private RadioButton vlcRadio;
    @FXML private ComboBox<String> qualityBox;
    @FXML private CheckBox dubCheck;
    @FXML private CheckBox downloadCheck;
    @FXML private Button watchButton;
    @FXML private Button updateButton;
    @FXML private TextArea outputArea;
    @FXML private Text statusText;

    private final AniCliService aniCliService = new AniCliService();
    private final ObservableList<String> searchResults = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        resultsList.setItems(searchResults);

        SpinnerValueFactory.IntegerSpinnerValueFactory spvf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1);
        episodeSpinner.setValueFactory(spvf);

        qualityBox.getItems().addAll("", "1080", "720", "480");
        qualityBox.getSelectionModel().selectFirst();

        mpvRadio.setSelected(true);

        checkAniCli();
    }

    private void checkAniCli() {
        new Thread(() -> {
            boolean installed = aniCliService.isInstalled();
            Platform.runLater(() -> {
                if (installed) {
                    statusText.setText("ani-cli detected");
                    setControlsDisabled(false);
                } else {
                    statusText.setText("ani-cli not found — install it first");
                    setControlsDisabled(true);
                }
            });
        }).start();
    }

    private void setControlsDisabled(boolean disabled) {
        searchField.setDisable(disabled);
        searchButton.setDisable(disabled);
        watchButton.setDisable(disabled);
        updateButton.setDisable(disabled);
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        searchResults.clear();
        outputArea.clear();
        statusText.setText("Searching...");
        searchButton.setDisable(true);

        aniCliService.searchAnime(query, line -> {
            Platform.runLater(() -> {
                searchResults.add(line);
                outputArea.appendText(line + "\n");
            });
        }).thenRun(() -> {
            Platform.runLater(() -> {
                statusText.setText("Found " + searchResults.size() + " results");
                searchButton.setDisable(false);
            });
        });
    }

    @FXML
    private void onWatch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }

        int episode = episodeSpinner.getValue();
        WatchOptions opts = new WatchOptions();
        opts.setDub(dubCheck.isSelected());
        opts.setDownload(downloadCheck.isSelected());

        if (vlcRadio.isSelected()) {
            opts.setPlayer("vlc");
        } else {
            opts.setPlayer("mpv");
        }

        String quality = qualityBox.getValue();
        opts.setQuality(quality);

        outputArea.clear();
        statusText.setText("Launching...");
        watchButton.setDisable(true);

        aniCliService.watchAnime(query, episode, opts, line -> {
            Platform.runLater(() -> outputArea.appendText(line + "\n"));
        }).thenAccept(exitCode -> {
            Platform.runLater(() -> {
                statusText.setText(exitCode == 0 ? "Done" : "Exited with errors");
                watchButton.setDisable(false);
            });
        });
    }

    @FXML
    private void onUpdate() {
        outputArea.clear();
        statusText.setText("Updating ani-cli...");
        updateButton.setDisable(true);

        aniCliService.update(line -> {
            Platform.runLater(() -> outputArea.appendText(line + "\n"));
        }).thenAccept(exitCode -> {
            Platform.runLater(() -> {
                statusText.setText(exitCode == 0 ? "Update complete" : "Update failed");
                updateButton.setDisable(false);
            });
        });
    }
}
