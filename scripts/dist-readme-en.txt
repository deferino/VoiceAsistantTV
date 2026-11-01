VOICE SEARCH (STT) - ANDROID TV SETUP
=====================================

Press the assistant / microphone key on your TV remote and this app opens: you speak, and the
text is typed into the focused field, or searched in SmartTube / Nuvio. Say "weather in London"
(or "what's the weather tomorrow") and a weather panel appears instead.
The app speaks Turkish and English (Settings - Interface language / Arayuz dili).

PREPARE (one time, on the TV)
1. Settings - Device Preferences - About - press OK on "Build" 7 times (developer mode turns on).
2. Settings - Device Preferences - Developer options - turn ON "USB debugging" and, if you see it,
   "Network debugging".
3. Note the TV's IP address (Settings - Network and Internet - your connected network).
4. Optional: get a free Groq API key at https://console.groq.com/keys

INSTALL (on a Windows 10 / 11 computer on the same network as the TV)
1. Extract everything in this zip into one folder (keep all files together).
2. Double-click install.bat   (kurulum.bat is the same installer; the language follows Windows).
3. Type the TV's IP address. On the first connection the TV may ask "Allow USB debugging?" -
   tick "Always allow" and press OK.
4. Paste your Groq key (leave it empty to enter it later in the app).
5. When you see "SETUP COMPLETE", press the assistant key on the remote.

UNDO / GO BACK
From a command prompt: install.bat uninstall
(Restores the assistant settings the TV had before, and removes the app.)

IF SOMETHING GOES WRONG
- kurulum.log shows what was done.
- If the key does nothing, restart the TV and run install.bat again.
- Your speech is sent to the API provider you choose (default: Groq). The microphone is only
  used while the listening screen is open.

License: GPL-3.0. Source code and details are on the GitHub page.
