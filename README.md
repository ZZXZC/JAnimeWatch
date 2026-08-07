# JAnimeWatch

A modern JavaFX GUI for [ani-cli](https://github.com/pystardust/ani-cli) — making anime streaming accessible to everyone, no terminal knowledge required.

## What is this?

JAnimeWatch wraps the powerful `ani-cli` command-line tool in a beautiful, easy-to-use graphical interface. Search for anime, browse episodes, and watch — all with clicks instead of commands.

## Features

- **Search** — Find any anime by title
- **Browse Episodes** — View and select from available episodes
- **Watch Instantly** — Launch playback in mpv or vlc
- **Download** — Save episodes for offline viewing
- **Quality Selection** — Choose 1080p, 720p, or 480p
- **Dub or Sub** — Toggle between dubbed and subtitled versions
- **Auto-Update** — Keep ani-cli up to date from the app

## Requirements

- **Java 21** or higher
- **Maven 3.8+**
- **ani-cli** installed and available in your PATH
- **mpv** or **vlc** media player

### Installing ani-cli

**Windows (Scoop):**
```bash
scoop bucket add extras
scoop install ani-cli fzf ffmpeg mpv
```

**Linux (Arch):**
```bash
yay -S ani-cli
```

**macOS (Homebrew):**
```bash
brew tap pystardust/ani-cli https://github.com/pystardust/ani-cli.git
brew install ani-cli
```

For other platforms, see the [ani-cli installation guide](https://github.com/pystardust/ani-cli#install).

## Quick Start

1. Clone the repository:
```bash
git clone https://github.com/yourusername/JAnimeWatch.git
cd JAnimeWatch
```

2. Run the app:
```bash
mvn clean compile exec:java
```

3. Search for an anime, select an episode, and enjoy!

## Building a JAR

```bash
mvn clean package
java -jar target/janimewatch-1.0-SNAPSHOT.jar
```

## How It Works

1. You type an anime name in the search bar
2. JAnimeWatch queries ani-cli and displays matching results
3. You select an anime, then choose an episode
4. The app launches your preferred player (mpv/vlc) and starts streaming

All the heavy lifting (scraping, stream resolution, playback) is handled by ani-cli — this GUI just makes it point-and-click.

## License

GPL-3.0 — same as ani-cli.
