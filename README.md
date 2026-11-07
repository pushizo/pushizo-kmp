<!-- pushizo:header -->
<p align="center">
  <a href="https://pushizo.com"><img src="https://raw.githubusercontent.com/pushizo/.github/main/profile/assets/mark.svg" alt="Pushizo" width="56" height="56"></a>
</p>
<p align="center">
  <a href="https://docs.pushizo.com/docs/sdk/kmp"><img src="https://img.shields.io/badge/docs-docs.pushizo.com-5b4bdb" alt="Docs"></a>
  <a href="https://docs.pushizo.com/docs/api"><img src="https://img.shields.io/badge/API-reference-b44fc4" alt="API reference"></a>
  <a href="https://github.com/pushizo/pushizo-kmp/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-16130f" alt="License: MIT"></a>
</p>
<p align="center"><a href="#english">English</a> · <a href="#türkçe">Türkçe</a></p>

## English

**Pushizo Kotlin Multiplatform SDK** — Shared Kotlin API for push notifications, users, tags and aliases, events, in-app triggers, notification inbox, preferences, feature flags and geofencing. Wraps the native Pushizo Android SDK and the Objective-C surface of the iOS SDK (Pushizo.xcframework).

> **Status:** Android and iOS targets are ready; the Maven Central release (com.pushizo:pushizo-kmp) is pending.<br>
> **Durum:** Android ve iOS hedefleri hazır; Maven Central yayını (com.pushizo:pushizo-kmp) bekleniyor.

**Install from GitHub until then · O zamana kadar GitHub'dan kurulum:**

```bash
git clone --branch 0.2.0 https://github.com/pushizo/pushizo-ios
pushizo-ios/scripts/build-xcframework.sh
git clone --branch v0.2.0 https://github.com/pushizo/pushizo-kmp
gradle -p pushizo-kmp publishToMavenLocal -Ppushizo.xcframework=$PWD/pushizo-ios/build/xcframework/Pushizo.xcframework
```

**After publication · Yayından sonra:**

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            api("com.pushizo:pushizo-kmp:0.2.0")
        }
    }
}
```

The detailed guide below is in Turkish; code samples are language-neutral. Full documentation:
[docs.pushizo.com/docs/sdk/kmp](https://docs.pushizo.com/docs/sdk/kmp) · API reference: [docs.pushizo.com/docs/api](https://docs.pushizo.com/docs/api).

## Türkçe
<!-- /pushizo:header -->

# Pushizo Kotlin Multiplatform SDK

Ortak Kotlin kodundan Pushizo'yu çağırmak için `com.pushizo:pushizo-kmp`. Mantığı yeniden yazmaz: her platformda yerel SDK'ya devreder; teslimat, kuyruk, oturum ve in-app çizimi yerel SDK'da kalır (ADR-0055).

**Durum:** Android (`androidTarget`) ve iOS (`iosArm64`, `iosSimulatorArm64`, `iosX64`) hedefleri hazır ve test edildi. iOS hedefi iOS SDK'nın Objective-C cephesini (`Pushizo.xcframework`, `PZ` önekli sınıflar) cinterop ile sarar ve yalnızca macOS'ta derlenir. Maven Central (`com.pushizo:pushizo-kmp`) yayın yapılandırması hazır; yayın kullanıcıda (`npm run sdk:publish -- maven-kmp --yayinla`, macOS). O zamana kadar kaynaktan kullanılır.

Sunucudan bildirim göndermek için bu paket değil, ayrı olan JVM istemcisi `sdk/kmp` kullanılır.

## Kurulum (kaynaktan)

`settings.gradle.kts`:

```kotlin
includeBuild("../pushizo/sdk/kmp-mobile")
```

Ortak modülün `build.gradle.kts` dosyası:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            api("com.pushizo:pushizo-kmp:0.2.0")
        }
    }
}
```

Android tarafında FCM kurulumu yerel SDK ile aynıdır: `google-services.json` ve `com.google.gms.google-services` eklentisi ([Android rehberi](https://docs.pushizo.com/docs/sdk/android)).

## Kullanım

Başlatma `Application.onCreate` içinde yapılır; `PushizoContext` Android'de `Context` türüdür:

```kotlin
Pushizo.initialize(context, "app_…", PushizoOptions())
```

Bundan sonraki çağrılar ortak koddan yapılabilir:

```kotlin
Pushizo.login("user_42")
Pushizo.User.addTags(mapOf("plan" to "pro"))
Pushizo.User.addEmail("ali@ornek.com")
Pushizo.Session.sendEvent("purchase", mapOf("sku" to "a1"), value = 49.9)
Pushizo.InAppMessages.addTrigger("level", "gold")

val clicks = Pushizo.Notifications.addClickListener { event ->
    println(event.notification.title)
}
clicks.remove()
```

İzin istemi platform nesnesi ister; `PushizoPermissionHost` Android'de `Activity` türüdür. Android'de sonuç ayrıca bildirilir, yoksa abonelik ve izin gözlemcileri güncellenmez:

```kotlin
Pushizo.Notifications.requestPermission(activity)

override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Pushizo.Notifications.onPermissionResult()
}
```

## Kapsam

Ortak API: başlatma, gizlilik onayı, `login` / `logout`, JWT yenileme, etiket, takma ad, dil, e-posta ve SMS, push aboneliği, bildirim izni, tıklama ve ön plan dinleyicileri, olaylar, in-app tetikleri ve konum paylaşımı.

Bildirim kutusu (`Pushizo.Inbox`), tercihler ve rızalar (`Pushizo.Preferences`), özellik bayrakları (`Pushizo.FeatureFlags`)
ve arka plan bölge izleme (`Pushizo.Location.setGeofencingEnabled`) ortak API'dedir; ağ çağrıları `suspend` fonksiyondur:

```kotlin
val page = Pushizo.Inbox.list(limit = 20)
Pushizo.Inbox.markRead(page.items.first().id)
Pushizo.Preferences.set(listOf(PushizoPreferenceInput("promo", optedIn = false)))
if (Pushizo.FeatureFlags.refresh() && Pushizo.FeatureFlags.isEnabled("yeni_sepet")) { /* … */ }
Pushizo.Location.setGeofencingEnabled(true) // yalnızca konum paylaşımı ve arka plan izni varsa izler
```

Canlı sohbet, gömülü yerleşimler ve özel in-app çizici ortak API'de yok. Android'de `api` bağımlılığı sayesinde `androidMain` içinden `com.pushizo.sdk.Pushizo` ile doğrudan çağrılır.

## Geliştirme

```bash
cd sdk/kmp-mobile
gradle :testDebugUnitTest :assembleRelease
# iOS (macOS): önce framework, sonra simülatör testleri (ortak testler + iOS dönüşüm testleri)
../ios/scripts/build-xcframework.sh
gradle :iosSimulatorArm64Test :linkDebugTestIosArm64
```

Android SDK `includeBuild("../android")` ile kaynaktan derlenir; iOS hedefleri `sdk/ios/build/xcframework/Pushizo.xcframework`'e
bağlanır (`-Ppushizo.xcframework=<yol>`). iOS uygulaması başlatmada `Pushizo.initialize(PushizoApplicationContext, "app_…")`
çağırır. Kotlin 2.2.21 (Xcode 26 SDK'sı için gerekli). Örnek uygulama: `examples/kmp-app`.

<!-- pushizo:footer -->
---

## Support · Destek

- **Guide · Rehber:** [docs.pushizo.com/docs/sdk/kmp](https://docs.pushizo.com/docs/sdk/kmp)
- **API reference & OpenAPI · API referansı ve OpenAPI:** [docs.pushizo.com/docs/api](https://docs.pushizo.com/docs/api) ·
  [pushizo-openapi](https://github.com/pushizo/pushizo-openapi)
- **Bugs & ideas · Hata ve öneri:** [Issues](https://github.com/pushizo/pushizo-kmp/issues)
- **Security · Güvenlik:** please do not open public issues; email [security@pushizo.com](mailto:security@pushizo.com) ·
  herkese açık issue açmayın, bu adrese yazın.
- **Help · Destek:** [support@pushizo.com](mailto:support@pushizo.com)

This repository is mirrored automatically from the Pushizo main repository on every release. Please open an issue
instead of a pull request; fixes land in the main repository and ship with the next release.
Bu depo her sürümde Pushizo ana deposundan otomatik yansıtılır. Pull request yerine issue açın; düzeltmeler ana depoda
yapılır ve bir sonraki sürümle buraya gelir.

Released under the MIT License · MIT lisanslıdır — [LICENSE](https://github.com/pushizo/pushizo-kmp/blob/main/LICENSE).
<!-- /pushizo:footer -->
