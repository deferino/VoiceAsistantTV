# NOTICE

Copyright (C) 2026 VoiceAsistantTV contributors.
This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3** (`LICENSE.txt`).
It is distributed WITHOUT ANY WARRANTY. / Bu program hiçbir garanti olmadan dağıtılır.

## Derivation / Türev çalışma

This project is a derivative work based on **Sayboard** (https://github.com/ElishaAz/Sayboard, GPL-3.0, by ElishaAz).
Bu proje, **Sayboard** (https://github.com/ElishaAz/Sayboard, GPL-3.0) temel alınarak oluşturulmuş bir türev çalışmadır.

Taken from Sayboard / Sayboard'dan alınan:
- the general architecture idea (RecognitionService + AudioRecord loop + text-entry flow) / genel mimari fikri (RecognitionService + AudioRecord döngüsü + metin giriş akışı),
- the Gradle wrapper files and the license text / Gradle sarmalayıcı dosyaları ve lisans metni.

Changed / Değişenler:
- Vosk (offline model) was removed completely; an OpenAI-compatible cloud STT client (`SttClient`) was added. / Vosk (çevrimdışı model) tamamen kaldırıldı; yerine OpenAI uyumlu bulut STT istemcisi (`SttClient`) eklendi.
- Instead of a keyboard (IME): assistant-key takeover (VoiceInteractionService), a listening screen and typing through accessibility, for Android TV. / Klavye (IME) yerine Android TV'ye uygun: asistan tuşu devralma (VoiceInteractionService), dinleme ekranı, erişilebilirlik ile metin yazma.
- androidx / Compose dependencies were removed; only Android framework APIs are used. / androidx / Compose bağımlılıkları kaldırıldı; yalnızca Android framework API'leri kullanıldı.
- Added: remote-friendly settings screen, SmartTube / Nuvio result cards, weather panel, English + Turkish UI, installer scripts. / Eklendi: kumandayla gezilen ayar ekranı, SmartTube / Nuvio sonuç kartları, hava paneli, İngilizce + Türkçe arayüz, kurulum betikleri.

## Bundled third-party files / Pakete dahil üçüncü taraf dosyalar

| File / Dosya | License / Lisans |
|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Apache License 2.0 (Gradle Inc.) |
| `LICENSE.txt` | GNU GPL v3 text (Free Software Foundation) |

The APK contains no third-party libraries. The app icon, TV banner and the weather icons (drawn with Canvas) were created for this project and are covered by the same GPL-3.0 license.
APK içinde üçüncü taraf kütüphane yoktur. Uygulama simgesi, TV afişi ve (Canvas ile çizilen) hava ikonları bu proje için hazırlanmıştır ve aynı GPL-3.0 lisansı altındadır.

## Services used at run time / Çalışma anında kullanılan hizmetler

- Weather / Hava durumu: [Open-Meteo.com](https://open-meteo.com/) (data CC BY 4.0; the free API is for non-commercial use / ücretsiz API ticari olmayan kullanım içindir).
- Movies & series / Film ve dizi: Cinemeta (Stremio) and optionally / ve isteğe bağlı TMDB. *This product uses the TMDB API but is not endorsed or certified by TMDB.*
- Speech recognition / Konuşma tanıma: the OpenAI-compatible API chosen by the user (default Groq) with the **user's own API key**; that provider's terms apply. / kullanıcının seçtiği OpenAI uyumlu API (varsayılan Groq), **kullanıcının kendi anahtarıyla**; sağlayıcının koşulları geçerlidir.
- YouTube search / arama (keyless web search or optional YouTube Data API key / anahtarsız web araması veya isteğe bağlı YouTube Data API anahtarı).

## Trademarks / Markalar

YouTube, SmartTube, Nuvio, Groq, OpenAI, TMDB, Stremio and Open-Meteo are trademarks or products of their respective owners. This project is not affiliated with, endorsed by or sponsored by them, and it does not include their software.
İlgili sahiplerinin marka/ürünleridir; bu proje onlarla bağlantılı değildir, onlarca onaylanmamıştır ve yazılımlarını içermez.

## Screenshots / Ekran görüntüleri

`docs/img/` contains screenshots of this app's own interface. Thumbnails and posters visible in them belong to their owners and are shown only to illustrate the UI.
`docs/img/` bu uygulamanın kendi arayüzünün ekran görüntülerini içerir; içlerindeki küçük resim ve afişler sahiplerine aittir, yalnızca arayüzü göstermek için yer alır.
