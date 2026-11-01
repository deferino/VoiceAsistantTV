#!/system/bin/sh
# Runs on the TV (pushed by install.bat and scripts/install.bat).
# Makes the app the default assistant, speech-recognition service and accessibility service.
PKG=com.voiceasistanttv.stt
ASSIST=$PKG/$PKG.AssistService
RECOG=$PKG/$PKG.CloudRecognitionService
A11Y=$PKG/$PKG.InjectorService
BK=/data/local/tmp/voicetv_backup.txt

# Back up the old settings once (scripts/restore.bat restores them)
if [ ! -f "$BK" ]; then
  {
    echo "assistant=$(settings get secure assistant)"
    echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
    echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
    echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"
  } > "$BK"
  echo "backup saved: $BK"
fi

echo "microphone permission..."
pm grant $PKG android.permission.RECORD_AUDIO

echo "speech recognition service..."
settings put secure voice_recognition_service "$RECOG"

# TCL/MediaTek TV firmware blocks third-party apps from being started in the background via the "APP_AUTO_START" permission (default: ignore).
# Without it the system cannot bind to our assistant service and the key does nothing (logcat: callee_does't_have_OP_AUTO_START_permission).
echo "auto-start permission (TCL)..."
cmd appops set $PKG APP_AUTO_START allow 2>&1
cmd appops get $PKG APP_AUTO_START 2>&1 | head -1

echo "default assistant..."
cmd role add-role-holder --user 0 android.app.role.ASSISTANT $PKG 2>&1
echo "role exit code: $?"
# If the system failed to bind to the assistant service once, it remembers the failure (mBound=false); deleting the setting and writing it again resets the binding.
settings delete secure voice_interaction_service
sleep 2
settings put secure assistant "$ASSIST"
settings put secure voice_interaction_service "$ASSIST"
sleep 3

echo "accessibility service..."
CUR=$(settings get secure enabled_accessibility_services)
case "$CUR" in
  *"$A11Y"*) echo "already enabled" ;;
  ""|null) settings put secure enabled_accessibility_services "$A11Y" ;;
  *) settings put secure enabled_accessibility_services "$CUR:$A11Y" ;;
esac
settings put secure accessibility_enabled 1

echo "--- result ---"
echo "assistant=$(settings get secure assistant)"
echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"

echo "--- re-check after 4 s (does it persist?) ---"
sleep 4
echo "assistant=$(settings get secure assistant)"
echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"
dumpsys voiceinteraction | grep -E "mComponent=|Session service=|Recognition service=|mBound=" | head -6
if dumpsys voiceinteraction | grep -q "mService=android.service.voice"; then
  echo "STATUS: assistant service connected - key ready"
else
  echo "WARNING: could not bind to the assistant service. Restart the TV, then run install.bat (or scripts\\setup.bat) again."
fi
dumpsys role 2>/dev/null | grep -i -A5 "role.ASSISTANT" | head -14
