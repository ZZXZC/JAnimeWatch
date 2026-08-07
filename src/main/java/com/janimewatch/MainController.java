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
    @FXML private Button updateButton;
    @FXML private TextArea outputArea;
    @FXML private Text statusText;

    private final AllAnimeService apiService = new AllAnimeService();
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

        int episode = episodeSpinner.getValue();
        String quality = qualityBox.getValue();
        String mode = dubCheck.isSelected() ? "dub" : "sub";
        boolean download = downloadCheck.isSelected();
        String player = vlcRadio.isSelected() ? "vlc" : "mpv";

        outputArea.clear();
        statusText.setText("Loading episode " + episode + "...");
        watchButton.setDisable(true);

        new Thread(() -> {
            // Fetch episodes list to validate
            if (currentEpisodes == null || currentEpisodes.isEmpty()) {
                currentEpisodes = apiService.getEpisodes(selected.getId(), msg ->
                    Platform.runLater(() -> outputArea.appendText(msg + "\n"))
                );
            }

            if (!currentEpisodes.contains(episode)) {
                // Find nearest available episode
                int nearest = currentEpisodes.isEmpty() ? 1 : currentEpisodes.get(0);
                for (int ep : currentEpisodes) {
                    if (Math.abs(ep - episode) < Math.abs(nearest - episode)) {
                        nearest = ep;
                    }
                }
                int finalNearest = nearest;
                Platform.runLater(() -> {
                    outputArea.appendText("Episode " + episode + " not available. Nearest: " + finalNearest + "\n");
                    episodeSpinner.getValueFactory().setValue(finalNearest);
                });
                episode = nearest;
            }

            String streamUrl = apiService.getStreamUrl(selected.getId(), episode, quality, mode, msg ->
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
                    selected.getName() + " Episode " + episode,
                    player,
                    download
                );

                if (playerProcess != null) {
                    statusText.setText("Playing " + selected.getName() + " Ep " + episode);
                    watchButton.setDisable(false);

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
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Platform.runLater(() -> outputArea.appendText(line + "\n"));
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
