# JAnimeWatch

JavaFX GUI wrapper for ani-cli (command-line anime player).

## Build & Run

```bash
mvn clean compile exec:java
mvn clean package
java -jar target/janimewatch-1.0-SNAPSHOT.jar
```

## Architecture

- **Framework**: JavaFX 21+ with Maven
- **Entry point**: `com.janimewatch.App` (extends Application)
- **CLI wrapper**: `AniCliService` - executes ani-cli via ProcessBuilder
- **UI**: FXML in `src/main/resources/fxml/`, styled with CSS

## Conventions

- FXML uses `fx:controller` binding
- CSS: `.search-bar`, `.results-list` (BEM-like)
- All ani-cli parsing in `AniCliService`, never in controllers
- Use `Platform.runLater()` for UI updates from background threads

## Prerequisites

- Java 21+, Maven 3.8+
- `ani-cli` installed and in PATH
- mpv or vlc for playback
