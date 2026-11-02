# ApexPulse — Proje Sonu Teknik Dökümantasyonu

> **Proje Adı:** ApexPulse (`com.wheelcharm.apexpulse`)  
> **Sürüm:** `v1.1.0` (Build `1`, Target SDK `35`, Min SDK `26`)  
> **Mimari Yaklaşım:** Yerel-Öncelikli (Local-First) Hibrit Sensör Füzyonu + OBD-II (ELM327) ECU Telemetrisi + Firebase Bulut Senkronizasyonu  
> **Geliştirici:** **Yiğit (`YigitEXP`)** — *CircuitVR & Edge-to-Cloud Sistem Mimarisi*

---

## 1. Yönetici Özeti ve Proje Vizyonu

**ApexPulse**, akıllı telefonun dahili ataletsel sensörlerini (**IMU / İvmeölçer + GPS**) aracın **OBD-II (ELM327)** elektronik kontrol ünitesi (ECU) verileriyle gerçek zamanlı birleştiren, uçtan uca bir **Otomotiv Telemetri, Mekanik Arıza Teşhis, Canlı Navigasyon Kokpiti ve Oyunlaştırılmış Sürüş Analiz Platformudur**.

Donanım ve yazılım dünyasını birleştirirken yaşanan veri uyumsuzluklarını, sensör gürültülerini ve bağlantı kopmalarını tolere etmek üzere **4 ana mühendislik odağıyla** tasarlanmıştır:

1. **Hibrit Sensör Füzyonu ve Sinyal İşleme:** Sabit telefon tutucu veya orta konsol/cep kullanımına göre dinamik katsayılı Alçak Geçiren Filtre (LPF), Yüksek Geçiren Yerçekimi Filtresi (HPF) ve Haversine + Kalman tabanlı yörünge doğrulaması.
2. **Gerçek Zamanlı OBD-II (ELM327) Mekanik Teşhis:** Bluetooth SPP (`RFCOMM`) ve Wi-Fi (`TCP/IP`) üzerinden motor devri (`010C`), araç hızı (`010D`), motor yükü (`0104`), soğutma suyu sıcaklığı (`0105`) ve gaz kelebeği (`0111`) okuma; hız/devir oranındaki sapmadan **Debriyaj Kaçırma (Clutch Slip)** tespiti.
3. **Sıfır Veri Kaybı (Zero-Data-Loss) ve Güvenli Bulut Mimarisi:** Her 5 dakikalık sürüş periyodunda Room (`WRITE_AHEAD_LOGGING`) tabanlı parçalı (chunked) disk kaydı, çökme sonrası yetim sürüş kurtarma (`Crash Recovery`) ve katı şema doğrulamalı **Firebase Firestore (`eur3`)** güvenlik kuralları.
4. **Yarış Kokpiti & Oyunlaştırılmış Kullanıcı Deneyimi:** Canlı **G-Kuvveti Sürtünme Çemberi (G-Bowl)**, **Dragy tarzı `0-60 km/h` ve `0-100 km/h` performans kronometresi**, **Ön Cam HUD (Head-Up Display) Yansıtma Modu**, özel `Canvas` vektör çizimleri ve küresel liderlik tablosu.

---

## 2. Modül ve Kaynak Kod Dizin Yapısı

| Katman | Dosya / Sınıf | Sorumluluk Özeti |
| :--- | :--- | :--- |
| **Domain / Model** | `app/src/main/java/com/wheelcharm/telemetry/domain/model/TelemetryModels.kt` | Telemetri çerçeveleri (`TelemetryFrame`), OBD-II modelleri (`ObdLiveFrame`, `ObdBluetoothDeviceItem`), navigasyon durumu (`ActiveNavigationState`), performans metrikleri (`PerformanceTelemetryMetrics`) ve rozet tanımları. |
| **Domain / UseCase** | `app/src/main/java/com/wheelcharm/telemetry/domain/usecase/DriveAnalyticsEngine.kt` | Haversine mesafe hesabı, 3-eksenli G-kuvveti büyüklüğü, alçak geçiren filtre, agresif fren/ivme/viraj tespiti, mekanik sağlık ve sürüş puanı algoritmaları. |
| **Data / OBD-II** | `app/src/main/java/com/wheelcharm/telemetry/data/obd/Elm327ObdManager.kt` | Bluetooth SPP ve Wi-Fi TCP üzerinden ELM327 başlatma (`ATZ`, `ATE0`, `ATSP0`), hex PID ayrıştırma (`010C`, `010D`, `0104`, `0105`, `0111`) ve debriyaj kaçırma analizi. |
| **Data / Sensor** | `app/src/main/java/com/wheelcharm/telemetry/data/sensor/HybridTelemetryService.kt` | 50 Hz IMU + GPS örnekleme, `PARTIAL_WAKE_LOCK`, `0-60 / 0-100 km/h` otomatik performans ölçümü, 5 dakikalık parçalı Room flush döngüsü. |
| **Data / Sensor** | `app/src/main/java/com/wheelcharm/telemetry/data/sensor/DriveTransitionReceiver.kt` | Google Play `ActivityRecognition` üzerinden `IN_VEHICLE` geçişlerini yakalayıp servisi otonom başlatma ve durdurma. |
| **Data / Local** | `app/src/main/java/com/wheelcharm/telemetry/data/local/TelemetryDatabase.kt` | `WRITE_AHEAD_LOGGING` (WAL) modunda çalışan, Thread-Safe Singleton Room veritabanı (`DriveSummaryEntity`, `DriveRouteChunkEntity`, `VehicleProfileEntity`). |
| **Data / Local** | `app/src/main/java/com/wheelcharm/telemetry/data/local/TelemetryDao.kt` | Sürüş özetleri, 5 dakikalık sıkıştırılmış JSON rota parçaları ve senkronizasyon bekleyen kayıtlar için asenkron SQL sorguları. |
| **Data / Repository** | `app/src/main/java/com/wheelcharm/telemetry/data/repository/TelemetryRepository.kt` | Yerel Room veritabanı ile Firebase Firestore (`leaderboard`, `users/{uid}/drives`) arasındaki çift yönlü senkronizasyon ve çökme kurtarma (`recoverOrphanedDrivesIfNeeded`). |
| **UI / ViewModel** | `app/src/main/java/com/wheelcharm/telemetry/ui/viewmodel/TelemetryViewModel.kt` | UI durum yönetimi (`TelemetryUiState`), araç/sıralama filtreleme, OBD cihaz listeleme ve navigasyon/HUD durum kontrolü. |
| **UI / Screens** | `app/src/main/java/com/wheelcharm/telemetry/ui/screens/WheelCharmApp.kt` | 5 sekmeli Jetpack Compose arayüzü, özel `Canvas` çizimleri, Canlı Sürüş Kokpiti, G-Bowl radarı, OBD-II bağlantı diyaloğu ve `YigitEXP / CircuitVR` geliştirici kartı. |
| **UI / Activity** | `app/src/main/java/com/wheelcharm/telemetry/ui/MainActivity.kt` | Çalışma zamanı izin yönetimi (Konum, Bluetooth, Bildirim, Aktivite Tanıma), servis intent köprüleri ve Google Haritalar sesli navigasyon entegrasyonu. |
| **Test** | `app/src/test/java/com/wheelcharm/telemetry/domain/usecase/DriveAnalyticsEngineTest.kt` | Sinyal filtreleme, Haversine, G-kuvveti, OBD-II PID ayrıştırma ve debriyaj kaçırma algoritmalarını doğrulayan 11 kapsamlı JUnit4 birim testi. |

---

## 3. Matematiksel Modeller ve Algoritmik Altyapı

### 3.1. Dinamik Alçak Geçiren Filtre (Low-Pass Filter) ve Yerçekimi İzolasyonu
Telefonun araç içindeki konumuna (`DevicePlacementMode`) göre ivmeölçer verisine ayrık zamanlı Üstel Hareketli Ortalama (EMA) uygulanır:

$$\hat{a}[n] = \alpha \cdot a[n] + (1 - \alpha) \cdot \hat{a}[n-1]$$

- **Sabit Tutucu (`RIGID_MOUNT`):** $\alpha = 0.72$ (yüksek dinamik tepki)
- **Orta Konsol / Cep (`LOOSE_CONSOLE`):** $\alpha = 0.35$ (sarsıntı ve titreşim bastırma)
- Donanımda `TYPE_LINEAR_ACCELERATION` bulunmadığında `TYPE_ACCELEROMETER` verisinden yerçekimi bileşeni $\alpha_g = 0.85$ katsayılı yüksek geçiren filtreyle ayrıştırılır.

### 3.2. 3-Eksenli G-Kuvveti ve Sürüş Olay Eşikleri
Boylamsal ($a_{\text{lon}}$), yanal ($a_{\text{lat}}$) ve dikey ($a_{\text{vert}}$) ivme bileşenlerinden toplam G-kuvveti büyüklüğü hesaplanır:

$$G_{\text{total}} = \frac{\sqrt{a_{\text{lon}}^2 + a_{\text{lat}}^2 + a_{\text{vert}}^2}}{g_0}, \quad g_0 = 9.80665\text{ m/s}^2$$

- **Sert Fren (`HARSH_BRAKE`):** $a_{\text{lon}} / g_0 \le -0.45\text{ G}$
- **Sert İvmelenme (`HARSH_ACCEL`):** $a_{\text{lon}} / g_0 \ge +0.40\text{ G}$
- **Agresif Viraj (`SHARP_CORNER`):** $|a_{\text{lat}}| / g_0 \ge 0.50\text{ G}$

### 3.3. OBD-II (ELM327) PID Çözümleme ve Debriyaj Kaçırma (Clutch Slip) Tespiti
`Elm327ObdManager` üzerinden okunan onaltılık (Hex) yanıtlar standart SAE J1979 formülleriyle dönüştürülür:

$$\text{RPM (010C)} = \frac{256 \cdot A + B}{4}, \quad V_{\text{ECU}}\text{ (010D)} = A\text{ km/h}, \quad T_{\text{coolant}}\text{ (0105)} = A - 40^\circ\text{C}$$

**Debriyaj Kaçırma Algoritması (`detectClutchSlip`):**  
Gaz kelebeği açıklığı $\ge \%35$ ve hız $\ge 25\text{ km/h}$ iken, motor devri $\Delta \text{RPM} > 450$ arttığı halde $\text{km/h} / 1000\text{ RPM}$ aktarım oranı bir önceki örnekleme adımına göre $\%18$'den fazla düşüyorsa (`ratio < prevRatio * 0.82`), mekanik güç aktarım kaybı (**Clutch Slip**) bayrağı tetiklenir.

---

## 4. Test, Derleme ve Dağıtım Komutları

```powershell
# Birim testlerini çalıştırma ve Debug APK üretme
.\gradlew.bat testDebugUnitTest assembleDebug

# Firebase yapılandırma ve güvenlik kurallarını deploy etme
.\deploy_firebase.bat
```
