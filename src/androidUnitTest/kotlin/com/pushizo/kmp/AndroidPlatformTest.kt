package com.pushizo.kmp

import com.pushizo.sdk.HsAction
import com.pushizo.sdk.HsClickEvent
import com.pushizo.sdk.HsLog
import com.pushizo.sdk.HsNotification
import com.pushizo.sdk.HsPushSubscriptionState
import com.pushizo.sdk.Pushizo as Native
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidPlatformTest {
    private val notification = HsNotification(
        title = "Başlık",
        body = "Gövde",
        imageUrl = "https://ornek.com/a.png",
        url = "https://ornek.com",
        deliveryId = "del_1",
        messageId = "msg_1",
        collapseKey = null,
        channelId = null,
        actions = listOf(HsAction("ok", "Tamam", "https://ornek.com/ok", icon = "ic")),
        data = mapOf("k" to "v"),
        subtitle = "Alt",
    )

    @Test
    fun `bildirim ve tıklama ortak modele eksiksiz çevrilir`() {
        val click = HsClickEvent(notification, "ok", "https://ornek.com/ok").toCommon()
        assertEquals("ok", click.actionId)
        assertEquals("https://ornek.com/ok", click.url)
        val n = click.notification
        assertEquals(listOf("Başlık", "Alt", "Gövde"), listOf(n.title, n.subtitle, n.body))
        assertEquals(listOf("del_1", "msg_1"), listOf(n.deliveryId, n.messageId))
        assertEquals("https://ornek.com/a.png", n.imageUrl)
        assertEquals(listOf(PushizoNotificationAction("ok", "Tamam", "https://ornek.com/ok")), n.actions)
        assertEquals(mapOf("k" to "v"), n.data)
    }

    @Test
    fun `abonelik durumu çevrilir`() {
        assertEquals(
            PushizoSubscriptionState("sub_1", "tok", optedIn = true, permission = false),
            HsPushSubscriptionState("sub_1", "tok", optedIn = true, permission = false).toCommon(),
        )
    }

    @Test
    fun `günlük düzeyleri bire bir eşlenir`() {
        assertEquals(HsLog.Level.entries.map { it.name }, PushizoLogLevel.entries.map { it.name })
        PushizoLogLevel.entries.forEach { assertEquals(it.name, it.toNative().name) }
    }

    @Test
    fun `apiUrl verilmezse yerel varsayılan korunur, verilirse kullanılır`() {
        val defaults = Native.Options()
        assertEquals(defaults.apiUrl, PushizoOptions().toNative().apiUrl)
        assertEquals(defaults.apiUrl, PushizoOptions(apiUrl = " ").toNative().apiUrl)
        val custom = PushizoOptions(apiUrl = "https://eu.ornek.com", requiresPrivacyConsent = true, logLevel = PushizoLogLevel.DEBUG).toNative()
        assertEquals("https://eu.ornek.com", custom.apiUrl)
        assertTrue(custom.requiresPrivacyConsent)
        assertEquals(HsLog.Level.DEBUG, custom.logLevel)
        assertFalse(defaults.requiresPrivacyConsent)
    }

    @Test
    fun `profil yerel modele eksiksiz çevrilir`() {
        val native = PushizoUserProfile("a@ornek.com", "+905551112233", mapOf("p" to "1"), mapOf("crm" to "c1")).toNative()
        assertEquals("a@ornek.com", native.email)
        assertEquals("+905551112233", native.sms)
        assertEquals(mapOf("p" to "1"), native.tags)
        assertEquals(mapOf("crm" to "c1"), native.aliases)
    }

    @Test
    fun `android hedefi yerel platforma bağlıdır`() {
        assertSame(AndroidPushizoPlatform, createPushizoPlatform())
    }
}
