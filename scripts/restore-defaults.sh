#!/system/bin/sh
# Kurulumdan önceki asistan/tanıma/erişilebilirlik ayarlarını geri yükler.
PKG=com.voiceasistanttv.stt
BK=/data/local/tmp/voicetv_backup.txt
if [ ! -f "$BK" ]; then
  echo "yedek bulunamadi ($BK)"
  exit 1
fi
cmd role remove-role-holder android.app.role.ASSISTANT $PKG 2>&1 | head -2
while IFS== read -r KEY VAL; do
  [ -z "$KEY" ] && continue
  if [ "$VAL" = "null" ] || [ -z "$VAL" ]; then
    settings delete secure "$KEY"
  else
    settings put secure "$KEY" "$VAL"
  fi
  echo "$KEY -> $VAL"
done < "$BK"
echo "geri yukleme tamam"
