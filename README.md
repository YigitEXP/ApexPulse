# ApexPulse

Android için sürüş telemetrisi, mekanik analiz ve oyunlaştırma uygulaması. Kotlin, Jetpack Compose (koyu tema), Room, Fused Location, Activity Recognition, OBD-II (ELM327) ve Firebase kullanır.

## Özellikler
- Foreground Service ile GPS + ivmeölçer/jiroskop füzyonu; ani fren, ani hızlanma ve keskin viraj tespiti
- Chunked Room flush (500 nokta / 3 dk), yarım kalan sürüşleri kurtarma
- Activity Recognition ile otomatik sürüş başlatma/durdurma
- ELM327 (Bluetooth SPP / Wi-Fi), üstel geri çekilmeli yeniden bağlanma, yalnızca salt-okunur PID whitelist'i
- Vites tahmini, yarım debriyaj tespiti, mekanik yıpranma skoru ve sürücü profili
- Hız dağılımı, kişisel rekorlar, Indy 500 / Coast to Coast / Around Earth rozetleri
- Spotify Wrapped tarzı haftalık/aylık/yıllık hikaye ekranları
- Test için "Simüle Et" düğmesi (Drives sekmesi)

## Derleme
```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```
Firebase için `app/google-services.json` gerekir. Bu dosya repoda yoktur. `deploy_firebase.bat` ile kendi Firebase projenize kurabilir ya da `google-services.json.example` dosyasındaki yapıya bakabilirsiniz. Dosya yoksa uygulama Firebase olmadan da derlenir ve çalışır.

Windows'ta yol Türkçe karakter içeriyorsa derleme çıktısı `C:/Users/<kullanici>/.gradle/apexpulse_build` altına yönlendirilir. `app/build.gradle.kts` dosyasındaki yolu kendi kullanıcı adınıza göre düzenleyin.

## Geliştirici
YigitEXP. Aynı zamanda **CircuitVR** adlı açık kaynak projeyi geliştiriyorum. Python'da eğitilen Edge AI modellerini RTL donanıma çeviren, whitelist ve linter ile güvenliği gözeten, yerel EDA araçları yokken bulutta (Vercel Serverless) devam eden hibrit bir donanım/yazılım tasarım ortamı. Demoyu inceleyip hata ve geliştirme önerilerini bildirirseniz çok sevinirim.
