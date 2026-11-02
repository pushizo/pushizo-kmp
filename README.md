# Pushizo Kotlin Multiplatform SDK

Ortak Kotlin kodundan Pushizo'yu çağırmak için `com.pushizo:pushizo-kmp`. Mantığı yeniden yazmaz: her platformda yerel SDK'ya devreder; teslimat, kuyruk, oturum ve in-app çizimi yerel SDK'da kalır (ADR-0055).

**Durum:** Android hedefi hazır ve test edildi. iOS hedefi henüz yok: Kotlin/Native Swift'i doğrudan çağıramadığı için iOS SDK'nın Objective-C köprüsünü (Faz 29) bekliyor ve yalnızca macOS'ta derlenir. Paket iOS hedefiyle birlikte Maven Central'a çıkacak; o zamana kadar kaynaktan kullanılır.

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
            api("com.pushizo:pushizo-kmp:0.1.0")
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

Bildirim kutusu, tercih merkezi, canlı sohbet ve özel in-app çizici ortak API'de yok. Android'de `api` bağımlılığı sayesinde `androidMain` içinden `com.pushizo.sdk.Pushizo` ile doğrudan çağrılır.

## Geliştirme

```bash
cd sdk/kmp-mobile
gradle :testDebugUnitTest :assembleRelease
```

Android SDK `includeBuild("../android")` ile kaynaktan derlenir. Örnek uygulama: `examples/kmp-app`.
