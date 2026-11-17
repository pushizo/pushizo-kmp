package com.pushizo.kmp

import com.pushizo.sdk.HsAction
import com.pushizo.sdk.HsChatAttachment
import com.pushizo.sdk.HsChatConfig
import com.pushizo.sdk.HsChatConversation
import com.pushizo.sdk.HsChatConversationState
import com.pushizo.sdk.HsChatIdentifyResult
import com.pushizo.sdk.HsChatMessage
import com.pushizo.sdk.HsChatMessagePage
import com.pushizo.sdk.HsChatPushInfo
import com.pushizo.sdk.HsChatQuickReply
import com.pushizo.sdk.HsInAppAction
import com.pushizo.sdk.HsInAppBlock
import com.pushizo.sdk.HsInAppCarouselCard
import com.pushizo.sdk.HsInAppClickEvent
import com.pushizo.sdk.HsInAppDisplay
import com.pushizo.sdk.HsInAppLayout
import com.pushizo.sdk.HsInAppLifecycleEvent
import com.pushizo.sdk.HsInAppMessage
import com.pushizo.sdk.HsClickEvent
import com.pushizo.sdk.HsDeliveryRisk
import com.pushizo.sdk.HsDeliverySetting
import com.pushizo.sdk.HsDeliveryStatus
import com.pushizo.sdk.HsDeviceVendor
import com.pushizo.sdk.HsStandbyBucket
import com.pushizo.sdk.HsLog
import com.pushizo.sdk.HsNotification
import com.pushizo.sdk.HsPushSubscriptionState
import com.pushizo.sdk.Pushizo as Native
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal actual fun testPermissionHost(): PushizoPermissionHost = android.app.Activity()

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
    fun `teslim durumu ortak modele eksiksiz çevrilir`() {
        val native = HsDeliveryStatus(
            manufacturer = "xiaomi",
            brand = "redmi",
            vendor = HsDeviceVendor.XIAOMI,
            rom = "hyperos",
            romVersion = "1.0.2.0",
            notificationsEnabled = true,
            channelBlocked = false,
            batteryOptimizationExempt = false,
            backgroundRestricted = false,
            standbyBucket = HsStandbyBucket.RARE,
            powerSaveMode = true,
            dataSaverEnabled = false,
        )
        val status = native.toCommon()
        assertTrue(status.supported)
        assertEquals(listOf("xiaomi", "redmi", "xiaomi", "hyperos", "1.0.2.0"), listOf(status.manufacturer, status.brand, status.vendor, status.rom, status.romVersion))
        assertTrue(status.aggressiveVendor)
        assertTrue(status.notificationsEnabled)
        assertFalse(status.channelBlocked)
        assertFalse(status.batteryOptimizationExempt)
        assertFalse(status.backgroundRestricted)
        assertEquals("rare", status.standbyBucket)
        assertTrue(status.powerSaveMode)
        assertFalse(status.dataSaverEnabled)
        // Türetilen alanlar yerel kurallardan gelir (kopyalanmaz): kova `rare` → kısıtlı, saldırgan üretici + muafiyet yok → ELEVATED.
        assertEquals(native.restricted, status.restricted)
        assertTrue(status.restricted)
        assertEquals(PushizoDeliveryRisk.ELEVATED, status.risk)
        assertEquals(native.recommendedSettings.map { it.name }, status.recommendedSettings.map { it.name })
        assertEquals(listOf(PushizoDeliverySetting.AUTO_START, PushizoDeliverySetting.BATTERY_OPTIMIZATION), status.recommendedSettings)

        val clean = native.copy(vendor = HsDeviceVendor.OTHER, standbyBucket = null, batteryOptimizationExempt = true).toCommon()
        assertEquals(PushizoDeliveryRisk.NONE, clean.risk)
        assertFalse(clean.restricted)
        assertEquals(null, clean.standbyBucket)
        assertTrue(clean.recommendedSettings.isEmpty())
        assertEquals(PushizoDeliveryRisk.HIGH, native.copy(notificationsEnabled = false).toCommon().risk)
    }

    @Test
    fun `teslim riski ve ayar ekranı adları yerel enum'larla bire bir eşlenir`() {
        assertEquals(HsDeliveryRisk.entries.map { it.name }, PushizoDeliveryRisk.entries.map { it.name })
        assertEquals(HsDeliverySetting.entries.map { it.name }, PushizoDeliverySetting.entries.map { it.name })
        HsDeliveryRisk.entries.forEach { assertEquals(it.name, it.toCommon().name) }
        HsDeliverySetting.entries.forEach { assertEquals(it.name, it.toCommon()?.name) }
        PushizoDeliverySetting.entries.forEach { assertEquals(it.name, it.toNative().name) }
    }

    @Test
    fun `yerel SDK başlatılmadan teslim durumu desteklenmiyor ve çağrılar false döner`() {
        assertFalse(Native.isInitialized)
        assertSame(PushizoDeliveryStatus.UNSUPPORTED, AndroidPushizoPlatform.deliveryStatus())
        assertFalse(AndroidPushizoPlatform.shouldShowDeliveryGuidance(14))
    }

    @Test
    fun `android hedefi yerel platforma bağlıdır`() {
        assertSame(AndroidPushizoPlatform, createPushizoPlatform())
    }

    private fun button(id: String, action: HsInAppAction) = HsInAppBlock.Button(id, "Düğme $id", action, "#000000", null)

    private val inApp = HsInAppMessage(
        id = "iam_1",
        priority = 5,
        triggers = emptyList(),
        triggerGroups = emptyList(),
        variantId = "var_a",
        display = HsInAppDisplay(
            layout = HsInAppLayout.BANNER_TOP,
            backgroundColor = "#ffffff",
            textColor = "#111111",
            dismissible = true,
            autoDismissSeconds = null,
            blocks = listOf(
                HsInAppBlock.Image("https://cdn/x.png", "alt"),
                HsInAppBlock.Title("Başlık"),
                HsInAppBlock.Body("Gövde"),
                button("b_url", HsInAppAction.Url("https://ornek.com")),
                button("b_deep", HsInAppAction.Deeplink("app://sepet")),
                button("b_tags", HsInAppAction.Tags(mapOf("plan" to "pro"))),
                button("b_push", HsInAppAction.PushPrompt),
                button("b_out", HsInAppAction.Outcome("satin_alma", 9.5)),
                button("b_x", HsInAppAction.Dismiss),
                HsInAppBlock.Carousel("car", listOf(HsInAppCarouselCard("c1", "Kart", "Metin", "https://cdn/c.png", null, button("b_card", HsInAppAction.Dismiss)))),
            ),
        ),
        remainingImpressions = 1,
        minIntervalSeconds = 0,
        stopAfterInteraction = false,
        lastImpressionAt = null,
        endAt = null,
        json = "{}",
    )

    @Test
    fun `in-app mesaj ortak modele çevrilir ve yerel nesneyi taşır`() {
        val message = inApp.toCommon()
        assertEquals(listOf("iam_1", "var_a", "banner_top", "blocks"), listOf(message.id, message.variantId, message.layout, message.contentType))
        assertEquals(listOf("#ffffff", "#111111"), listOf(message.backgroundColor, message.textColor))
        assertTrue(message.dismissible)
        assertNull(message.html)
        assertEquals(listOf("image", "title", "body", "button", "button", "button", "button", "button", "button", "carousel"), message.blocks.map { it.kind })
        assertEquals(listOf("https://cdn/x.png", "alt"), message.blocks[0].let { listOf(it.url, it.alt) })
        assertEquals("Başlık", message.blocks[1].text)
        val buttons = message.blocks.mapNotNull { it.button }
        assertEquals(listOf("url", "deeplink", "tags", "push_prompt", "outcome", "dismiss"), buttons.map { it.actionType })
        assertEquals(listOf("https://ornek.com", "app://sepet", null, null, "satin_alma", null), buttons.map { it.actionValue })
        assertEquals(mapOf("plan" to "pro"), buttons[2].actionTags)
        assertEquals("#000000", buttons[0].backgroundColor)
        val card = message.blocks.last().cards.single()
        assertEquals(listOf("c1", "Kart", "Metin", "https://cdn/c.png"), listOf(card.id, card.title, card.body, card.imageUrl))
        assertEquals("b_card", card.button?.id)
        // Raporlama yerel nesneyle yapılır; elle kurulan modelde yerel nesne yoktur ve çağrı yok sayılır.
        assertSame(inApp, message.nativeAndroid())
        assertNull(message.copy().nativeAndroid())
        AndroidPushizoPlatform.placementReportImpression(message.copy())
        AndroidPushizoPlatform.placementReportClick(message.copy(), "b_url")
        AndroidPushizoPlatform.placementReportDismiss(message.copy())
    }

    @Test
    fun `in-app tıklama ve yaşam döngüsü olayları uyarlanır`() {
        assertEquals(PushizoInAppClickEvent("iam_1", "b_url", "https://ornek.com"), HsInAppClickEvent("iam_1", "b_url", "https://ornek.com").toCommon())
        val seen = mutableListOf<String>()
        val adapter = AndroidInAppLifecycleListener(object : PushizoInAppLifecycleListener {
            override fun onWillDisplay(event: PushizoInAppLifecycleEvent) { seen += "willDisplay ${event.message.id}" }
            override fun onDidDisplay(event: PushizoInAppLifecycleEvent) { seen += "didDisplay ${event.message.id}" }
            override fun onWillDismiss(event: PushizoInAppLifecycleEvent) { seen += "willDismiss ${event.message.id}" }
            override fun onDidDismiss(event: PushizoInAppLifecycleEvent) { seen += "didDismiss ${event.message.id}" }
        })
        val event = HsInAppLifecycleEvent(inApp)
        adapter.onWillDisplay(event)
        adapter.onDidDisplay(event)
        adapter.onWillDismiss(event)
        adapter.onDidDismiss(event)
        assertEquals(listOf("willDisplay iam_1", "didDisplay iam_1", "willDismiss iam_1", "didDismiss iam_1"), seen)
    }

    @Test
    fun `sohbet modelleri ortak modele eksiksiz çevrilir`() {
        val native = HsChatMessage(
            "m_1", 7, "c_1", HsChatMessage.SENDER_AGENT, "Ayşe", "Merhaba",
            listOf(HsChatAttachment("att_1", "a.png", "image/png", 10)), listOf(HsChatQuickReply("q1", "Evet")), HsChatMessage.STATUS_SENT, 5L,
        )
        val message = native.toCommon()
        assertEquals(
            PushizoChatMessage(
                "m_1", 7, "c_1", "agent", "Ayşe", "Merhaba", listOf(PushizoChatAttachment("att_1", "a.png", "image/png", 10)),
                listOf(PushizoChatQuickReply("q1", "Evet")), "sent", 5L,
            ),
            message,
        )
        assertFalse(message.isFromVisitor)
        assertEquals(native.copy(sender = HsChatMessage.SENDER_VISITOR).isFromVisitor, native.copy(sender = HsChatMessage.SENDER_VISITOR).toCommon().isFromVisitor)
        assertNull(native.copy(quickReplies = null).toCommon().quickReplies)

        val conversation = HsChatConversation("conv_1", "open", 7, 3, 5, 2, botActive = true, assigneeName = "Ayşe", createdAt = 1L)
        val common = PushizoChatConversation("conv_1", "open", 7, 3, 5, 2, botActive = true, assigneeName = "Ayşe", createdAt = 1L)
        assertEquals(common, conversation.toCommon())
        assertEquals(PushizoChatConversationState(common, 2), HsChatConversationState(conversation, 2).toCommon())
        assertEquals(PushizoChatConversationState(null, 0), HsChatConversationState(null, 0).toCommon())
        assertEquals(PushizoChatMessagePage(listOf(message), hasMore = true), HsChatMessagePage(listOf(native), hasMore = true).toCommon())
        assertEquals(PushizoChatIdentifyResult("usr_1", common), HsChatIdentifyResult("usr_1", conversation).toCommon())
        assertEquals(PushizoChatPushInfo("conv_1", 7), HsChatPushInfo("conv_1", 7).toCommon())
        assertEquals(
            PushizoChatConfig(true, false, true, "#000", "#fff", "left", "dark", "Destek", "Selam", "Kapalıyız", true, false, true, 1024, listOf("image/png"), "tr"),
            HsChatConfig(true, false, true, "#000", "#fff", "left", "dark", "Destek", "Selam", "Kapalıyız", true, false, true, 1024, listOf("image/png"), "tr").toCommon(),
        )
    }

    @Test
    fun `sohbet dinleyicisi yerel olayları ortak dinleyiciye aktarır`() {
        val seen = mutableListOf<String>()
        val adapter = AndroidChatListener(object : PushizoChatListener {
            override fun onMessage(message: PushizoChatMessage) { seen += "message ${message.seq}" }
            override fun onTyping(typing: Boolean, name: String?) { seen += "typing $typing $name" }
            override fun onAgentRead(seq: Long) { seen += "read $seq" }
            override fun onUnreadCountChanged(count: Int) { seen += "unread $count" }
            override fun onConversationUpdated(conversationId: String, status: String, botActive: Boolean) { seen += "conversation $conversationId $status $botActive" }
            override fun onConnectionChanged(connected: Boolean) { seen += "connection $connected" }
            override fun onError(code: String, message: String?) { seen += "error $code $message" }
        })
        adapter.onMessage(HsChatMessage("m", 3, null, "bot", null, "x", emptyList(), null, "sent", null))
        adapter.onTyping(true, "Ayşe")
        adapter.onAgentRead(3)
        adapter.onUnreadCountChanged(1)
        adapter.onConversationUpdated("conv_1", "closed", false)
        adapter.onConnectionChanged(true)
        adapter.onError("stream", "koptu")
        assertEquals(
            listOf("message 3", "typing true Ayşe", "read 3", "unread 1", "conversation conv_1 closed false", "connection true", "error stream koptu"),
            seen,
        )
    }

    @Test
    fun `yerel SDK başlatılmadan yeni çağrılar zararsızdır`() {
        assertFalse(Native.isInitialized)
        assertTrue(AndroidPushizoPlatform.chatSupported)
        assertNull(AndroidPushizoPlatform.chatVisitorId)
        assertEquals(0, AndroidPushizoPlatform.chatUnreadCount)
        assertFalse(AndroidPushizoPlatform.chatIsListening)
        assertFalse(AndroidPushizoPlatform.locationBackgroundPermissionGranted)
        // Android'de rozet sayacı yoktur; bildirim kaldırma ve reklam kimliği başlatılmadan etkisizdir.
        AndroidPushizoPlatform.setBadgeCount(5)
        AndroidPushizoPlatform.removeNotification(42)
        AndroidPushizoPlatform.removeNotification("42")
        AndroidPushizoPlatform.removeNotification("ios-istek-kimligi")
        AndroidPushizoPlatform.removeGroupedNotifications("siparis")
        AndroidPushizoPlatform.setAdvertisingId(null)
        AndroidPushizoPlatform.addInAppClickListener { }.remove()
        AndroidPushizoPlatform.addInAppLifecycleListener(object : PushizoInAppLifecycleListener {}).remove()
        AndroidPushizoPlatform.addChatListener(object : PushizoChatListener {}).remove()
        AndroidPushizoPlatform.addChatOpenListener { }.remove()
    }

    @Test
    fun `bildirim tepsi kimliği ortak modele taşınır`() {
        assertEquals(notification.androidNotificationId, notification.toCommon().androidNotificationId)
    }
}
