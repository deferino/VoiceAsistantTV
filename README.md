# Sesli Arama (STT) — Android TV için hafif, bulut tabanlı ses→metin

![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue) ![Android TV](https://img.shields.io/badge/Android%20TV-6.0%2B-green) ![APK](https://img.shields.io/badge/APK-~190%20KB-lightgrey)

Android TV kumandasındaki **asistan/mikrofon tuşuna basıp konuşursun**; ses bulut STT ile metne çevrilir, metin odaktaki alana yazılır, **SmartTube / Nuvio'da aranır** ya da (hava sorusuysa) **hava paneli** açılır. Küçük ve hafif bir uygulama.
[Sayboard](https://github.com/ElishaAz/Sayboard) (GPL-3.0) mimarisinden türetildi; Vosk yerine
**bulut STT API'si** (varsayılan: Groq `whisper-large-v3-turbo`) kullanır. Telefondaki ses modeli yok,
RAM/işlemci kullanımı çok düşük; sadece konuşurken mikrofonu açar, sonucu API'den alır.

> 🇬🇧 **English:** [README.en.md](README.en.md) — the app, the installer and the weather/search commands now work in **English and Turkish**
> (Settings → *Arayüz dili / Interface language*; "Automatic" follows the TV's language).
> It was developed and tested on a **TCL Smart TV Pro (Android 11)** only and is provided **as is**, without warranty (GPL-3.0).
> It is not affiliated with SmartTube, Nuvio, Groq, OpenAI, TMDB, YouTube or Open-Meteo.

<p align="center">
  <img src="docs/img/weather.png" width="49%" alt="Hava durumu paneli (“weather in london”)">
  <img src="docs/img/results.png" width="49%" alt="Arama sonuçları: SmartTube / YouTube satırı ve Nuvio film satırı">
</p>

## Ne yapar?

- **Kumandadaki asistan/mikrofon tuşu bu uygulamayı açar** (varsayılan asistan olarak atanır).
- Tuşa bas → konuş → sessizlik olunca (veya OK'a basınca) metne çevrilir → **odaktaki arama/yazı alanına yazılır**.
  Alan bulunamazsa metin panoya kopyalanır ve ekranda gösterilir.
- Başka uygulamalar `SpeechRecognizer` / `RECOGNIZE_SPEECH` ile ses tanıma isterse onlara da hizmet verir.
- **Aramayı SmartTube ve Nuvio'da birlikte yapar** (ayarlardan değiştirilebilir):
  - *İkisinde de ara* (varsayılan): konuşma bitince tek ekranda yan yana iki liste çıkar: üstte **SmartTube · YouTube** videoları (küçük resim, süre, kanal, izlenme, tarih), altta **Nuvio · Film/Dizi** sonuçları (afiş, yıl, puan, tür, özet) yatay kart satırları olarak çıkar (Google Asistan sonuçları gibi). Sol/Sağ: kartlarda gez, Yukarı/Aşağı: satır değiştir, OK: aç (video SmartTube'da oynar, film/dizi Nuvio'da açılır). Altta "Tüm sonuçlar için SmartTube'da ara" ve "Odaktaki alana yaz" düğmeleri vardır.
  - *Hep SmartTube*: doğrudan SmartTube arama sonuçlarını açar. *Hep Nuvio*: yalnızca Nuvio listesini gösterir. *Sadece alana yaz*: eski davranış.
  - YouTube listesi için anahtarsız web araması denenir; istersen ayarlardan YouTube Data API anahtarı girebilirsin. Nuvio'nun arama bağlantısı olmadığı için film/dizi listesi Cinemeta'dan (anahtarsız) gelir; istersen TMDB anahtarı girersen Türkçe adlarla da bulur.
- **Hava durumu**: "İstanbul hava durumu", "İstanbul'da bugün hava nasıl", "yarın Ankara'da hava nasıl olacak", "Bursa kaç derece", "Antalya'da yağmur yağacak mı" gibi bir şey söylersen **sadece hava paneli** açılır (şu anki durum, saat saat ve 7 günlük tahmin; Open-Meteo, anahtarsız). "Yarın/öbür gün" dersen o günün özeti gelir. Şehir söylemezsen ("hava durumu") ayarlardaki varsayılan şehir kullanılır (varsayılan: Türkçe arayüzde İstanbul, İngilizcede London; ayarlardan değiştirilir). Hava sorusu olmayan her şey eskisi gibi aranır; söylenen şey bir yer adı çıkmazsa da normal aramaya düşer. "Sadece alana yaz" modunda hava devreye girmez. Panelde *Kapat* ve *YouTube / Nuvio'da ara* düğmeleri vardır.
- **SmartTube / Nuvio paket adı seçilebilir** (Ayarlar › Arama hedefi › *SmartTube paketi*, *Nuvio paketi*): *Otomatik* (kurulu olanı kendisi bulur, varsayılan), hazır liste ya da *Başka paket adı yaz…*. Hazır liste: SmartTube `org.smarttube.stable`, SmartTube Beta `org.smarttube.beta`; Nuvio `com.nuvio.tv` (orijinal), Nuvio Arc `com.nuvio.tv.test`, Nuvio Native Legacy `space.nuvio.nativelegacy`, Nuvio Reshaped `com.nuvioreshaped.tv`. Listede kurulu olanlar ✓ ile işaretlenir. Bir paket seçersen yalnız o kullanılır (kurulu değilse hata gösterilir). Native Legacy `nuvio://` bağlantısını tanımadığı için başlıklar IMDb bağlantısıyla açılır.
- **Türkçe ve İngilizce arayüz**: Ayarlar › Genel › *Arayüz dili / Interface language* (Otomatik = TV'nin diline uyar). İngilizce arayüzde hava ve arama de İngilizce çalışır ("weather in London", "will it rain tomorrow", "what's the weather like in Paris tomorrow" …); konuşma dilini (Whisper) ayrıca *Konuşma dili* satırından seçersin. Kurulum betiği de Windows diline göre Türkçe/İngilizce konuşur (`install.bat` = `kurulum.bat`).
- **Tamamen kumanda ile kullanılır** (yön tuşları, OK, Geri). Dokunmatik gerekmez.
- **API ayarları uygulamanın içinden değiştirilir**: sağlayıcı (Groq / OpenAI / kendi sunucun), adres, anahtar, model, dil, sessizlik süresi vb.

## Kumanda kullanımı

| Tuş | Ayarlar ekranında | Dinleme ekranında |
|---|---|---|
| Yukarı / Aşağı | Satırlar arasında gez | — |
| OK | Satırı aç / değiştir | Konuşmayı hemen bitir ve çevir |
| Asistan tuşu | (ana işlev) dinlemeyi başlat | Konuşmayı hemen bitir |
| Geri | Çık | İptal |

## Hızlı kurulum (Windows — önerilen)

1. [Releases](../../releases) sayfasından **`SesliArama-kurulum.zip`** dosyasını indir ve bir klasöre çıkar (içindekiler aynı klasörde kalsın).
2. **TV'de bir kerelik hazırlık:** Ayarlar → Cihaz tercihleri → Hakkında → *Derleme* satırına 7 kez OK (geliştirici modu açılır).
   Sonra Geliştirici seçenekleri → **USB hata ayıklama** (varsa **Ağ hata ayıklama**) → *Açık*. TV'nin IP adresini not et (Ayarlar → Ağ ve İnternet).
3. **`kurulum.bat`** dosyasına çift tıkla ve TV'nin IP adresini yaz. İlk bağlanışta TV'de çıkan "USB hata ayıklamaya izin verilsin mi?"
   penceresinde **Her zaman izin ver**'i seç. (Bilgisayar ve TV aynı modemde olmalı.)
4. İstersen Groq API anahtarını yapıştır ([ücretsiz anahtar al](https://console.groq.com/keys)); boş bırakırsan sonra uygulamanın Ayarlar ekranından girersin.
5. "KURULUM TAMAM" yazısını görünce kumandadaki **asistan / mikrofon tuşuna** bas ve konuş.

`kurulum.bat` şunları yapar: gerekirse adb'yi Google'ın resmi adresinden indirmeyi önerir → TV'ye bağlanır → APK'yı kurar (varsa günceller) →
mikrofon iznini verir → uygulamayı varsayılan asistan, konuşma tanıma ve erişilebilirlik servisi yapar → (isteğe bağlı) API anahtarını TV'ye aktarır →
ayar ekranını açar. Kayıt: `kurulum.log`. Geri almak için komut satırından `kurulum.bat kaldir` (İngilizce: `install.bat uninstall`; eski asistan ayarlarını geri yükler, uygulamayı siler).

- Sorusuz çalıştırma: `kurulum.bat auto` (TV adresi `tv.txt` dosyasında ya da `VT_TV` ortam değişkeninde olmalı; anahtar için `VT_KEY`).
- Kurulum penceresi hata verirse `kurulum.log` dosyasına bak; en sık sebep: TV'de hata ayıklama kapalı, IP yanlış ya da TV'deki izin penceresi onaylanmadı.
- API anahtarı adb ile uygulamanın dahili ayar alıcısına (`ConfigReceiver`) yollanır; alıcı `android.permission.DUMP` ister, yani yalnızca adb/shell gönderebilir, TV'deki başka bir uygulama ayar değiştiremez. Anahtar loga yazılmaz.
- Release APK, hata ayıklama anahtarıyla imzalıdır (kişisel kullanım). Güvenmek istemiyorsan kaynaktan kendin derle (`scripts\release.bat` → `dist\`).

## Kaynaktan derleyip kurma (geliştiriciler)

Gerekenler: **Android Studio** (SDK + platform-tools ile gelir) ve TV ile aynı ağda bir bilgisayar.

1. **TV'de ağ hata ayıklamayı aç**
   Ayarlar → Cihaz tercihleri → Hakkında → *Derleme* satırına 7 kez OK (geliştirici modu).
   Sonra Ayarlar → Cihaz tercihleri → Geliştirici seçenekleri → **USB hata ayıklama / Ağ hata ayıklama: Açık**.
   TV'nin IP adresini not et (Ayarlar → Ağ). Port genelde `5555`.
2. `scripts\tv.example.txt` dosyasını `scripts\tv.txt` olarak kopyala, içine `IP:5555` yaz (örn. `192.168.1.50:5555`).
3. **API anahtarı** (ücretsiz): <https://console.groq.com/keys> adresinden Groq anahtarı al.
   `scripts\config.example.json` dosyasını proje köküne `config.json` olarak kopyala, `apiKey` alanına yaz.
   (Anahtar APK'nın içine gömülmez; kurulumda TV'ye aktarılır ve alınınca dosya silinir. İstersen bunu atlayıp
   anahtarı uygulamanın içinden de girebilirsin.)
4. `scripts\build.bat` çalıştır → `app\build\outputs\apk\debug\app-debug.apk`
5. TV açıkken `scripts\install.bat` çalıştır. İlk bağlanışta TV'de "USB hata ayıklamaya izin ver?" çıkarsa **Her zaman izin ver** de.
   Betik: APK'yı kurar, mikrofon iznini verir, uygulamayı **varsayılan asistan + konuşma tanıma servisi + erişilebilirlik servisi** yapar, ayar ekranını açar.

### Test

1. Uygulamayı aç (Uygulamalar listesinde "Sesli Arama (STT)").
2. "Bağlantıyı test et" → "Tamam" görmelisin.
3. "Mikrofon testi" → konuş, seviye çubuğu hareket etmeli.
4. Bir arama kutusuna (ör. TV'nin arama ekranı) gel, **kumandadaki mikrofon tuşuna** bas, konuş.

### Sorun giderme

| Belirti | Ne yapmalı |
|---|---|
| Mikrofon tuşuna basınca **hiçbir şey olmuyor** (TCL / MediaTek TV) | Kök neden: TV yazılımı 3. parti uygulamaların arka planda başlatılmasını `APP_AUTO_START` izniyle engeller (varsayılan *ignore*); sistem asistan servisimize bağlanamaz ve bu başarısız durumu hatırlar. `scripts\setup.bat` izni verir (`cmd appops set … APP_AUTO_START allow`) ve bağlantıyı sıfırlar. Kalıcıdır, yeniden başlatmada da korunur; uygulamayı her `install` ettiğinde betik bunu yeniden uygular. |
| Mikrofon tuşu hâlâ Google Asistan'ı açıyor | `scripts\keys.bat` çalıştırıp tuşa bas; `logs\keys.log` içinde tuş görünüyor mu bak. Uygulamadaki *Kumanda tuş testi*ni de dene. Sonra `scripts\logcat.bat clear`, tuşa bas, `scripts\logcat.bat` ve `logs\logcat.log` dosyasına bak. |
| Kumanda mikrofonu sessiz (seviye hep 0) | Bazı kumandalar sesi Bluetooth/özel kanaldan sisteme verir ve 3. parti uygulamalara açmaz. Ayarlardan *Ses kaynağı* ve *Mikrofon cihazı* seçeneklerini dene; olmuyorsa TV'ye USB mikrofon veya telefon mikrofonu gerekir. |
| Metin yazılmıyor, panoya kopyalanıyor | Erişilebilirlik servisi kapalı olabilir (ana ekrandaki durum satırına bak) ya da uygulamanın arama kutusu standart değil. |
| "Bağlantı yok / 401" | Adres, anahtar ve modeli ayarlardan kontrol et. |
| Hata bildirecek misin? | `kurulum.log` / `logs\*.log` dosyasını yalnızca **TV'nin IP adresini `x.x.x.x` ile değiştirdikten sonra** ekle; API anahtarını (ya da bir parçasını) hiçbir yerde paylaşma. |
| Eski haline dönmek | `scripts\restore.bat` — kurulumdan önceki asistan/tanıma/erişilebilirlik ayarlarını geri yükler. |

## Elle komutlar (betik kullanmak istemezsen)

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

## API sağlayıcıları

`POST {adres}/audio/transcriptions` (OpenAI uyumlu) kullanır; bu yüzden şunlarla çalışır:

- **Groq** (varsayılan, ücretsiz katman): `https://api.groq.com/openai/v1`, model `whisper-large-v3-turbo`
- **OpenAI**: `https://api.openai.com/v1`, model `gpt-4o-mini-transcribe` / `whisper-1`
- **Kendi sunucun** (LAN'da faster-whisper-server vb.): `http://192.168.1.10:8000/v1`, anahtar boş olabilir

## Gizlilik

- **Ses**: konuşma sesi, seçtiğin API sağlayıcısına (varsayılan Groq) gönderilir; başka yere gönderilmez, saklanmaz. Mikrofon yalnızca dinleme ekranı açıkken kullanılır. Ücretsiz katmanların veri kullanım koşullarını sağlayıcının sayfasından kontrol et.
- **Konuşmanın metni**: aranan kelimeler YouTube (anahtarsız web araması), Cinemeta / TMDB (film/dizi) ve — hava sorusuysa — Open-Meteo'ya (yalnızca şehir adı) gider.
- **Bağlantı ısıtma**: asistan tuşuna basıp dinleme başlayınca, sonuçlar daha çabuk gelsin diye uygulama konuşurken arama sunucularına (YouTube, TMDB, Groq) boş bir `HEAD` isteği atar. İçinde konuşma ya da kişisel veri yoktur; sunucular yalnızca bağlantı kurulduğunu (IP adresini) görür.
- **Erişilebilirlik servisi** hiçbir ekran içeriğini okumaz/kaydetmez; yalnızca konuşma bitince odaktaki yazı alanını bulup metni oraya yazar.
- API anahtarları yalnızca TV'deki uygulama ayarlarında tutulur (APK'ya gömülmez, loga yazılmaz).
- Analitik, reklam ya da izleme yoktur.

## Kaynaklar ve teşekkür

- Hava verisi: **[Open-Meteo.com](https://open-meteo.com/)** (CC BY 4.0). Hava panelinde de belirtilir.
- Film/dizi verisi: Cinemeta (Stremio) ve isteğe bağlı TMDB. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- Video araması YouTube'dan; sonuçlar SmartTube'da açılır. SmartTube ve Nuvio ayrı uygulamalardır, bu proje onlarla bağlantılı değildir ve onları içermez; kendin kurman gerekir.
- Mimari fikri: [Sayboard](https://github.com/ElishaAz/Sayboard) (GPL-3.0), bkz. `NOTICE.md`.

## Klavye ile birlikte kullanım (isteğe bağlı)

Varsayılan klavye *Simple Keyboard TV* (`rkr.simplekeyboard.inputmethod.tv`) ise, asistan tuşuna basılınca uygulama önce klavyeye
`rkr.simplekeyboard.inputmethod.tv.action.VOICE_KEY` adlı sıralı bir yayın gönderir (en çok 0,7 sn bekler). Klavye bunu desteklerse kendi "sesle yaz"
özelliğini başlatır ve metin alanı odağını kaybetmeden yazılır. Klavye yoksa / yanıt vermezse normal dinleme ekranı açılır. Bu davranış klavye yoksa hiçbir şey değiştirmez.

## Performans

- Release APK yaklaşık **190 KB** (iki dil dahil) (R8 küçültme + kaynak küçültme). Hiçbir üçüncü taraf kütüphane yok; sadece Android framework'ü.
- Asistan tuşuna basınca dinleme başlarken, konuşma sürerken arka planda arama sunucularına bağlantılar önceden kurulur (DNS + TLS ısıtma, ~0,5 sn). Konuşma bitince sonuç aramaları hazır bağlantıyla gider: ölçümümde (TCL TV, kendi ağım) ısıtılmış bağlantıyla film arama isteği ~40 ms'de döndü; ısıtmasız ilk istek yüzlerce ms ile birkaç saniye arasında sürebiliyor. Sonuçlar ağına göre değişir.
- Mikrofon ve ağ yalnızca tuşa basıldığında kullanılır; uygulama kendi başına arka planda iş yapmaz (asistan ve erişilebilirlik servisleri sistem tarafından bağlanır, boştayken bir şey yapmaz).

## Yapı

```
app/src/main/java/com/voiceasistanttv/stt/
  Transcriber.kt          mikrofon + sessizlik algılama + yükleme
  SttClient.kt            OpenAI uyumlu multipart istemci
  AssistService.kt        asistan tuşu (VoiceInteractionService)
  AppLauncher.kt          SmartTube / Nuvio bulma ve açma
  MediaSearch.kt          film/dizi adı arama (Cinemeta / TMDB)
  YouTubeSearch.kt        YouTube video arama (web / Data API)
  Weather.kt              hava sorusu ayrıştırma (şehir/gün) + Open-Meteo istemcisi
  Prewarm.kt              dinleme sırasında arama sunucularına bağlantı ısıtma
  NetLog.kt               ağ çağrısı süre ölçümü (logcat, tag VoiceTV)
  ConfigReceiver.kt       kurulum betiğinin adb ile ayar (API anahtarı) göndermesi (DUMP izinli)
  KeyboardHandoff.kt      Simple Keyboard TV varsa dikteyi klavyeye devretme
  WeatherUi.kt            hava paneli kartları + Canvas ile çizilen hava simgeleri
  ResultCards.kt          sonuç kartları + küçük resim yükleyici
  AssistActivity.kt       dinleme ekranı
  CloudRecognitionService.kt  SpeechRecognizer servisi
  InjectorService.kt      odaktaki alana yazma (erişilebilirlik)
  MainActivity.kt         kumanda uyumlu ayar ekranı
  KeyTestActivity.kt      kumanda tuş testi
  Lang.kt                 arayüz dili seçimi + çeviri yardımcısı (res/values = İngilizce, res/values-tr = Türkçe)
docs/img/                 README ekran görüntüleri
kurulum.bat / install.bat  son kullanıcı kurulumu (adb + APK + asistan ataması; `kurulum.bat kaldir` / `install.bat uninstall` ile geri alma; Türkçe/İngilizce)
scripts/                  all (derle+kur+test) / update (derle+kur) / build / install / setup / restore / logcat / keys / diag / shot (TV ekran görüntüsü)
scripts/release.bat       release APK + dist\SesliArama-kurulum.zip (GitHub Releases'e yüklenecek paket)
```

## Lisans

Bu proje **GNU Genel Kamu Lisansı v3 (GPL-3.0)** altında özgür yazılımdır — bkz. [`LICENSE.txt`](LICENSE.txt). Kullanabilir, inceleyebilir, değiştirebilir ve
dağıtabilirsin; türev çalışmalar da aynı lisansla ve kaynak koduyla birlikte kalmalıdır. **Hiçbir garanti verilmez.**
Telif: Copyright (C) 2026 VoiceAsistantTV katkıda bulunanlar.

| Bölüm | Lisans / koşul |
|---|---|
| Bu proje (kaynak kod, betikler, çeviriler, bu proje için çizilen uygulama simgesi ve TV afişi) | GPL-3.0 |
| [Sayboard](https://github.com/ElishaAz/Sayboard) (ElishaAz) — türetildiği mimari fikri | GPL-3.0 |
| Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) | Apache License 2.0 (Gradle Inc.) |
| APK içindeki üçüncü taraf kütüphaneler | yok — yalnızca Android framework API'leri kullanılır |
| Hava verisi — [Open-Meteo.com](https://open-meteo.com/) | CC BY 4.0 (hava panelinde belirtilir). Ücretsiz API'si ticari olmayan kullanım içindir |
| Film/dizi verisi — Cinemeta (Stremio), TMDB | kendi koşulları; *This product uses the TMDB API but is not endorsed or certified by TMDB.* |
| Konuşma tanıma — Groq / OpenAI / kendi sunucun | **senin** API anahtarın ve **o sağlayıcının** kullanım koşulları geçerlidir |
| `docs/img/` ekran görüntüleri | bu uygulamanın arayüzü; içindeki küçük resim/afişler sahiplerine aittir, yalnızca arayüzü göstermek için yer alır |

SmartTube, Nuvio, YouTube, Groq, OpenAI, TMDB ve Open-Meteo ilgili sahiplerinin marka/ürünleridir; bu proje onlarla bağlantılı değildir ve onların yazılımını içermez.
Ayrıntı: [`NOTICE.md`](NOTICE.md).
