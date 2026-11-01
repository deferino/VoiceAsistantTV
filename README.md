# Voice Search (STT) — a lightweight, cloud-based speech-to-text app for Android TV

![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue) ![Android TV](https://img.shields.io/badge/Android%20TV-6.0%2B-green) ![APK](https://img.shields.io/badge/APK-~190%20KB-lightgrey)

A small Android TV app that takes over the remote's **assistant / microphone key**: you speak, the audio is transcribed by a
cloud speech-to-text API (Groq `whisper-large-v3-turbo` by default; any OpenAI-compatible endpoint works), and the text is
typed into the focused field, searched in **SmartTube** and **Nuvio** (Google-Assistant-style result cards), or — for weather
questions — shown as a **weather panel**. Derived from the architecture of [Sayboard](https://github.com/ElishaAz/Sayboard) (GPL-3.0);
there is no on-device speech model, RAM/CPU use is tiny, and the microphone is only open while you speak.

The app, the installer and the voice commands work in **English and Turkish**
(Settings → *Interface language / Arayüz dili*; "Automatic" follows the TV's system language).

<p align="center"><img src="docs/img/language.png" width="49%" alt="Interface language picker"></p>

> Developed and tested on a **TCL Smart TV Pro (Android 11)** only. Provided **as is**, without warranty (GPL-3.0).
> Not affiliated with SmartTube, Nuvio, Groq, OpenAI, TMDB, YouTube or Open-Meteo.

<p align="center">
  <img src="docs/img/weather.png" width="49%" alt="Weather panel (“weather in london”)">
  <img src="docs/img/results.png" width="49%" alt="Search results: SmartTube / YouTube row and Nuvio movies row">
</p>

## What it does

- **The remote's assistant/microphone key opens this app** (it is set as the default assistant).
- Press the key → speak → on silence (or OK) the speech is transcribed → the text is **typed into the focused search/text field**.
  If no field is found, the text is copied to the clipboard and shown on screen.
- Other apps that ask for speech recognition (`SpeechRecognizer` / `RECOGNIZE_SPEECH`) are served too.
- **Searches SmartTube and Nuvio together** (changeable in Settings):
  - *Search both* (default): after you speak, two rows of horizontal cards appear: **SmartTube · YouTube** videos on top (thumbnail,
    duration, channel, views, date) and **Nuvio · Movies / Series** below (poster, year, rating, genre, overview). Left/Right: browse,
    Up/Down: switch rows, OK: open (videos play in SmartTube, titles open in Nuvio). Buttons at the bottom: *Search all results in
    SmartTube* and *Type into the focused field*.
  - *Always SmartTube* opens SmartTube's search results directly; *Always Nuvio* shows only the Nuvio row; *Only type into the field* is the plain dictation mode.
  - The YouTube row uses a keyless web search; you can enter a YouTube Data API key in Settings. Nuvio has no search deep link, so titles come
    from Cinemeta (keyless); with a TMDB key you also get localized titles.
- **Weather**: say "weather in London", "what's the weather like in Paris tomorrow", "will it rain tomorrow", "how many degrees is it in
  New York", "day after tomorrow weather in Berlin" (Turkish: "İstanbul hava durumu", "yarın Ankara'da hava nasıl olacak" …) and **only the weather panel**
  opens (current conditions, hourly and 7-day forecast; Open-Meteo, keyless). "Tomorrow" / "day after tomorrow" show that day's summary.
  If you don't name a city ("weather"), the default city from Settings is used (London in English, İstanbul in Turkish — change it in Settings).
  Everything that is not a weather question is searched as before; if the spoken place isn't a real place, it falls back to a normal search.
  Fahrenheit and mph are used when the interface is English and the TV's region is the US. The panel has *Close* and *Search in YouTube / Nuvio* buttons.
- **Choose the SmartTube / Nuvio package** (Settings › Search target › *SmartTube package*, *Nuvio package*): *Automatic* (default), a preset list,
  or *Enter another package name…*. Presets: SmartTube `org.smarttube.stable`, SmartTube Beta `org.smarttube.beta`; Nuvio `com.nuvio.tv` (original),
  Nuvio Arc `com.nuvio.tv.test`, Nuvio Native Legacy `space.nuvio.nativelegacy`, Nuvio Reshaped `com.nuvioreshaped.tv`. Installed ones are marked ✓.
- **Fully remote-controlled** (arrows, OK, Back). No touch needed.
- **API settings live inside the app**: provider (Groq / OpenAI / your own server), address, key, model, speech language, silence length, etc.
  (*Speech language* is the language Whisper listens for; it is separate from the interface language.)

## Remote control

| Key | In Settings | On the listening screen |
|---|---|---|
| Up / Down | Move between rows | — |
| OK | Open / change the row | Finish speaking now and transcribe |
| Assistant key | (main function) start listening | Finish speaking now |
| Back | Exit | Cancel |

## Quick install (Windows — recommended)

1. Download **`VoiceSearch-installer.zip`** from the [Releases](../../releases) page and extract it into one folder (keep the files together).
2. **One-time preparation on the TV:** Settings → Device Preferences → About → press OK on *Build* 7 times (developer mode).
   Then Developer options → turn **USB debugging** (and **Network debugging**, if present) *ON*. Note the TV's IP address (Settings → Network & Internet).
3. Double-click **`install.bat`** (the installer's language follows Windows) and enter the TV's IP address. On the first
   connection the TV asks "Allow USB debugging?" — choose **Always allow**. (The computer and the TV must be on the same router.)
4. Optionally paste your Groq API key ([get a free key](https://console.groq.com/keys)); if you leave it empty you can enter it later in the app's Settings.
5. When you see "SETUP COMPLETE", press the **assistant / microphone key** on the remote and speak.

`install.bat` does this: offers to download adb from Google's official address if needed → connects to the TV → installs (or updates) the APK →
grants the microphone permission → sets the app as default assistant, speech-recognition and accessibility service → (optionally) sends the API
key to the TV → opens the Settings screen. Log: `install.log`. To undo: `install.bat uninstall` (restores the previous assistant settings and removes the app).

- Unattended: `install.bat auto` (the TV address must be in `tv.txt` or the `VT_TV` environment variable; `VT_KEY` for the API key).
- Force the installer language with `VT_LANG=en` or `VT_LANG=tr`.
- If the installer fails, read `install.log`. Most common causes: debugging is off on the TV, a wrong IP, or the TV's permission prompt was not accepted.
- The API key is sent over adb to the app's internal settings receiver (`ConfigReceiver`); it requires `android.permission.DUMP`, so only adb/shell can send it
  and no other app on the TV can change settings. The key is never written to logs.
- The release APK is signed with the debug key (personal use). If you don't want to trust a prebuilt APK, build it yourself (`scripts\release.bat` → `dist\`).

## Build from source (developers)

You need **Android Studio** (comes with the SDK and platform-tools) and a computer on the same network as the TV.

1. Enable network debugging on the TV (see above). The port is usually `5555`.
2. Copy `scripts\tv.example.txt` to `scripts\tv.txt` and write `IP:5555` in it (e.g. `192.168.1.50:5555`).
3. API key (free): get a Groq key at <https://console.groq.com/keys>. Copy `scripts\config.example.json` to `config.json` in the project root and put
   it in `apiKey` (the key is not embedded in the APK; it is sent to the TV at install time and the file is deleted once imported). You can also skip this and enter the key in the app.
4. Run `scripts\build.bat` → `app\build\outputs\apk\debug\app-debug.apk`
5. With the TV on, run `scripts\install.bat`: it installs the APK, grants the microphone permission, makes the app the **default assistant + speech-recognition + accessibility service** and opens Settings.

Translations live in `app/src/main/res/values/strings.xml` (English, the default) and `values-tr/strings.xml` (Turkish); `Lang.kt` picks the language at run time.

### Test

1. Open the app ("Voice Search (STT)" in the app list; "Sesli Arama (STT)" on a Turkish TV).
2. *Test the connection* → you should see "OK".
3. *Microphone / listening test* → speak; the level should move.
4. Go to a search box, press the **remote's microphone key** and speak.

### Troubleshooting

| Symptom | What to do |
|---|---|
| Pressing the mic key **does nothing** (TCL / MediaTek TV) | Root cause: the TV firmware blocks 3rd-party apps from being started in the background through the `APP_AUTO_START` permission (default *ignore*); the system can't bind to our assistant service and remembers the failure. `scripts\setup.bat` grants the permission (`cmd appops set … APP_AUTO_START allow`) and resets the binding. It persists across reboots; the script re-applies it on every install. |
| The mic key still opens Google Assistant | Run `scripts\keys.bat` and press the key; see whether it appears in `logs\keys.log`. Also try *Remote key test* in the app. Then `scripts\logcat.bat clear`, press the key, `scripts\logcat.bat` and read `logs\logcat.log`. |
| The remote's microphone is silent (level always 0) | Some remotes send audio over Bluetooth/a private channel and don't expose it to 3rd-party apps. Try *Audio source* and *Microphone device* in Settings; otherwise you need a USB or phone microphone. |
| Text isn't typed, it is copied to the clipboard | The accessibility service may be off (see the status row on the main screen) or the app's search box is non-standard. |
| "No connection / 401" | Check the address, key and model in Settings. |
| Filing an issue? | Attach `install.log` / `logs\*.log` only **after replacing your TV's IP address with `x.x.x.x`**, and never paste an API key (or any part of one) anywhere public. |
| Go back to the original state | `scripts\restore.bat` — restores the assistant / recognition / accessibility settings from before the install. |

## Manual commands (if you prefer not to use the scripts)

```
adb connect IP:5555
adb install -r -g app\build\outputs\apk\debug\app-debug.apk
adb shell pm grant com.voiceasistanttv.stt android.permission.RECORD_AUDIO
adb shell settings put secure voice_recognition_service com.voiceasistanttv.stt/com.voiceasistanttv.stt.CloudRecognitionService
adb shell cmd appops set com.voiceasistanttv.stt APP_AUTO_START allow
adb shell cmd role add-role-holder android.app.role.ASSISTANT com.voiceasistanttv.stt
adb shell settings delete secure voice_interaction_service
adb shell settings put secure assistant com.voiceasistanttv.stt/com.voiceasistanttv.stt.AssistService
adb shell settings put secure voice_interaction_service com.voiceasistanttv.stt/com.voiceasistanttv.stt.AssistService
adb shell settings put secure enabled_accessibility_services com.voiceasistanttv.stt/com.voiceasistanttv.stt.InjectorService
adb shell settings put secure accessibility_enabled 1
```

## API providers

It uses `POST {address}/audio/transcriptions` (OpenAI compatible), so it works with:

- **Groq** (default, free tier): `https://api.groq.com/openai/v1`, model `whisper-large-v3-turbo`
- **OpenAI**: `https://api.openai.com/v1`, model `gpt-4o-mini-transcribe` / `whisper-1`
- **Your own server** (e.g. faster-whisper-server on your LAN): `http://192.168.1.10:8000/v1`, the key may be empty

## Privacy

- **Audio**: your speech is sent to the API provider you choose (default Groq) and nowhere else; the app doesn't store it. The microphone is only used while the listening screen is open. Check the provider's data-use terms for free tiers.
- **Recognized text**: searched words go to YouTube (keyless web search), Cinemeta / TMDB (movies/series) and — for weather questions — Open-Meteo (the city name only).
- **Connection warm-up**: when listening starts, the app sends empty `HEAD` requests to the search servers (YouTube, TMDB, Groq) so results arrive faster. They contain no speech or personal data; the servers only see that a connection was made (your IP address).
- The **accessibility service** doesn't read or record screen content; after you speak it only finds the focused text field and types the text there.
- API keys are kept only in the app's settings on the TV (not embedded in the APK, not logged).
- No analytics, ads or tracking.

## Credits

- Weather data: **[Open-Meteo.com](https://open-meteo.com/)** (CC BY 4.0), also credited in the weather panel.
- Movie/series data: Cinemeta (Stremio) and optionally TMDB. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- Video search comes from YouTube; results open in SmartTube. SmartTube and Nuvio are separate apps; this project is not affiliated with them and does not include them — you install them yourself.
- Architecture idea: [Sayboard](https://github.com/ElishaAz/Sayboard) (GPL-3.0), see `NOTICE.md`.

## Optional: use with a keyboard

If the default keyboard is *Simple Keyboard TV* (`rkr.simplekeyboard.inputmethod.tv`), pressing the assistant key first sends the keyboard an ordered broadcast
`rkr.simplekeyboard.inputmethod.tv.action.VOICE_KEY` (waits at most 0.7 s). If the keyboard supports it, it starts its own voice typing and the text is typed without the field losing focus.
If there is no keyboard or it doesn't answer, the normal listening screen opens. Without such a keyboard nothing changes.

## Performance

- The release APK is about **190 KB** (both languages included) (R8 + resource shrinking). No third-party libraries — only the Android framework.
- While you speak, connections to the search servers are warmed up in the background (DNS + TLS, ~0.5 s). When speech ends, the searches use ready connections: in my measurement (TCL TV, my own network) a movie search returned in ~40 ms with a warm connection; a cold first request can take hundreds of ms to a few seconds. Results depend on your network.
- The microphone and network are only used when you press the key; the app does nothing in the background on its own.

## Layout

```
app/src/main/java/com/voiceasistanttv/stt/
  Transcriber.kt          microphone + silence detection + upload
  SttClient.kt            OpenAI-compatible multipart client
  AssistService.kt        assistant key (VoiceInteractionService)
  AppLauncher.kt          find and open SmartTube / Nuvio
  MediaSearch.kt          movie/series search (Cinemeta / TMDB)
  YouTubeSearch.kt        YouTube video search (web / Data API)
  Weather.kt              weather-question parsing (city/day, EN+TR) + Open-Meteo client
  Prewarm.kt              connection warm-up while listening
  NetLog.kt               network call timing (logcat, tag VoiceTV)
  ConfigReceiver.kt       lets the installer send settings (API key) over adb (DUMP-protected)
  KeyboardHandoff.kt      hand dictation to Simple Keyboard TV if present
  WeatherUi.kt            weather panel cards + Canvas-drawn weather icons
  ResultCards.kt          result cards + small image loader
  AssistActivity.kt       listening screen
  CloudRecognitionService.kt  SpeechRecognizer service
  InjectorService.kt      typing into the focused field (accessibility)
  MainActivity.kt         remote-friendly settings screen
  KeyTestActivity.kt      remote key test
  Lang.kt                 interface language (Automatic / Türkçe / English) + string lookup
app/src/main/res/values/strings.xml      English strings (default)
app/src/main/res/values-tr/strings.xml   Turkish strings
docs/img/                 README screenshots
install.bat               end-user installer (adb + APK + assistant assignment; `install.bat uninstall` to undo; English/Turkish messages)
scripts/                  all (build+install+test) / update / build / install / setup / restore / logcat / keys / diag / shot (TV screenshot)
scripts/release.bat       release APK + dist\VoiceSearch-installer.zip (the package to upload to GitHub Releases)
```

## License

This project is free software under the **GNU General Public License v3** — see [`LICENSE.txt`](LICENSE.txt). You may use, study, modify and
redistribute it, provided derived works stay under the same license with their source available. It comes **without any warranty**.
Copyright (C) 2026 the VoiceAsistantTV contributors.

| Part | License / terms |
|---|---|
| This project (source, scripts, translations, app icon and TV banner — drawn for this project) | GPL-3.0 |
| [Sayboard](https://github.com/ElishaAz/Sayboard) by ElishaAz — architecture idea this project is derived from | GPL-3.0 |
| Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) | Apache License 2.0 (Gradle Inc.) |
| Third-party libraries inside the APK | none — only Android framework APIs are used |
| Weather data — [Open-Meteo.com](https://open-meteo.com/) | CC BY 4.0 (credited in the weather panel). Its free API is meant for non-commercial use |
| Movie/series data — Cinemeta (Stremio), TMDB | their own terms; *This product uses the TMDB API but is not endorsed or certified by TMDB.* |
| Speech recognition — Groq / OpenAI / your own server | **your** API key and **that provider's** terms of service apply |
| Screenshots in `docs/img/` | UI of this app; the thumbnails and posters in them belong to their owners and are shown only to illustrate the interface |

SmartTube, Nuvio, YouTube, Groq, OpenAI, TMDB and Open-Meteo are trademarks/products of their respective owners; this project is not affiliated with
them and does not include their software. See [`NOTICE.md`](NOTICE.md) for details.
