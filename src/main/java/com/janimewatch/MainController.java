package com.janimewatch;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

public class MainController {

    // Window resize edge direction bit flags
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int TOP = 4;
    private static final int BOTTOM = 8;
    private static final double RESIZE_MARGIN = 6;

    @FXML private StackPane rootPane;
    @FXML private ImageView bgImage1;
    @FXML private ImageView bgImage2;
    @FXML private SVGPath maximizePath;
    @FXML private SVGPath restorePath;
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
    @FXML private CheckBox skipCheck;
    @FXML private Button watchButton;
    @FXML private Button historyButton;
    @FXML private Button updateButton;
    @FXML private TextArea outputArea;
    @FXML private Text statusText;

    private final AllAnimeService apiService = new AllAnimeService();
    private final AniCliService aniCliService = new AniCliService();
    private final HistoryService historyService = new HistoryService();
    private final ObservableList<AnimeResult> searchResults = FXCollections.observableArrayList();
    private List<Integer> currentEpisodes;

    private boolean aniSkipOk;

    private Stage stage;

    // Title bar drag offsets
    private double dragOffsetX;
    private double dragOffsetY;

    // Window resize tracking
    private boolean resizing;
    private int resizeDir;
    private double startMouseX;
    private double startMouseY;
    private double startStageX;
    private double startStageY;
    private double startStageW;
    private double startStageH;

    @FXML
    public void initialize() {
        // Bind anime background images to window size (stretch to cover)
        bgImage1.fitWidthProperty().bind(rootPane.widthProperty());
        bgImage1.fitHeightProperty().bind(rootPane.heightProperty());
        bgImage2.fitWidthProperty().bind(rootPane.widthProperty());
        bgImage2.fitHeightProperty().bind(rootPane.heightProperty());

        // Stage is only available once a window is bound to the scene
        rootPane.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            scene.windowProperty().addListener((wObs, oldWin, win) -> {
                if (win instanceof Stage s && stage == null) {
                    stage = s;

                    // Keep maximize icon in sync with window state
                    stage.maximizedProperty().addListener((sObs, wasMax, isMax) -> {
                        maximizePath.setVisible(!isMax);
                        restorePath.setVisible(isMax);
                    });
                }
            });
        });

        // Edge resize support for the undecorated window
        rootPane.setOnMouseMoved(this::updateResizeCursor);
        rootPane.setOnMousePressed(this::onResizePressed);
        rootPane.setOnMouseDragged(this::onResizeDragged);
        rootPane.setOnMouseReleased(this::onResizeReleased);
        rootPane.setOnMouseExited(e -> {
            if (!resizing) {
                rootPane.setCursor(Cursor.DEFAULT);
            }
        });

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

    private static class Deps {
        boolean curl;
        boolean mpv;
        boolean vlc;
        boolean aniCli;
        boolean gitBash;
        boolean aniSkip;
        boolean scoop;

        boolean hasPlayer() {
            return mpv || vlc;
        }

        boolean allRequired() {
            return curl && hasPlayer() && aniCli && gitBash;
        }

        String missingList() {
            StringBuilder sb = new StringBuilder();
            if (!curl) sb.append("- curl\n");
            if (!hasPlayer()) sb.append("- a video player (mpv or vlc)\n");
            if (!aniCli) sb.append("- ani-cli\n");
            if (!gitBash) sb.append("- Git Bash\n");
            return sb.toString();
        }
    }

    private void checkDependencies() {
        new Thread(() -> {
            Deps d = detect();
            Platform.runLater(() -> applyDeps(d, true));
        }).start();
    }

    private Deps detect() {
        Deps d = new Deps();
        d.curl = apiService.isCurlInstalled();
        d.mpv = apiService.isPlayerInstalled("mpv");
        d.vlc = apiService.isPlayerInstalled("vlc");
        d.aniCli = aniCliService.isAniCliInstalled();
        d.gitBash = aniCliService.isGitBashAvailable();
        d.aniSkip = aniCliService.isAniSkipInstalled();
        d.scoop = aniCliService.isScoopAvailable();
        return d;
    }

    private void applyDeps(Deps d, boolean askInstall) {
        aniSkipOk = d.aniSkip;

        if (d.allRequired()) {
            String player = d.mpv ? "mpv" : "vlc";
            statusText.setText("Ready (" + player + " detected)");
            setControlsDisabled(false);
            return;
        }

        setControlsDisabled(true);
        if (askInstall) {
            showMissingDependenciesDialog(d);
        } else {
            statusText.setText("Dependencies still missing - install failed or incomplete");
            outputArea.appendText("Still missing:\n" + d.missingList());
        }
    }

    private void showMissingDependenciesDialog(Deps d) {
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle("Missing Dependencies");
        error.setHeaderText("Required dependencies are not installed");
        error.setContentText("The following required dependencies are missing:\n\n" + d.missingList());
        styleDialog(error);
        error.showAndWait();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Install Dependencies");
        confirm.setHeaderText("Do you want to install the required dependencies for this program?");
        String note = d.scoop ? ""
            : "\nNote: scoop was not found. Install it from https://scoop.sh first.\n";
        confirm.setContentText("Installation uses Git Bash and Scoop.\n\nMissing:\n"
            + d.missingList() + note);
        ButtonType yesType = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
        ButtonType noType = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yesType, noType);
        styleDialog(confirm);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == yesType) {
            startDependencyInstall();
        } else {
            statusText.setText("Missing dependencies - install manually and restart");
        }
    }

    private void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(
            getClass().getResource("/styles/dark-theme.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("root-pane");
    }

    private void startDependencyInstall() {
        outputArea.clear();
        statusText.setText("Installing dependencies via Scoop...");
        setControlsDisabled(true);

        new Thread(() -> {
            Process p = aniCliService.installDependencies(msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n")));
            if (p == null) {
                Platform.runLater(() -> {
                    outputArea.appendText("Failed to start the installer\n");
                    statusText.setText("Dependency install failed");
                });
                return;
            }
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> {
                outputArea.appendText("Install finished - verifying...\n");
                statusText.setText("Verifying installation...");
                new Thread(() -> {
                    Deps after = detect();
                    Platform.runLater(() -> applyDeps(after, false));
                }).start();
            });
        }).start();
    }

    private void setControlsDisabled(boolean disabled) {
        searchField.setDisable(disabled);
        searchButton.setDisable(disabled);
        watchButton.setDisable(disabled);
        historyButton.setDisable(disabled);
        updateButton.setDisable(disabled);
        episodeSpinner.setDisable(disabled);
        mpvRadio.setDisable(disabled);
        vlcRadio.setDisable(disabled);
        qualityBox.setDisable(disabled);
        dubCheck.setDisable(disabled);
        downloadCheck.setDisable(disabled);
        skipCheck.setDisable(disabled);
    }

    // ===== Custom Title Bar / Window Controls =====

    @FXML
    private void onTitleBarPressed(MouseEvent e) {
        if (stage.isMaximized()) return;
        dragOffsetX = e.getScreenX() - stage.getX();
        dragOffsetY = e.getScreenY() - stage.getY();
    }

    @FXML
    private void onTitleBarDragged(MouseEvent e) {
        if (stage.isMaximized() || resizing) return;
        stage.setX(e.getScreenX() - dragOffsetX);
        stage.setY(e.getScreenY() - dragOffsetY);
    }

    @FXML
    private void onTitleBarDoubleClick(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            toggleMaximize();
        }
    }

    @FXML
    private void onMinimize() {
        stage.setIconified(true);
    }

    @FXML
    private void onMaximizeToggle() {
        toggleMaximize();
    }

    @FXML
    private void onClose() {
        stage.close();
    }

    private void toggleMaximize() {
        stage.setMaximized(!stage.isMaximized());
        boolean isMax = stage.isMaximized();
        maximizePath.setVisible(!isMax);
        restorePath.setVisible(isMax);
    }

    // ===== Edge Resize for Undecorated Window =====

    private int resizeDirection(MouseEvent e) {
        if (stage.isMaximized()) return 0;
        double sx = e.getSceneX();
        double sy = e.getSceneY();
        double w = rootPane.getWidth();
        double h = rootPane.getHeight();
        int dir = 0;
        if (sx <= RESIZE_MARGIN) dir |= LEFT;
        if (sx >= w - RESIZE_MARGIN) dir |= RIGHT;
        if (sy <= RESIZE_MARGIN) dir |= TOP;
        if (sy >= h - RESIZE_MARGIN) dir |= BOTTOM;
        return dir;
    }

    private void updateResizeCursor(MouseEvent e) {
        if (resizing) return;
        int dir = resizeDirection(e);
        if (dir == 0) {
            rootPane.setCursor(Cursor.DEFAULT);
            return;
        }
        boolean x = (dir & (LEFT | RIGHT)) != 0;
        boolean y = (dir & (TOP | BOTTOM)) != 0;
        Cursor cursor;
        if (x && y) {
            boolean left = (dir & LEFT) != 0;
            boolean top = (dir & TOP) != 0;
            cursor = left
                    ? (top ? Cursor.NW_RESIZE : Cursor.SW_RESIZE)
                    : (top ? Cursor.NE_RESIZE : Cursor.SE_RESIZE);
        } else if (x) {
            cursor = (dir & LEFT) != 0 ? Cursor.W_RESIZE : Cursor.E_RESIZE;
        } else {
            cursor = (dir & TOP) != 0 ? Cursor.N_RESIZE : Cursor.S_RESIZE;
        }
        rootPane.setCursor(cursor);
    }

    private void onResizePressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || stage.isMaximized()) return;
        int dir = resizeDirection(e);
        if (dir != 0) {
            resizing = true;
            resizeDir = dir;
            startMouseX = e.getScreenX();
            startMouseY = e.getScreenY();
            startStageX = stage.getX();
            startStageY = stage.getY();
            startStageW = stage.getWidth();
            startStageH = stage.getHeight();
        }
    }

    private void onResizeDragged(MouseEvent e) {
        if (!resizing) return;
        double dx = e.getScreenX() - startMouseX;
        double dy = e.getScreenY() - startMouseY;
        double nx = startStageX;
        double ny = startStageY;
        double nw = startStageW;
        double nh = startStageH;

        if ((resizeDir & LEFT) != 0) {
            nw = startStageW - dx;
            nx = startStageX + dx;
            if (nw < stage.getMinWidth()) {
                nx = startStageX + startStageW - stage.getMinWidth();
                nw = stage.getMinWidth();
            }
        } else if ((resizeDir & RIGHT) != 0) {
            nw = startStageW + dx;
            if (nw < stage.getMinWidth()) nw = stage.getMinWidth();
        }

        if ((resizeDir & TOP) != 0) {
            nh = startStageH - dy;
            ny = startStageY + dy;
            if (nh < stage.getMinHeight()) {
                ny = startStageY + startStageH - stage.getMinHeight();
                nh = stage.getMinHeight();
            }
        } else if ((resizeDir & BOTTOM) != 0) {
            nh = startStageH + dy;
            if (nh < stage.getMinHeight()) nh = stage.getMinHeight();
        }

        stage.setX(nx);
        stage.setY(ny);
        stage.setWidth(nw);
        stage.setHeight(nh);
    }

    @FXML
    private void onResizeReleased(MouseEvent e) {
        resizing = false;
        rootPane.setCursor(Cursor.DEFAULT);
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
        final boolean dub = dubCheck.isSelected();
        final boolean download = downloadCheck.isSelected();
        final boolean skip = skipCheck.isSelected();
        final String player = vlcRadio.isSelected() ? "vlc" : "mpv";

        outputArea.clear();
        watchButton.setDisable(true);

        if (skip && aniSkipOk) {
            runAniCliPath(selected, quality, dub, player);
            return;
        }

        statusText.setText("Loading episode " + episodeSpinner.getValue() + "...");

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
            AllAnimeService.StreamResult stream = apiService.getStreamUrl(
                selected.getId(), finalEpisode, quality, dub ? "dub" : "sub", msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n"))
            );

            Platform.runLater(() -> {
                if (stream == null || stream.url == null || stream.url.isEmpty()) {
                    statusText.setText("Failed to get stream URL");
                    watchButton.setDisable(false);
                    return;
                }

                statusText.setText("Launching " + player + "...");
                outputArea.appendText("Stream URL resolved. Launching player...\n");

                Process playerProcess = apiService.launchPlayer(
                    stream.url,
                    selected.getName() + " Episode " + finalEpisode,
                    player,
                    download,
                    stream.referrer,
                    stream.subtitle
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

    private void runAniCliPath(AnimeResult selected, String quality, boolean dub, String player) {
        final int episode = episodeSpinner.getValue();
        final String query = searchField.getText().trim();
        final int selectNth = resultsList.getSelectionModel().getSelectedIndex() + 1;

        statusText.setText("Launching ani-cli (skip intro) Ep " + episode + "...");
        outputArea.appendText("Playing via ani-cli with intro skipping...\n");

        WatchOptions options = new WatchOptions();
        options.setDub(dub);
        options.setQuality(quality);
        options.setPlayer(player);
        options.setSkip(true);

        new Thread(() -> {
            Process p = aniCliService.play(options, episode, query, selectNth, msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n"))
            );

            if (p == null) {
                Platform.runLater(() -> {
                    statusText.setText("Failed to launch ani-cli");
                    watchButton.setDisable(false);
                });
                return;
            }

            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Platform.runLater(() -> {
                historyService.save(episode, selected.getId(), selected.getName());
                statusText.setText("Playback finished");
                watchButton.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void onSkipToggled() {
        if (!skipCheck.isSelected()) return;

        if (!aniSkipOk) {
            skipCheck.setSelected(false);
            promptInstallAniSkip();
            return;
        }

        if (downloadCheck.isSelected()) {
            skipCheck.setSelected(false);
            outputArea.appendText("Skip Intro is disabled while Download is selected\n");
            return;
        }

        if (vlcRadio.isSelected()) {
            mpvRadio.setSelected(true);
            outputArea.appendText("Intro skip requires mpv - player switched to mpv\n");
        }
    }

    @FXML
    private void onDownloadToggled() {
        if (downloadCheck.isSelected() && skipCheck.isSelected()) {
            skipCheck.setSelected(false);
            outputArea.appendText("Download and Skip Intro cannot be combined - Skip disabled\n");
        }
    }

    private void promptInstallAniSkip() {
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setTitle("ani-skip Not Installed");
        warn.setHeaderText("ani-skip not installed");
        warn.setContentText("Intro skipping requires ani-skip. "
            + "It is a Git Bash script installed alongside mpv.");
        styleDialog(warn);
        warn.showAndWait();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Install ani-skip");
        confirm.setHeaderText("Install ani-skip now?");
        confirm.setContentText("Run the official one-line installer using Git Bash?");
        ButtonType yesType = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
        ButtonType noType = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yesType, noType);
        styleDialog(confirm);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == yesType) {
            installAniSkip();
        }
    }

    private void installAniSkip() {
        outputArea.clear();
        statusText.setText("Installing ani-skip...");

        new Thread(() -> {
            Process p = aniCliService.installAniSkip(msg ->
                Platform.runLater(() -> outputArea.appendText(msg + "\n"))
            );

            if (p == null) {
                Platform.runLater(() -> statusText.setText("Failed to start ani-skip installer"));
                return;
            }

            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Platform.runLater(() -> {
                aniSkipOk = aniCliService.isAniSkipInstalled();
                if (aniSkipOk) {
                    skipCheck.setSelected(true);
                    statusText.setText("ani-skip installed - Skip Intro enabled");
                } else {
                    statusText.setText("ani-skip not detected after install");
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
