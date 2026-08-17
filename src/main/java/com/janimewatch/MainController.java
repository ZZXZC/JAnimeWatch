package com.janimewatch;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Optional;

public class MainController {

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ListView<AnimeResult> resultsList;
    @FXML private Spinner<Integer> episodeSpinner;
    @FXML private ToggleGroup playerGroup;
    @FXML private RadioButton mpvRadio;
    @FXML private RadioButton vlcRadio;
    @FXML private ComboBox<String> qualityBox;
    @FXML private CheckBox dubCheck;
    @FXML private CheckBox downloadCheck;
    @FXML private Button watchButton;
    @FXML private Button historyButton;
    @FXML private Button updateButton;
    @FXML private TextArea outputArea;
    @FXML private Text statusText;

    private final AllAnimeService apiService = new AllAnimeService();
    private final HistoryService historyService = new HistoryService();
    private final ObservableList<AnimeResult> searchResults = FXCollections.observableArrayList();
    private List<Integer> currentEpisodes;

    @FXML
    public void initialize() {
        resultsList.setItems(searchResults);
        resultsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(AnimeResult item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        SpinnerValueFactory.IntegerSpinnerValueFactory spvf =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1);
        episodeSpinner.setValueFactory(spvf);
        episodeSpinner.setEditable(true);

        qualityBox.getItems().addAll("best", "1080", "720", "480");
        qualityBox.getSelectionModel().selectFirst();

        mpvRadio.setSelected(true);

        outputArea.setEditable(false);

        checkDependencies();
    }

    private void checkDependencies() {
        new Thread(() -> {
            boolean curlOk = apiService.isCurlInstalled();
            boolean mpvOk = apiService.isPlayerInstalled("mpv");
            boolean vlcOk = apiService.isPlayerInstalled("vlc");

            Platform.runLater(() -> {
                if (!curlOk) {
                    statusText.setText("curl not found - install curl first");
                    setControlsDisabled(true);
                } else if (!mpvOk && !vlcOk) {
                    statusText.setText("mpv or vlc not found - install a player");
                    setControlsDisabled(true);
                } else {
                    String player = mpvOk ? "mpv" : "vlc";
                    statusText.setText("Ready (" + player + " detected)");
                    setControlsDisabled(false);
                }
            });
        }).start();
    }

    private void setControlsDisabled(boolean disabled) {
        searchField.setDisable(disabled);
        searchButton.setDisable(disabled);
        watchButton.setDisable(disabled);
        historyButton.setDisable(disabled);
        updateButton.setDisable(disabled);
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        searchResults.clear();
        outputArea.clear();
        statusText.setText("Searching...");
        searchButton.setDisable(true);

        new Thread(() -> {
            List<AnimeResult> results = apiService.search(query, msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n"))
            );
            Platform.runLater(() -> {
                searchResults.addAll(results);
                statusText.setText("Found " + results.size() + " results - select one and click Watch");
                searchButton.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void onWatch() {
        AnimeResult selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusText.setText("Select an anime from the search results first");
            return;
        }

        final String quality = qualityBox.getValue();
        final String mode = dubCheck.isSelected() ? "dub" : "sub";
        final boolean download = downloadCheck.isSelected();
        final String player = vlcRadio.isSelected() ? "vlc" : "mpv";

        outputArea.clear();
        statusText.setText("Loading episode " + episodeSpinner.getValue() + "...");
        watchButton.setDisable(true);

        new Thread(() -> {
            // Fetch episodes list to validate
            if (currentEpisodes == null || currentEpisodes.isEmpty()) {
                currentEpisodes = apiService.getEpisodes(selected.getId(), msg ->
                    Platform.runLater(() -> outputArea.appendText(msg + "\n"))
                );
            }

            int episode = episodeSpinner.getValue();
            if (!currentEpisodes.contains(episode)) {
                int nearest = currentEpisodes.isEmpty() ? 1 : currentEpisodes.get(0);
                for (int ep : currentEpisodes) {
                    if (Math.abs(ep - episode) < Math.abs(nearest - episode)) {
                        nearest = ep;
                    }
                }
                final int nearestEp = nearest;
                final int requestedEp = episode;
                Platform.runLater(() -> {
                    outputArea.appendText("Episode " + requestedEp + " not available. Nearest: " + nearestEp + "\n");
                    episodeSpinner.getValueFactory().setValue(nearestEp);
                });
                episode = nearestEp;
            }

            final int finalEpisode = episode;
            String streamUrl = apiService.getStreamUrl(selected.getId(), finalEpisode, quality, mode, msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n"))
            );

            Platform.runLater(() -> {
                if (streamUrl == null || streamUrl.isEmpty()) {
                    statusText.setText("Failed to get stream URL");
                    watchButton.setDisable(false);
                    return;
                }

                statusText.setText("Launching " + player + "...");
                outputArea.appendText("Stream URL resolved. Launching player...\n");

                Process playerProcess = apiService.launchPlayer(
                    streamUrl,
                    selected.getName() + " Episode " + finalEpisode,
                    player,
                    download
                );

                if (playerProcess != null) {
                    statusText.setText("Playing " + selected.getName() + " Ep " + finalEpisode);
                    watchButton.setDisable(false);

                    historyService.save(finalEpisode, selected.getId(), selected.getName());

                    new Thread(() -> {
                        try {
                            playerProcess.waitFor();
                            Platform.runLater(() -> statusText.setText("Playback finished"));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                } else {
                    statusText.setText("Failed to launch player");
                    watchButton.setDisable(false);
                }
            });
        }).start();
    }

    @FXML
    private void onHistory() {
        List<HistoryEntry> entries = historyService.load();
        if (entries.isEmpty()) {
            statusText.setText("No watch history found");
            return;
        }

        java.util.Collections.reverse(entries);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Watch History");
        dialog.setHeaderText("Select an entry to resume watching");

        ButtonType resumeType = new ButtonType("Resume", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteType = new ButtonType("Delete History", ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = ButtonType.CANCEL;
        dialog.getDialogPane().getButtonTypes().addAll(resumeType, deleteType, cancelType);
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/dark-theme.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("root-pane");

        ListView<HistoryEntry> listView = new ListView<>(FXCollections.observableArrayList(entries));
        listView.setPrefHeight(300);
        listView.setPrefWidth(400);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        listView.getSelectionModel().selectFirst();
        dialog.getDialogPane().setContent(listView);

        dialog.setResultConverter(btn -> {
            if (btn == resumeType) return "resume";
            if (btn == deleteType) return "delete";
            return null;
        });

        dialog.showAndWait().ifPresent(action -> {
            if ("delete".equals(action)) {
                historyService.clear();
                // Also try ani-cli -D
                try {
                    new ProcessBuilder("ani-cli", "-D").start();
                } catch (Exception ignored) {}
                statusText.setText("History deleted");
                outputArea.clear();
            } else if ("resume".equals(action)) {
                HistoryEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected == null) return;

                statusText.setText("Searching for " + selected.getAnimeName() + "...");
                outputArea.clear();
                outputArea.appendText("Resuming: " + selected.getAnimeName()
                    + " Episode " + selected.getEpisode() + "\n");

                new Thread(() -> {
                    List<AnimeResult> results = apiService.search(selected.getAnimeName(), null);
                    AnimeResult match = null;
                    for (AnimeResult r : results) {
                        if (r.getName().equalsIgnoreCase(selected.getAnimeName())) {
                            match = r;
                            break;
                        }
                    }
                    if (match == null && !results.isEmpty()) {
                        match = results.get(0);
                    }
                    final AnimeResult found = match;

                    Platform.runLater(() -> {
                        if (found == null) {
                            statusText.setText("Could not find " + selected.getAnimeName());
                            return;
                        }
                        searchResults.clear();
                        searchResults.add(found);
                        resultsList.getSelectionModel().select(found);
                        episodeSpinner.getValueFactory().setValue(selected.getEpisode());
                        onWatch();
                    });
                }).start();
            }
        });
    }

    @FXML
    private void onUpdate() {
        outputArea.clear();
        statusText.setText("Checking for ani-cli updates...");
        updateButton.setDisable(true);

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "bash", "-c",
                    "cd ~ && git clone --depth 1 https://github.com/pystardust/ani-cli.git ~/.ani-cli-new 2>&1 "
                    + "&& cp ~/.ani-cli-new/ani-cli ~/.local/bin/ani-cli 2>/dev/null || "
                    + "cp ~/.ani-cli-new/ani-cli /usr/local/bin/ani-cli 2>/dev/null; "
                    + "rm -rf ~/.ani-cli-new; echo Done"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String updateLine;
                    while ((updateLine = reader.readLine()) != null) {
                        final String msg = updateLine;
                        Platform.runLater(() -> outputArea.appendText(msg + "\n"));
                    }
                }
                p.waitFor();
                Platform.runLater(() -> statusText.setText("Update check complete"));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    outputArea.appendText("Update failed: " + e.getMessage() + "\n");
                    statusText.setText("Update failed");
                });
            } finally {
                Platform.runLater(() -> updateButton.setDisable(false));
            }
        }).start();
    }
}
