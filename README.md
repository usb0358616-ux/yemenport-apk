# نظام المنافذ والجوازات (Smart Border Ports & Passports System)

تطبيق أندرويد أصلي (**Native Android**) مبني بلغة **Kotlin** وواجهة **Jetpack Compose (Material 3)** مع دعم كامل للغة العربية واتجاه **RTL** وخط **Cairo** للعمل الميداني في المنافذ الحدودية اليمنية (المطارات، الموانئ البحرية، والمنافذ البرية).

---

## أبرز القدرات والمكونات
- **العمل بدون إنترنت أولاً (100% Offline-First + WorkManager)**:
  - قاعدة بيانات **Room (`AppDatabase` v2)** مع محولات أنواع (`BorderTypeConverters`) لكيانات `TravelEntity` و`TravelerEntity` و`AuditLogEntity` وتشفير التفضيلات الحساسة عبر **Android Keystore (`AES-256-GCM`)**.
  - طبقة مزامنة تلقائية في الخلفية عبر **`WorkManager` (`SovereignSyncWorker`)** لجدولة وإعادة محاولة رفع السجلات المعلقة وسجل التدقيق فور توفر الإنترنت، بالتكامل مع **Firebase Firestore** بعد المصادقة الموحدة عبر **Google Credential Manager**.
- **الماسح الذكي للجوازات والوثائق (`CameraX + ML Kit + ZXing QR`)**:
  - إطار محاذاة بصري لسطرَي `MRZ` ورموز `QR` مع زر تحكم بالفلاش (`Flashlight`).
  - قراءة `MRZ (TD3 / TD1)` والتحقق من أرقام الفحص (`Check Digits`) وحساب العمر وصلاحية الجواز وتطبيع الأرقام الهندية (`٠-٩`).
  - قراءة رموز `QR` الخاصة بالعبور (`YEM-BORDER|...`) بدعم كامل للترميز العربي `UTF-8` من الكاميرا أو صور المعرض.
  - فحص تلقائي مؤجل (`Debounce`) مع قوائم الممنوعين عند الإدخال اليدوي.
- **محرك الحراسة الأمنية والمباينة (`WatchlistGuardEngine`)**:
  - مطابقة فورية للمسافرين وبيانات MRZ والمستوردات مع قوائم الممنوعين، أوامر القبض، البلاغات النشطة، الجوازات المضبوطة، وحالات المنع/الارتجاع في `TravelEntity` مع تنبيه صوتي واهتزازي حرج.
- **الأمان البيومتري وإدارة الجلسة (`BiometricAuthentication`)**:
  - قفل تلقائي بالبصمة (`androidx.biometric`) عند فتح التطبيق وبعد الخمول بمهلة قابلة للتخصيص (`1` / `3` / `5` دقائق) محفوظة مشفرة، مع زر قفل فوري للجلسة.
- **الوحدات السيادية والتقارير**:
  - قائمة المنافذ السيادية الـ 19 كاملة مع البحث والتصفية.
  - بطاقة تفاصيل المسافر مع الخط الزمني للدمج الذكي (`Smart Merge`) وسجل العبور وأزرار تسجيل **دخول / مغادرة** مباشرة.
  - تصدير ومشاركة كشوفات `CSV` و`XLSX` و`PDF`، وتوليد **تقرير الوردية اليومي (PDF)** من لوحة القيادة.
  - مركز الاستيراد الموحد (`Excel / CSV / PDF / صور`) والتحليل الذكي عبر **Firebase AI Logic (`Firebase.ai`)**.

---

## البناء والتشغيل (Android Studio & GitHub Actions)

### متطلبات البيئة
- **JDK 17**
- **Android SDK** (`compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`)
- **Gradle 9.3.1** (أو استخدام مسار GitHub Actions المرفق `.github/workflows/android-ci.yml`)

### تجهيز مفتاح التوقيع التجريبي عند الاستنساخ من GitHub
يعتمد إعداد التوقيع الافتراضي على ملف `debug.keystore` في جذر المشروع والمشفر مرجعياً في `debug.keystore.base64`. عند استنساخ المستودع محلياً على جهاز جديد، نفّذ الأمر التالي مرة واحدة قبل البناء:

```bash
base64 -d debug.keystore.base64 > debug.keystore
```

*(تتولى خطوة GitHub Actions في `.github/workflows/android-ci.yml` تنفيذ هذا الأمر تلقائياً قبل البناء.)*

### أوامر البناء والاختبار
```bash
# تشغيل اختبارات الوحدة والمحاكاة المحلية (Robolectric + Room + MRZ + Watchlist + QR)
gradle :app:testDebugUnitTest

# تشغيل الفحص الساكن Android Lint
gradle :app:lintDebug

# بناء حزمة Debug APK
gradle :app:assembleDebug

# بناء حزمة Release APK
gradle :app:assembleRelease
```
