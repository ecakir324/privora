# Android Studio Olmadan APK Alma

1. GitHub'da private repo aç.
2. Bu ZIP içindeki dosyaları repo ana dizinine yükle.
3. Repo ana sayfasında şunlar aynı seviyede görünmeli:

```
app/
.github/
build.gradle
settings.gradle
README_TR.md
```

4. Actions sekmesine gir.
5. Build Debug APK workflow'una gir.
6. Run workflow de.
7. Yeşil tikten sonra Artifacts bölümünden `privora-v3-debug-apk` indir.
8. ZIP'i aç, `app-debug.apk` dosyasını telefona gönder ve kur.

Not: `.github` klasörü bilgisayarda gizli görünebilir. GitHub'da görünmesi gerekir.
