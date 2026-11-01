@echo off
rem ====================================================================
rem  Voice Search (STT) - Android TV installer (Windows 10 / 11)
rem
rem  Usage:
rem    install.bat             install or update (double-click is enough)
rem    install.bat uninstall   restore the previous assistant settings and remove the app
rem    install.bat auto        unattended: asks nothing (needs tv.txt or VT_TV)
rem
rem  Language: English, or Turkish when Windows is set to Turkish.
rem  Force it with VT_LANG=en or VT_LANG=tr.
rem
rem  What it does: connects to the TV over adb, installs the APK, grants the
rem  microphone permission and makes the app the default assistant + speech
rem  recognition + accessibility service (the remote's assistant key then opens
rem  this app). Optionally sends your Groq API key to the TV as well.
rem
rem  Required files next to this one (or under scripts\):
rem  VoiceSearch.apk, tv-setup.sh, restore-defaults.sh
rem
rem  Optional environment variables:
rem    VT_TV=192.168.1.50:5555   VT_APK=C:\path\app.apk   VT_KEY=gsk_...
rem
rem  Exit codes: 0 ok, 3 file missing, 4 TV address missing/invalid,
rem  5 no adb, 6 cannot connect to the TV, 7 install failed
rem ====================================================================
setlocal EnableExtensions
cd /d "%~dp0"
set "PKG=com.voiceasistanttv.stt"
set "LOG=%~dp0install.log"
set "TMPF=%TEMP%\vt-install-%RANDOM%.txt"
set "AUTO=0"
set "MODE=install"
set "RC=0"

rem ---------------------------------------------------------------- language
set "L=en"
set "LOC="
if defined VT_LANG goto langforced
for /f "tokens=3" %%A in ('reg query "HKCU\Control Panel\International" /v LocaleName 2^>nul') do set "LOC=%%A"
if defined LOC if /i "%LOC:~0,2%"=="tr" set "L=tr"
goto langdone
:langforced
if /i "%VT_LANG%"=="tr" set "L=tr"
:langdone
if /i "%L%"=="tr" (call :msg_tr) else (call :msg_en)
goto args

:args
if "%~1"=="" goto argsdone
if /i "%~1"=="auto" set "AUTO=1"
if /i "%~1"=="uninstall" set "MODE=uninstall"
shift
goto args
:argsdone

> "%LOG%" echo [%date% %time%] install (%MODE%)
echo.
echo  ==============================================
echo    %M_title%
echo  ==============================================
echo.

rem ---------------------------------------------------------------- 1) adb
set "ADB="
if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if not defined ADB where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if defined ADB goto adbok

echo  %M_adb_missing%
echo        %M_adb_info%
if "%AUTO%"=="1" goto dladb
set "ANS="
set /p "ANS=%M_adb_ask%"
if /i "%ANS%"=="h" goto noadb
if /i "%ANS%"=="n" goto noadb
:dladb
echo        %M_downloading%
curl.exe -L -f -s -o "%TEMP%\vt-platform-tools.zip" https://dl.google.com/android/repository/platform-tools-latest-windows.zip >> "%LOG%" 2>&1
if errorlevel 1 goto noadb
tar.exe -xf "%TEMP%\vt-platform-tools.zip" -C "%~dp0." >> "%LOG%" 2>&1
del "%TEMP%\vt-platform-tools.zip" >nul 2>&1
if not exist "%~dp0platform-tools\adb.exe" goto noadb
set "ADB=%~dp0platform-tools\adb.exe"

:adbok
>> "%LOG%" echo adb: %ADB%
echo  %M_adb_ready%

rem ---------------------------------------------------------------- files
set "SETUPSH="
if exist "%~dp0tv-setup.sh" set "SETUPSH=%~dp0tv-setup.sh"
if not defined SETUPSH if exist "%~dp0scripts\tv-setup.sh" set "SETUPSH=%~dp0scripts\tv-setup.sh"
set "RESTORESH="
if exist "%~dp0restore-defaults.sh" set "RESTORESH=%~dp0restore-defaults.sh"
if not defined RESTORESH if exist "%~dp0scripts\restore-defaults.sh" set "RESTORESH=%~dp0scripts\restore-defaults.sh"

set "APKF="
if "%MODE%"=="uninstall" goto apkdone
if defined VT_APK if exist "%VT_APK%" set "APKF=%VT_APK%"
if not defined APKF if exist "%~dp0VoiceSearch.apk" set "APKF=%~dp0VoiceSearch.apk"
if not defined APKF for %%F in ("%~dp0*.apk") do if not defined APKF set "APKF=%%~fF"
if not defined APKF if exist "%~dp0app\build\outputs\apk\release\app-release.apk" set "APKF=%~dp0app\build\outputs\apk\release\app-release.apk"
if not defined APKF if exist "%~dp0app\build\outputs\apk\debug\app-debug.apk" set "APKF=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not defined APKF goto noapk
if not defined SETUPSH goto nosetupsh
>> "%LOG%" echo apk: %APKF%
:apkdone

rem ---------------------------------------------------------------- 2) TV address
set "TV="
set "ASKED=0"
if defined VT_TV set "TV=%VT_TV%"
if not defined TV if exist "%~dp0tv.txt" set /p TV=<"%~dp0tv.txt"
if not defined TV if exist "%~dp0scripts\tv.txt" set /p TV=<"%~dp0scripts\tv.txt"
if defined TV goto tvcheck
if "%AUTO%"=="1" goto notv

echo.
echo  %M_tv_help1%
echo  %M_tv_help2%
echo  %M_tv_help3%
echo  %M_tv_help4%
echo.
:asktv
set /a ASKED+=1
if %ASKED% GTR 5 goto notv
set "TV="
set /p "TV=%M_tv_prompt%"
if not defined TV goto asktv

:tvcheck
set "TV=%TV:"=%"
set "TV=%TV: =%"
set "VT_TV=%TV%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "if ($env:VT_TV -match '^[0-9A-Za-z.:-]{3,64}$') { exit 0 } else { exit 1 }"
if errorlevel 1 goto badtv
if "%TV::=%"=="%TV%" set "TV=%TV%:5555"
set "VT_TV=%TV%"
> "%~dp0tv.txt" echo %TV%
>> "%LOG%" echo tv: %TV%

rem ---------------------------------------------------------------- 3) connect
echo.
echo  %M_connecting% %TV%
set "TRY=0"
set "UT=0"
:conn
set /a TRY+=1
>> "%LOG%" echo == adb connect %TV% (attempt %TRY%)
"%ADB%" connect %TV% >> "%LOG%" 2>&1
set "STATE=none"
"%ADB%" -s %TV% get-state > "%TMPF%" 2>&1
set /p STATE=<"%TMPF%"
set "STATE=%STATE:"=%"
>> "%LOG%" echo state: %STATE%
if "%STATE%"=="device" goto connected
if not "%STATE:unauthorized=%"=="%STATE%" goto unauth
if %TRY% GEQ 3 goto notconnected
echo        %M_retry%
ping -n 4 127.0.0.1 >nul
goto conn

:unauth
set /a UT+=1
if %UT% GEQ 13 goto notconnected
if %UT%==1 call :unauthmsg
ping -n 6 127.0.0.1 >nul
set "TRY=0"
goto conn

:unauthmsg
echo.
echo        %M_unauth1%
echo        %M_unauth2%
echo        %M_unauth3%
goto :eof

:connected
echo        %M_connected%
if "%MODE%"=="uninstall" goto uninst

rem ---------------------------------------------------------------- 4) API key question
set "KEYIN="
if defined VT_KEY goto keyasked
if "%AUTO%"=="1" goto keyasked
echo.
echo  %M_key1%
echo  %M_key2%
set /p "KEYIN=%M_key_prompt%"
if defined KEYIN set "VT_KEY=%KEYIN:"=%"
:keyasked

rem ---------------------------------------------------------------- 5) install
echo.
echo  %M_installing%
"%ADB%" -s %TV% install -r -g "%APKF%" > "%TMPF%" 2>&1
type "%TMPF%" >> "%LOG%"
findstr /c:"Success" "%TMPF%" >nul 2>&1
if errorlevel 1 goto installfail
echo        %M_installed%

echo.
echo  %M_assist_setup%
"%ADB%" -s %TV% push "%SETUPSH%" /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell "tr -d '\r' < /data/local/tmp/vt-setup.sh > /data/local/tmp/vt-setup2.sh; sh /data/local/tmp/vt-setup2.sh" > "%TMPF%" 2>&1
type "%TMPF%" >> "%LOG%"
set "SETUPOK=0"
findstr /c:"STATUS: assistant service connected" "%TMPF%" >nul 2>&1
if not errorlevel 1 set "SETUPOK=1"
if "%SETUPOK%"=="1" echo        %M_assist_ok%
if not "%SETUPOK%"=="1" echo        %M_assist_warn%

echo.
echo  %M_settings%
rem Key: typed / VT_KEY / config.json in the installer folder. Only PowerShell handles the value (it never enters cmd).
set "VT_ADB=%ADB%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$k=$env:VT_KEY; if(-not $k -and (Test-Path 'config.json')){try{$k=(Get-Content -Raw -Encoding UTF8 'config.json'|ConvertFrom-Json).apiKey}catch{}}; if(-not $k){exit 3}; $k=$k.Trim(); if($k -notmatch '^[A-Za-z0-9_.-]{8,200}$'){exit 2}; $o=(& $env:VT_ADB -s $env:VT_TV shell am broadcast --include-stopped-packages -n com.voiceasistanttv.stt/.ConfigReceiver -a com.voiceasistanttv.stt.CONFIG --es apiKey $k 2>&1) | Out-String; Add-Content -Path 'install.log' -Value $o; if($o -match 'result=1'){exit 0}else{exit 4}"
set "KRC=%ERRORLEVEL%"
set "VT_KEY="
>> "%LOG%" echo key step: %KRC%
if "%KRC%"=="0" echo        %M_key_ok%
if "%KRC%"=="3" echo        %M_key_none%
if "%KRC%"=="2" echo        %M_key_bad%
if "%KRC%"=="4" echo        %M_key_fail%

rem NOTE: do NOT use "am force-stop". If the assistant/recognition service is stopped, Android reverts the assignment to Google.
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity >> "%LOG%" 2>&1

echo.
echo  ==============================================
echo    %M_done%
echo  ==============================================
echo  %M_done1%
echo  %M_done2%
echo  %M_done3%
echo   %M_detail% %LOG%
goto end

rem ---------------------------------------------------------------- uninstall
:uninst
if "%AUTO%"=="1" goto ungo
echo.
echo  %M_un_info%
set "ANS="
set /p "ANS=%M_un_ask%"
if /i "%ANS%"=="y" goto ungo
if /i not "%ANS%"=="e" goto end
:ungo
if not defined RESTORESH goto unnorestore
"%ADB%" -s %TV% push "%RESTORESH%" /data/local/tmp/vt-restore.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell "tr -d '\r' < /data/local/tmp/vt-restore.sh > /data/local/tmp/vt-restore2.sh; sh /data/local/tmp/vt-restore2.sh" > "%TMPF%" 2>&1
type "%TMPF%" >> "%LOG%"
type "%TMPF%"
goto dounins
:unnorestore
echo  %M_un_nofile%
:dounins
"%ADB%" -s %TV% uninstall %PKG% > "%TMPF%" 2>&1
type "%TMPF%" >> "%LOG%"
type "%TMPF%"
echo.
echo  %M_un_done%
goto end

rem ---------------------------------------------------------------- errors
:noadb
set "RC=5"
echo.
echo  %M_noadb1%
echo  %M_noadb2%
echo  https://developer.android.com/tools/releases/platform-tools
goto end

:noapk
set "RC=3"
echo.
echo  %M_noapk1%
echo  %M_noapk2%
goto end

:nosetupsh
set "RC=3"
echo.
echo  %M_nosetup%
goto end

:notv
set "RC=4"
echo.
echo  %M_notv1%
echo  %M_notv2%
goto end

:badtv
set "RC=4"
echo.
echo  %M_badtv%
goto end

:notconnected
set "RC=6"
echo.
echo  %M_nc1%
echo  %M_nc2%
echo  %M_nc3%
echo  %M_nc4%
echo  %M_nc5% (%TV%)
echo  %M_nc6%
echo  %M_detail% %LOG%
goto end

:installfail
set "RC=7"
echo.
echo  %M_instfail%
type "%TMPF%"
findstr /c:"INCOMPATIBLE" "%TMPF%" >nul 2>&1
if not errorlevel 1 echo  %M_incompat%
echo  %M_detail% %LOG%
goto end

:end
del "%TMPF%" >nul 2>&1
if "%AUTO%"=="1" exit /b %RC%
echo.
pause
exit /b %RC%

rem ---------------------------------------------------------------- mesajlar / messages
:msg_tr
set "M_title=Sesli Arama (STT) - Android TV kurulumu"
set "M_adb_missing=[1/5] adb bulunamadi."
set "M_adb_info=adb, Google'in platform-tools paketinde gelir (yaklasik 7 MB)."
set "M_adb_ask=        Google'in resmi adresinden indirilsin mi? [E/h]: "
set "M_downloading=Indiriliyor..."
set "M_adb_ready=[1/5] adb hazir."
set "M_tv_help1=TV'nin IP adresini yaz."
set "M_tv_help2=Nerede yazar: TV'de Ayarlar - Ag ve Internet - bagli oldugun ag."
set "M_tv_help3=Bilgisayar ile TV ayni modeme (wifi veya kablo) bagli olmali."
set "M_tv_help4=Ornek: 192.168.1.50"
set "M_tv_prompt=  TV IP adresi: "
set "M_connecting=[2/5] TV'ye baglaniliyor:"
set "M_retry=Baglanilamadi, tekrar deneniyor..."
set "M_unauth1=TV'nin ekraninda 'USB hata ayiklamaya izin verilsin mi?' penceresi cikmali."
set "M_unauth2='Bu bilgisayardan her zaman izin ver' kutusunu isaretle, sonra Tamam'a bas."
set "M_unauth3=Bekliyorum..."
set "M_connected=Baglandi."
set "M_key1=Groq API anahtarin (ucretsiz: https://console.groq.com/keys) varsa simdi yapistir."
set "M_key2=Bos birakirsan atlanir; anahtari sonra uygulamanin Ayarlar ekranindan da girebilirsin."
set "M_key_prompt=  Anahtar (gsk_...): "
set "M_installing=[3/5] Uygulama kuruluyor..."
set "M_installed=Kuruldu."
set "M_assist_setup=[4/5] Asistan tusu ve izinler ayarlaniyor (yaklasik 15 saniye)..."
set "M_assist_ok=Asistan tusu bu uygulamaya baglandi."
set "M_assist_warn=UYARI: asistan servisine baglanilamadi. TV'yi yeniden baslatip install.bat'i tekrar calistir."
set "M_settings=[5/5] Ayarlar..."
set "M_key_ok=API anahtari TV'ye aktarildi."
set "M_key_none=API anahtari girilmedi (uygulamada Ayarlar - Anahtar bolumunden girebilirsin)."
set "M_key_bad=UYARI: anahtarda gecersiz karakter var, aktarilmadi. Uygulamadan elle gir."
set "M_key_fail=UYARI: anahtar aktarilamadi. Uygulamadan elle gir (Ayarlar)."
set "M_done=KURULUM TAMAM"
set "M_done1= 1) TV'de Ayarlar ekrani acildi: 'Baglantiyi test et' ile anahtarini dene."
set "M_done2= 2) Kumandadaki ASISTAN / MIKROFON tusuna bas ve konus."
set "M_done3= 3) Eski haline donmek icin: install.bat uninstall"
set "M_detail=Ayrinti:"
set "M_un_info=Bu islem eski asistan / tanima ayarlarini geri yukler ve uygulamayi TV'den siler."
set "M_un_ask=  Devam edilsin mi? [e/H]: "
set "M_un_nofile=UYARI: restore-defaults.sh bulunamadi, ayarlar geri yuklenmedi."
set "M_un_done=Tamam. Gerekirse TV'yi yeniden baslat."
set "M_noadb1=adb bulunamadi. Su adresten 'SDK Platform-Tools for Windows' indirip zip'i"
set "M_noadb2=bu klasore ac (platform-tools klasoru bu dosyanin yaninda olsun), sonra tekrar calistir:"
set "M_noapk1=APK dosyasi bulunamadi. VoiceSearch.apk dosyasini bu klasore koy"
set "M_noapk2=(GitHub 'Releases' sayfasindan indir) ve tekrar calistir."
set "M_nosetup=tv-setup.sh bulunamadi. Zip'in tamamini ayni klasore cikardigindan emin ol."
set "M_notv1=TV adresi yok. tv.txt dosyasina IP yaz (ornek 192.168.1.50:5555) ya da install.bat'i"
set "M_notv2=cift tiklayip sorulunca IP'yi gir."
set "M_badtv=TV adresi gecersiz: sadece rakam, nokta ve iki nokta kullan (ornek 192.168.1.50)."
set "M_nc1=TV'ye baglanilamadi. Kontrol et:"
set "M_nc2= - TV acik ve bilgisayarla ayni agda mi?"
set "M_nc3= - TV'de Ayarlar - Cihaz tercihleri - Hakkinda - 'Derleme'ye 7 kez OK bas (gelistirici modu),"
set "M_nc4=   sonra Gelistirici secenekleri - 'USB hata ayiklama / Ag hata ayiklama' ACIK mi?"
set "M_nc5= - IP adresi dogru mu?"
set "M_nc6= - Pencere cikmadiysa TV'yi yeniden baslat."
set "M_instfail=Kurulum basarisiz:"
set "M_incompat=TV'de farkli imzali bir surum var. Once 'install.bat uninstall' calistir, sonra tekrar kur."
goto :eof

:msg_en
set "M_title=Voice Search (STT) - Android TV installer"
set "M_adb_missing=[1/5] adb not found."
set "M_adb_info=adb comes in Google's platform-tools package (about 7 MB)."
set "M_adb_ask=        Download it from Google's official site? [Y/n]: "
set "M_downloading=Downloading..."
set "M_adb_ready=[1/5] adb ready."
set "M_tv_help1=Enter your TV's IP address."
set "M_tv_help2=Where to find it: on the TV, Settings - Network and Internet - your connected network."
set "M_tv_help3=The computer and the TV must be on the same router (wifi or cable)."
set "M_tv_help4=Example: 192.168.1.50"
set "M_tv_prompt=  TV IP address: "
set "M_connecting=[2/5] Connecting to the TV:"
set "M_retry=Could not connect, retrying..."
set "M_unauth1=A 'Allow USB debugging?' prompt should appear on the TV screen."
set "M_unauth2=Tick 'Always allow from this computer', then press OK."
set "M_unauth3=Waiting..."
set "M_connected=Connected."
set "M_key1=If you have a Groq API key (free: https://console.groq.com/keys), paste it now."
set "M_key2=Leave it empty to skip; you can also enter it later in the app's Settings screen."
set "M_key_prompt=  API key (gsk_...): "
set "M_installing=[3/5] Installing the app..."
set "M_installed=Installed."
set "M_assist_setup=[4/5] Setting up the assistant key and permissions (about 15 seconds)..."
set "M_assist_ok=The assistant key is now linked to this app."
set "M_assist_warn=WARNING: could not link the assistant service. Restart the TV and run install.bat again."
set "M_settings=[5/5] Settings..."
set "M_key_ok=API key was sent to the TV."
set "M_key_none=No API key entered (you can enter it in the app under Settings - API key)."
set "M_key_bad=WARNING: the key contains invalid characters and was not sent. Enter it in the app."
set "M_key_fail=WARNING: could not send the key. Enter it in the app (Settings)."
set "M_done=SETUP COMPLETE"
set "M_done1= 1) The app's Settings screen opened on the TV: try your key with 'Test the connection'."
set "M_done2= 2) Press the ASSISTANT / MICROPHONE key on the remote and speak."
set "M_done3= 3) To undo everything: install.bat uninstall"
set "M_detail=Details:"
set "M_un_info=This restores the previous assistant / recognition settings and removes the app from the TV."
set "M_un_ask=  Continue? [y/N]: "
set "M_un_nofile=WARNING: restore-defaults.sh not found, settings were not restored."
set "M_un_done=Done. Restart the TV if needed."
set "M_noadb1=adb not found. Download 'SDK Platform-Tools for Windows' from the address below and unzip it"
set "M_noadb2=into this folder (the platform-tools folder next to this file), then run this again:"
set "M_noapk1=APK file not found. Put VoiceSearch.apk into this folder"
set "M_noapk2=(download it from the GitHub 'Releases' page) and run this again."
set "M_nosetup=tv-setup.sh not found. Make sure you extracted the whole zip into the same folder."
set "M_notv1=No TV address. Write the IP into tv.txt (example 192.168.1.50:5555) or double-click install.bat"
set "M_notv2=and enter the IP when asked."
set "M_badtv=Invalid TV address: use only digits, dots and a colon (example 192.168.1.50)."
set "M_nc1=Could not connect to the TV. Check:"
set "M_nc2= - Is the TV on and on the same network as this computer?"
set "M_nc3= - On the TV: Settings - Device Preferences - About - press OK on 'Build' 7 times (developer mode),"
set "M_nc4=   then Developer options - is 'USB debugging / Network debugging' ON?"
set "M_nc5= - Is the IP address correct?"
set "M_nc6= - If no prompt appeared, restart the TV."
set "M_instfail=Installation failed:"
set "M_incompat=The TV has a version signed with a different key. Run 'install.bat uninstall' first, then install again."
goto :eof
