#!/system/bin/sh
# TV üzerinde çalışır (scripts/install.bat tarafından gönderilir).
# Uygulamayı varsayılan asistan, konuşma tanıma servisi ve erişilebilirlik servisi olarak atar.
PKG=com.voiceasistanttv.stt
ASSIST=$PKG/$PKG.AssistService
RECOG=$PKG/$PKG.CloudRecognitionService
A11Y=$PKG/$PKG.InjectorService
BK=/data/local/tmp/voicetv_backup.txt

# Eski ayarları bir kez yedekle (scripts/restore.bat bunları geri yükler)
if [ ! -f "$BK" ]; then
  {
    echo "assistant=$(settings get secure assistant)"
    echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
    echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
    echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"
  } > "$BK"
  echo "yedek alindi: $BK"
fi

echo "mikrofon izni..."
pm grant $PKG android.permission.RECORD_AUDIO

echo "konusma tanima servisi..."
settings put secure voice_recognition_service "$RECOG"

# TCL/MediaTek TV yazilimi, 3. parti uygulamalarin arka planda baslatilmasini "APP_AUTO_START" izniyle engeller (varsayilan: ignore).
# Izin yokken sistem asistan servisimize baglanamaz ve tus HIC bir sey yapmaz (log'da callee_does't_have_OP_AUTO_START_permission).
echo "otomatik baslatma izni (TCL)..."
cmd appops set $PKG APP_AUTO_START allow 2>&1
cmd appops get $PKG APP_AUTO_START 2>&1 | head -1

echo "varsayilan asistan..."
cmd role add-role-holder --user 0 android.app.role.ASSISTANT $PKG 2>&1
echo "role cikis kodu: $?"
# Sistem asistan servisine bir kez baglanamadiysa bu basarisiz durumu hatirlar (mBound=false); ayari silip yeniden yazmak baglantiyi sifirlar.
settings delete secure voice_interaction_service
sleep 2
settings put secure assistant "$ASSIST"
settings put secure voice_interaction_service "$ASSIST"
sleep 3

echo "erisilebilirlik servisi..."
CUR=$(settings get secure enabled_accessibility_services)
case "$CUR" in
  *"$A11Y"*) echo "zaten acik" ;;
  ""|null) settings put secure enabled_accessibility_services "$A11Y" ;;
  *) settings put secure enabled_accessibility_services "$CUR:$A11Y" ;;
esac
settings put secure accessibility_enabled 1

echo "--- sonuc ---"
echo "assistant=$(settings get secure assistant)"
echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"

echo "--- 4 sn sonra tekrar kontrol (kalici mi?) ---"
sleep 4
echo "assistant=$(settings get secure assistant)"
echo "voice_interaction_service=$(settings get secure voice_interaction_service)"
echo "voice_recognition_service=$(settings get secure voice_recognition_service)"
echo "enabled_accessibility_services=$(settings get secure enabled_accessibility_services)"
dumpsys voiceinteraction | grep -E "mComponent=|Session service=|Recognition service=|mBound=" | head -6
if dumpsys voiceinteraction | grep -q "mService=android.service.voice"; then
  echo "DURUM: asistan servisine baglandi - tus hazir"
else
  echo "UYARI: asistan servisine BAGLANILAMADI. TV'yi yeniden baslat, sonra kurulum.bat (veya scripts\\setup.bat) dosyasini tekrar calistir."
fi
dumpsys role 2>/dev/null | grep -i -A5 "role.ASSISTANT" | head -14
