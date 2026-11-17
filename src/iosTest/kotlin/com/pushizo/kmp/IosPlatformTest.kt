@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pushizo.kmp

import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS köprüsü: Objective-C cephesinden gelen Foundation türlerinin ortak modele çevrilmesi ve gerçek cepheyle çağrılar. */
internal actual fun testPermissionHost(): PushizoPermissionHost = platform.UIKit.UIViewController()

internal actual fun testContext(): PushizoContext = PushizoApplicationContext

/**
 * Gerçek cephede askıya alınan çağrıyı tamamlanana kadar bekler. Cephe completion block'larını ana kuyruğa gönderdiğinden
 * (testler ana iş parçacığında koşar) ana çalışma döngüsü sonuç gelene kadar döndürülür.
 */
private fun <T> awaitOnMainRunLoop(block: suspend () -> T): Result<T> {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    val deadline = NSDate().timeIntervalSince1970 + 15
    while (result == null && NSDate().timeIntervalSince1970 < deadline) {
        NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
    }
    return checkNotNull(result) { "askıya alınan çağrı 15 sn içinde tamamlanmadı" }
}

class IosPlatformTest {
    @Test
    fun hataAlaniAndroidIleAyniIstisnaTurlerineCevrilir() {
        fun error(code: Long, domain: String = PZ_ERROR_DOMAIN) =
            NSError.errorWithDomain(domain, code, mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "mesaj $code"))
        assertIs<IllegalStateException>(error(1).toKotlinException())
        assertIs<IllegalStateException>(error(3).toKotlinException())
        val invalid = error(4).toKotlinException()
        assertIs<IllegalArgumentException>(invalid)
        assertEquals("mesaj 4", invalid.message)
        assertIs<RuntimeException>(error(5).toKotlinException())
        // Platformun desteklemediği özellik (ör. iOS 16.1 öncesinde Live Activities) ve sistem reddi.
        assertIs<UnsupportedOperationException>(error(6).toKotlinException())
        assertFalse(error(7).toKotlinException() is UnsupportedOperationException)
        assertIs<RuntimeException>(error(7).toKotlinException())
        assertIs<RuntimeException>(error(1, "NSURLErrorDomain").toKotlinException())
    }

    @Test
    fun tarihEpochMilisaniyeyeCevrilir() {
        assertEquals(1_700_000_000_500L, NSDate.dateWithTimeIntervalSince1970(1_700_000_000.5).toEpochMillis())
        assertNull((null as NSDate?).toEpochMillis())
    }

    @Test
    fun bayrakDegeriFoundationTurlerindenOrtakTurlereCevrilir() {
        assertEquals(true, NSNumber(bool = true).toCommonFlagValue())
        assertEquals(3, NSNumber(long = 3).toCommonFlagValue())
        assertEquals(5_000_000_000L, NSNumber(longLong = 5_000_000_000L).toCommonFlagValue())
        assertEquals(1.5, NSNumber(double = 1.5).toCommonFlagValue())
        assertNull(NSNull.`null`().toCommonFlagValue())
        assertEquals("metin", "metin".toCommonFlagValue())
        val nested = mapOf<Any?, Any?>("a" to NSNumber(long = 1), "b" to listOf("x", NSNull.`null`()), "c" to NSNumber(bool = false)).toCommonFlagValue()
        assertEquals(mapOf("a" to 1, "b" to listOf("x", null), "c" to false), nested)
    }

    @Test
    fun bildirimIzniTamVeGeciciIzindeVerilmisSayilir() {
        assertTrue(isPermissionGranted(UNAuthorizationStatusAuthorized))
        assertTrue(isPermissionGranted(UNAuthorizationStatusProvisional))
        assertFalse(isPermissionGranted(UNAuthorizationStatusDenied))
        assertFalse(isPermissionGranted(UNAuthorizationStatusNotDetermined))
    }

    @Test
    fun sozlukDonusumleri() {
        assertEquals(mapOf("plan" to "pro"), mapOf<Any?, Any?>("plan" to "pro", 1 to "x", "n" to NSNumber(long = 2)).toStringMap())
        assertEquals(mapOf<Any?, Any?>("a" to "b"), mapOf("a" to "b").toObjC())
    }

    @Test
    fun gercekCepheBaslatilmadanVarsayilanlariDondurur() {
        // Ortak API → iOS platformu → Pushizo.xcframework Objective-C cephesi (simülatörde gerçek framework).
        assertFalse(IosPushizoPlatform.isInitialized)
        assertNull(IosPushizoPlatform.externalId)
        assertTrue(IosPushizoPlatform.getTags().isEmpty())
        assertFalse(IosPushizoPlatform.geofencingEnabled)
        assertTrue(IosPushizoPlatform.isFeatureEnabled("yeni_sepet", default = true))
        assertNull(IosPushizoPlatform.featureFlagValue("yok"))
        assertFalse(IosPushizoPlatform.subscriptionState.optedIn)
    }

    @Test
    fun androidTeslimatGuclendirmeIosTaZararsizdir() {
        val status = IosPushizoPlatform.deliveryStatus()
        assertFalse(status.supported)
        assertEquals(PushizoDeliveryRisk.NONE, status.risk)
        assertTrue(status.recommendedSettings.isEmpty())
        assertFalse(IosPushizoPlatform.shouldShowDeliveryGuidance(14))
        assertFalse(IosPushizoPlatform.openDeliverySettings(PushizoApplicationContext, PushizoDeliverySetting.AUTO_START))
        var result: Boolean? = null
        IosPushizoPlatform.showDeliveryGuidance(platform.UIKit.UIViewController(), PushizoDeliverySetting.AUTO, null) { result = it }
        assertEquals(false, result)
    }

    @Test
    fun sohbetIosTaBagliVeBaslatilmadanAndroidIleAyniHatalariVerir() {
        // Gerçek `PZChat` cephesi: durum özellikleri yerelden okunur.
        assertTrue(IosPushizoPlatform.chatSupported)
        assertNull(IosPushizoPlatform.chatVisitorId)
        assertEquals(0, IosPushizoPlatform.chatUnreadCount)
        assertFalse(IosPushizoPlatform.chatIsListening)
        val listener = IosPushizoPlatform.addChatListener(object : PushizoChatListener {})
        listener.remove()
        listener.remove()
        IosPushizoPlatform.addChatOpenListener { }.remove()
        // SDK başlatılmadan değer döndüren çağrılar cephenin `notInitialized` hatasını `IllegalStateException` olarak verir.
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatStart("Ali", null, "tr") }.exceptionOrNull())
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatGetMessages(5, null, 50) }.exceptionOrNull())
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatSendMessage("Selam", null) }.exceptionOrNull())
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatSendAttachment(byteArrayOf(1, 2, 3), "a.png", "image/png", null) }.exceptionOrNull())
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatMarkRead(1) }.exceptionOrNull())
        assertIs<IllegalStateException>(awaitOnMainRunLoop { IosPushizoPlatform.chatGetConfig(null) }.exceptionOrNull())
        assertFailsWith<IllegalStateException> { IosPushizoPlatform.chatAttachmentUrl("att_1", download = false) }
    }

    @Test
    fun hazirSohbetEkraniAcikDegilkenKapatmaZararsizdir() {
        assertFalse(IosPushizoPlatform.chatPresented)
        assertFalse(IosPushizoPlatform.chatHide(animated = false))
        assertFalse(IosPushizoPlatform.chatPresented)
    }

    @Test
    fun sohbetOlayiTureGoreOrtakDinleyiciyeAktarilir() {
        val seen = mutableListOf<String>()
        val listener = object : PushizoChatListener {
            override fun onMessage(message: PushizoChatMessage) { seen += "message ${message.seq}" }
            override fun onTyping(typing: Boolean, name: String?) { seen += "typing $typing $name" }
            override fun onAgentRead(seq: Long) { seen += "read $seq" }
            override fun onUnreadCountChanged(count: Int) { seen += "unread $count" }
            override fun onConversationUpdated(conversationId: String, status: String, botActive: Boolean) { seen += "conversation $conversationId $status $botActive" }
            override fun onConnectionChanged(connected: Boolean) { seen += "connection $connected" }
            override fun onError(code: String, message: String?) { seen += "error $code $message" }
        }
        val message = PushizoChatMessage("m", 3, null, "bot", null, "x", emptyList(), null, "sent", null)
        listener.dispatchChatEvent("message", message = message)
        listener.dispatchChatEvent("message")
        listener.dispatchChatEvent("typing", typing = true, name = "Ayşe")
        listener.dispatchChatEvent("read", seq = 3)
        listener.dispatchChatEvent("unread", unreadCount = 1)
        listener.dispatchChatEvent("conversation", conversationId = "conv_1", status = "resolved", botActive = false)
        listener.dispatchChatEvent("connection", connected = true)
        listener.dispatchChatEvent("error", code = "stream", errorMessage = "koptu")
        listener.dispatchChatEvent("bilinmeyen")
        assertEquals(
            listOf("message 3", "typing true Ayşe", "read 3", "unread 1", "conversation conv_1 resolved false", "connection true", "error stream koptu"),
            seen,
        )
    }

    @Test
    fun ozellikDegeriVeProfilFoundationTurlerineCevrilir() {
        assertNull(null.toObjCPropertyValue())
        assertEquals("Ali", "Ali".toObjCPropertyValue())
        assertEquals(34, 34.toObjCPropertyValue())
        assertEquals(true, true.toObjCPropertyValue())
        // Koleksiyon içindeki null `NSNull` olur; iç içe harita ve liste özyinelemeli çevrilir.
        val nested = mapOf("il" to "İzmir", "kat" to null, "etiketler" to listOf("a", null, mapOf(1 to 2.5))).toObjCPropertyValue()
        assertEquals(mapOf<Any?, Any?>("il" to "İzmir", "kat" to NSNull.`null`(), "etiketler" to listOf("a", NSNull.`null`(), mapOf<Any?, Any?>("1" to 2.5))), nested)
        assertEquals(listOf<Any?>("x", 1), arrayOf<Any?>("x", 1).toObjCPropertyValue())
        assertEquals("HIGH", PushizoDeliveryRisk.HIGH.toObjCPropertyValue())

        val profile = PushizoUserProfile("a@ornek.com", "+905551112233", mapOf("p" to "1"), mapOf("crm" to "c1")).toNative()
        assertEquals("a@ornek.com", profile.email())
        assertEquals("+905551112233", profile.sms())
        assertEquals(mapOf("p" to "1"), profile.tags().toStringMap())
        assertEquals(mapOf("crm" to "c1"), profile.aliases().toStringMap())
        val empty = PushizoUserProfile().toNative()
        assertNull(empty.email())
        assertTrue(empty.tags().isEmpty())

        assertEquals(3UL, byteArrayOf(1, 2, 3).toNSData().length)
        assertEquals(0UL, ByteArray(0).toNSData().length)
    }

    @Test
    fun gercekCepheIleYeniCagrilarBaslatilmadanZararsizdir() {
        // Kullanıcı özelliği, reklam kimliği ve kimlikli olay yerel SDK'ya gider; başlatılmadan kuyruk yoktur, çökmez.
        IosPushizoPlatform.setProperty("first_name", "Ali")
        IosPushizoPlatform.setProperty("adres", mapOf("il" to "İzmir", "kat" to 3))
        // İç içe null `NSNull` olarak gider; yerel SDK bu değeri günlüğe yazıp yok sayar (çökmez).
        IosPushizoPlatform.setProperty("etiketler", listOf("a", null))
        IosPushizoPlatform.setProperty("eski", null)
        IosPushizoPlatform.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d")
        IosPushizoPlatform.setAdvertisingId(null)
        IosPushizoPlatform.sendEvent("purchase", mapOf("sku" to "a1"), 49.9, "ord_12345678")
        IosPushizoPlatform.sendEvent("view", emptyMap(), null, null)
        // Gözlemciler: kayıt ve kaldırma (birden çok kez) çökmeden çalışır.
        val flags = IosPushizoPlatform.addFeatureFlagObserver { }
        flags.remove()
        flags.remove()
        val inbox = IosPushizoPlatform.addInboxObserver { }
        inbox.remove()
        inbox.remove()
        // Konum izni yerel SDK'dan okunur (istem gösterilmez).
        assertFalse(IosPushizoPlatform.locationPermissionGranted)
        assertFalse(IosPushizoPlatform.locationBackgroundPermissionGranted)
    }

    @Test
    fun profilliGirisYerelCagriyaGiderVeBaslatilmadanHataVerir() {
        val profile = PushizoUserProfile(email = "ali@ornek.com", tags = mapOf("plan" to "pro"))
        val error = assertFailsWith<IllegalStateException> { IosPushizoPlatform.login("user_42", profile, null, null) }
        assertTrue(error.message.orEmpty().isNotBlank())
        // Giriş yapılmadı: köprü artık alanları ayrı ayrı eklemez.
        assertNull(IosPushizoPlatform.externalId)
        assertTrue(IosPushizoPlatform.emails.isEmpty())
        assertTrue(IosPushizoPlatform.getTags().isEmpty())
    }

    @Test
    fun uygulamaIciYasamDongusuDinleyicisiKaldirilanaKadarTutulur() {
        // Cephe dinleyiciyi zayıf tutar; köprü uyarlayıcıyı kayıt kaldırılana kadar güçlü tutar.
        val before = IosPushizoPlatform.retainedLifecycleListenerCount
        val first = IosPushizoPlatform.addInAppLifecycleListener(object : PushizoInAppLifecycleListener {})
        val second = IosPushizoPlatform.addInAppLifecycleListener(object : PushizoInAppLifecycleListener {})
        assertEquals(before + 2, IosPushizoPlatform.retainedLifecycleListenerCount)
        first.remove()
        first.remove()
        assertEquals(before + 1, IosPushizoPlatform.retainedLifecycleListenerCount)
        second.remove()
        assertEquals(before, IosPushizoPlatform.retainedLifecycleListenerCount)
    }

    @Test
    fun canliEtkinliklerGenelTurleBaglidirVeBaslatilmadanHataVerir() {
        assertTrue(IosPushizoPlatform.liveActivitiesSupported)
        assertTrue(IosPushizoPlatform.defaultLiveActivityType.orEmpty().isNotBlank())
        assertFailsWith<IllegalStateException> { IosPushizoPlatform.setupDefaultLiveActivity() }
        assertFailsWith<IllegalStateException> {
            IosPushizoPlatform.startDefaultLiveActivity("order_42", mapOf("title" to "Sipariş"), mapOf("step" to 2, "done" to false))
        }
    }

    @Test
    fun androideOzguCagrilarEtkisizdirVeElleKurulanYerlesimRaporlanmaz() {
        IosPushizoPlatform.registerAdm("amzn1.adm-registration.v3.x")
        IosPushizoPlatform.removeNotification(42)
        // Elle kurulan (cephe nesnesi olmayan) yerleşim mesajında raporlama yok sayılır.
        val message = PushizoInAppMessage("iam_1", null, "modal", "blocks", null, null, null, dismissible = true, blocks = emptyList())
        assertNull(message.nativeIos())
        IosPushizoPlatform.placementReportImpression(message)
        IosPushizoPlatform.placementReportClick(message, "btn_1")
        IosPushizoPlatform.placementReportDismiss(message)
    }

    @Test
    fun gercekCepheIleInAppTiklamaDinleyicisiVeArkaPlanIzni() {
        // Gerçek Objective-C cephesi: dinleyici kaydı/kaldırma ve yerel konum izni okuması çökmeden çalışır.
        val registration = IosPushizoPlatform.addInAppClickListener { }
        registration.remove()
        registration.remove()
        assertFalse(IosPushizoPlatform.locationBackgroundPermissionGranted)
    }
}
