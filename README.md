# TV Loop Player (Android TV)

TV açılınca kendiliğinden başlar, içindeki videoyu sonsuz döngüde oynatır.
Videoyu internetten indirip TV'nin hafızasına kaydeder ve oradan oynatır. İnternet giderse oynatma durmaz.

## 1) APK'yı derle (bilgisayara bir şey kurman gerekmez)
1. GitHub'da yeni bir repo aç ve bu klasördeki her şeyi yükle (`.github` klasörü de dahil).
2. **Actions** sekmesinde "Build APK" kendiliğinden çalışır (yaklaşık 5 dakika).
3. İş bitince sayfanın altındaki **TvLoopPlayer-apk** dosyasını indir ve zip'i aç → `app-debug.apk`.
(Android Studio kullanırsan klasörü aç ve Build > Build APK de diyebilirsin.)

## 2) TV'ye kur
- APK'yı USB belleğe at. TV'de bir dosya yöneticisi (ör. "File Commander", "X-plore") ile aç, kur.
- TV, "bilinmeyen kaynaklar" izni isteyebilir: Ayarlar > Güvenlik > Bilinmeyen kaynaklar > dosya yöneticisine izin ver.
- İlk açılışta "diğer uygulamaların üzerinde göster" ekranı gelirse uygulamaya izin ver. Android 10 ve sonrasında TV açılırken uygulamanın başlayabilmesi için bu izin gerekli.

## 3) Videoyu uzaktan yükle / değiştir
Uygulama 15 dakikada bir küçük bir `manifest.json` dosyasına bakar:
```json
{ "version": 1, "url": "https://.../video.mp4" }
```
- Videoyu doğrudan indirilebilen bir yere yükle: Dropbox (linkin sonundaki `dl=0`'ı `dl=1` yap), kendi sunucun, Cloudflare R2, GitHub Releases vb.
  *Büyük dosyalarda Google Drive linkleri sorun çıkarır (virüs tarama sayfası).*
- `manifest.json` dosyasını da erişilebilir bir yerde tut (ör. Dropbox `dl=1` linki veya GitHub Gist "Raw" linki).
- **Video değiştirmek için:** yeni videoyu yükle, manifestteki `url`'yi güncelle ve `version`'ı 1 artır. TV en geç 15 dakika içinde yeni videoyu indirir. İndirme bitene kadar eski video oynamaya devam eder, bittiğinde yenisine geçer.
- Bağlantı koparsa indirme kaldığı yerden devam eder (sunucu destekliyorsa).

## Videoyu değiştirmek (bu kurulumda)
GitHub'da reponun kökündeki `manifest.json` dosyasını aç, kalem simgesine tıkla:
- `url`: Google Drive paylaşım linkini olduğu gibi yapıştırabilirsin. Dosyanın paylaşımı "Bağlantıya sahip olan herkes" olmalı.
- `version`: her değişiklikte 1 artır.
"Commit changes" dedikten sonra TV yeni videoyu en geç ~20 dakika içinde indirir.

## Manifest adresini ayarlama
- TV'de kumandanın **MENU** tuşuna bas ya da **OK tuşuna uzun bas** → adresi gir → "Kaydet ve kontrol et".
- Ya da derlemeden önce `app/build.gradle.kts` içindeki `DEFAULT_MANIFEST_URL` satırına kendi adresini yaz. Böylece TV'de hiçbir şey ayarlaman gerekmez.

## Notlar
- En sağlam yöntem uygulamayı TV'nin ana ekranı yapmak. Kurulumdan sonra Home tuşuna bastığında "varsayılan ana ekran" sorulursa TV Loop Player'ı seç. Bu durumda TV her açıldığında doğrudan uygulama gelir.
- Video formatı olarak MP4 (H.264 + AAC) öner; her TV bunu oynatır.
- Uygulamanın paket adı: `com.pion.tvloop`.
