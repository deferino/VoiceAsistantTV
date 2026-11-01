# NOTICE

Copyright (C) 2026 VoiceAsistantTV contributors.

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3** (`LICENSE.txt`).
It is distributed WITHOUT ANY WARRANTY.

## Derivation

This project is a derivative work based on **Sayboard** (https://github.com/ElishaAz/Sayboard, GPL-3.0).

Taken from Sayboard:
- the general architecture idea (RecognitionService + AudioRecord loop + text-entry flow),
- the Gradle wrapper files and the license text.

Changed:
- Vosk (offline model) was removed completely; an OpenAI-compatible cloud STT client (`SttClient`) was added.
- Instead of a keyboard (IME), the app is built for Android TV: assistant-key takeover (VoiceInteractionService), a listening screen and typing through accessibility.
- androidx / Compose dependencies were removed; only Android framework APIs are used.
- Added: remote-friendly settings screen, SmartTube / Nuvio result cards, weather panel, English + Turkish UI, installer scripts.

## Bundled third-party files

| File | License |
|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Apache License 2.0 (Gradle Inc.) |
| `LICENSE.txt` | GNU GPL v3 text (Free Software Foundation) |

The APK contains no third-party libraries. The app icon, the TV banner and the weather icons (drawn with Canvas) were created for this project and are covered by the same GPL-3.0 license.

## Services used at run time

- Weather: [Open-Meteo.com](https://open-meteo.com/) (data CC BY 4.0; the free API is for non-commercial use).
- Movies and series: Cinemeta (Stremio) and, optionally, TMDB. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- Speech recognition: the OpenAI-compatible API chosen by the user (default Groq), used with the **user's own API key**; that provider's terms apply.
- YouTube search: keyless web search, or an optional YouTube Data API key.

## Trademarks

YouTube, SmartTube, Nuvio, Groq, OpenAI, TMDB, Stremio and Open-Meteo are trademarks or products of their respective owners. This project is not affiliated with, endorsed by or sponsored by them, and it does not include their software.

## Screenshots

`docs/img/` contains screenshots of this app's own interface. Thumbnails and posters visible in them belong to their owners and are shown only to illustrate the UI.
