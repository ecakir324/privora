# Privora v3

Bu sürüm önceki deneme build'inin yerine hazırlanmıştır.

## V3'te düzeltilenler

- Import akışı yeniden yazıldı.
- Hızlı Gizle modunda dosya artık `.bin` uzantısına çevrilmiyor; iç storage'a orijinal uzantıya yakın şekilde alınır.
- Kapak/thumbnail üretimi hata verse bile import iptal olmaz.
- Video kapak saniyesi seçme özelliği korunur.
- Video açma için MediaController eklendi.
- Arayüz koyu tema + grid kart görünümüne çevrildi.
- Foto / Video / Tümü filtreleri eklendi.
- Büyük dosyalarda ana ekran donmasın diye import arka planda çalışır.

## APK build

GitHub'da repo ana dizinine bu dosyaları yükleyin:

- app/
- .github/
- build.gradle
- settings.gradle
- README_TR.md

Sonra:

Actions > Build Debug APK > Run workflow

Artifact adı:

privora-v3-debug-apk

İçinden app-debug.apk çıkar.
