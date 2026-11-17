package com.pushizo.kmp

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** İzin istemi çağrıları için platforma özgü boş ana bilgisayar nesnesi (Android `Activity`, iOS `UIViewController`). */
internal expect fun testPermissionHost(): PushizoPermissionHost

/** Yalnızca çağrıları kaydeder: ortak API'nin doğru yerel işleme, doğru bağımsız değişkenle gittiğini sınar. */
private class RecordingPlatform : PushizoPlatform {
    val calls = mutableListOf<String>()
    var state = PushizoSubscriptionState("sub_1", "tok", optedIn = true, permission = true)
    val subscriptionObservers = mutableListOf<(PushizoSubscriptionState) -> Unit>()
    private fun rec(call: String) { calls += call }

    override val isInitialized = true
    override fun initialize(context: PushizoContext, appId: String, options: PushizoOptions) = rec("initialize $appId")
    override fun setConsentGiven(given: Boolean) = rec("consent $given")
    override fun login(externalId: String, identityHash: String?, jwt: String?) = rec("login $externalId $identityHash $jwt")
    override fun login(externalId: String, profile: PushizoUserProfile, identityHash: String?, jwt: String?) = rec("loginProfile $externalId ${profile.email} ${profile.tags} $jwt")
    override fun updateUserJwt(jwt: String) = rec("jwt $jwt")
    override fun logout() = rec("logout")
    override fun setLogLevel(level: PushizoLogLevel) = rec("log $level")
    override fun addTags(tags: Map<String, String>) = rec("addTags $tags")
    override fun removeTags(keys: List<String>) = rec("removeTags $keys")
    override fun getTags() = mapOf("plan" to "pro")
    override fun addAliases(aliases: Map<String, String>) = rec("addAliases $aliases")
    override fun removeAlias(label: String) = rec("removeAlias $label")
    override fun setLanguage(code: String) = rec("language $code")
    override fun addEmail(email: String) = rec("addEmail $email")
    override fun removeEmail(email: String) = rec("removeEmail $email")
    override fun addSms(phoneNumber: String) = rec("addSms $phoneNumber")
    override fun removeSms(phoneNumber: String) = rec("removeSms $phoneNumber")
    override val emails = listOf("a@ornek.com")
    override val smsNumbers = listOf("+905550000000")
    override val externalId = "user_42"
    override val userId = "usr_1"
    override fun registerAdm(token: String) = rec("registerAdm $token")
    override val subscriptionState get() = state
    override fun optIn() = rec("optIn")
    override fun optOut() = rec("optOut")
    override fun addSubscriptionObserver(observer: (PushizoSubscriptionState) -> Unit): PushizoListenerRegistration {
        subscriptionObservers += observer
        return PushizoListenerRegistration { subscriptionObservers -= observer }
    }
    override val permission = true
    override fun requestPermission(host: PushizoPermissionHost) = rec("requestPermission")
    override fun onPermissionResult() = rec("permissionResult")
    override fun addPermissionObserver(observer: (Boolean) -> Unit) = PushizoListenerRegistration { rec("permission removed") }
    override fun addClickListener(listener: (PushizoClickEvent) -> Unit) = PushizoListenerRegistration { rec("click removed") }
    override fun addForegroundLifecycleListener(listener: (PushizoForegroundEvent) -> Unit) = PushizoListenerRegistration { rec("foreground removed") }
    override fun clearAllNotifications() = rec("clearAll")
    override fun removeNotification(androidNotificationId: Int) = rec("removeNotificationId $androidNotificationId")
    override fun removeNotification(identifier: String) = rec("removeNotification $identifier")
    override fun removeGroupedNotifications(group: String) = rec("removeGroup $group")
    override fun setBadgeCount(count: Int) = rec("badge $count")
    override fun setAdvertisingId(id: String?) = rec("adId $id")
    override fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?) = rec("event $name $properties $value")
    override fun addTriggers(triggers: Map<String, String>) = rec("addTriggers $triggers")
    override fun removeTriggers(keys: Collection<String>) = rec("removeTriggers $keys")
    override fun clearTriggers() = rec("clearTriggers")
    override fun getTriggers() = mapOf("level" to "gold")
    override fun setScreen(name: String?) = rec("screen $name")
    override var inAppPaused = false
    val inAppClickListeners = mutableListOf<(PushizoInAppClickEvent) -> Unit>()
    val inAppLifecycleListeners = mutableListOf<PushizoInAppLifecycleListener>()
    override fun addInAppClickListener(listener: (PushizoInAppClickEvent) -> Unit): PushizoListenerRegistration {
        inAppClickListeners += listener
        return PushizoListenerRegistration { inAppClickListeners -= listener }
    }
    override fun addInAppLifecycleListener(listener: PushizoInAppLifecycleListener): PushizoListenerRegistration {
        inAppLifecycleListeners += listener
        return PushizoListenerRegistration { inAppLifecycleListeners -= listener }
    }

    val placement = PushizoInAppMessage(
        "iam_1", "var_a", "banner_top", "blocks", null, "#ffffff", "#111111", dismissible = true,
        blocks = listOf(
            PushizoInAppBlock("title", text = "Merhaba"),
            PushizoInAppBlock("button", button = PushizoInAppButton("btn_1", "Aç", "url", "https://ornek.com", emptyMap(), null, null)),
        ),
    )
    override suspend fun placementsList(key: String, limit: Int): List<PushizoInAppMessage> { rec("placements $key $limit"); return listOf(placement) }
    override fun placementReportImpression(message: PushizoInAppMessage) = rec("placementImpression ${message.id}")
    override fun placementReportClick(message: PushizoInAppMessage, buttonId: String) = rec("placementClick ${message.id} $buttonId")
    override fun placementReportDismiss(message: PushizoInAppMessage) = rec("placementDismiss ${message.id}")
    override val locationShared = true
    override val locationPermissionGranted = false
    override fun setLocationShared(shared: Boolean) = rec("location $shared")
    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) = rec("locationPermission $rationale")
    override fun onLocationPermissionResult() = rec("locationPermissionResult")
    private var geofencing = false
    override val geofencingEnabled: Boolean get() = geofencing
    override fun setGeofencingEnabled(enabled: Boolean) { rec("geofencing $enabled"); geofencing = enabled }
    var backgroundGranted = false
    override val locationBackgroundPermissionGranted: Boolean get() = backgroundGranted
    override fun requestBackgroundLocationPermission(host: PushizoPermissionHost, rationale: String?) = rec("backgroundPermission $rationale")

    var delivery = PushizoDeliveryStatus.UNSUPPORTED
    var deliveryResult = false
    override fun deliveryStatus(): PushizoDeliveryStatus { rec("deliveryStatus"); return delivery }
    override fun shouldShowDeliveryGuidance(minIntervalDays: Int): Boolean { rec("deliveryShould $minIntervalDays"); return deliveryResult }
    override fun openDeliverySettings(context: PushizoContext, setting: PushizoDeliverySetting): Boolean { rec("deliveryOpen $setting"); return deliveryResult }
    override fun showDeliveryGuidance(host: PushizoPermissionHost, setting: PushizoDeliverySetting, message: String?, onResult: ((Boolean) -> Unit)?) {
        rec("deliveryGuidance $setting $message")
        onResult?.invoke(deliveryResult)
    }

    val inboxItem = PushizoInboxItem("inb_1", "msg_1", "Başlık", "Gövde", null, null, emptyMap(), read = false, readAt = null, archivedAt = null, expiresAt = null, createdAt = 1L)
    override suspend fun inboxList(limit: Int, cursor: String?): PushizoInboxPage { rec("inboxList $limit $cursor"); return PushizoInboxPage(listOf(inboxItem), 1, "c2", true) }
    override suspend fun inboxMarkRead(id: String): PushizoInboxItem { rec("inboxMarkRead $id"); return inboxItem.copy(read = true, readAt = 2L) }
    override suspend fun inboxMarkAllRead(): Int { rec("inboxMarkAllRead"); return 3 }
    override suspend fun inboxArchive(id: String) = rec("inboxArchive $id")
    override suspend fun inboxUnreadCount(): Int { rec("inboxUnreadCount"); return 1 }

    val card = PushizoContentCard(
        "cc_1", "msg_1", PushizoContentCardType.CAPTIONED_IMAGE, "Yeni", null, null, "https://cdn/x.png", "Aç", emptyMap(),
        pinned = true, priority = 50, viewed = false, clicked = false, expiresAt = null, createdAt = 1L,
    )
    override suspend fun contentCardsList(limit: Int): PushizoContentCardFeed { rec("cardsList $limit"); return PushizoContentCardFeed(listOf(card), 1) }
    override suspend fun contentCardImpression(id: String): PushizoContentCard { rec("cardImpression $id"); return card.copy(viewed = true) }
    override suspend fun contentCardClick(id: String): PushizoContentCard { rec("cardClick $id"); return card.copy(viewed = true, clicked = true) }
    override suspend fun contentCardDismiss(id: String): PushizoContentCard { rec("cardDismiss $id"); return card }

    val preferencesResult = PushizoPreferencesResult(
        listOf(PushizoPreference("promo", null, optedIn = false, explicit = true, source = "sdk", updatedAt = 5L)),
        listOf(PushizoMessageCategory("promo", mapOf("tr" to "Kampanyalar", "en" to "Promotions"), emptyMap(), null, defaultOptedIn = true)),
        listOf(PushizoConsent("ads", granted = true, source = "sdk", updatedAt = 6L)),
    )
    override suspend fun getPreferences(): PushizoPreferencesResult { rec("getPreferences"); return preferencesResult }
    override suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult { rec("setPreferences $preferences"); return preferencesResult }

    var chatAvailable = true
    val chatListeners = mutableListOf<PushizoChatListener>()
    val chatOpenListeners = mutableListOf<(PushizoChatPushInfo) -> Unit>()
    val chatMessage = PushizoChatMessage(
        "m_1", 7, "c_1", "agent", "Ayşe", "Merhaba", listOf(PushizoChatAttachment("att_1", "a.png", "image/png", 10)),
        listOf(PushizoChatQuickReply("q1", "Evet")), "sent", 5L,
    )
    val chatConversation = PushizoChatConversation("conv_1", "open", 7, 3, 5, 2, botActive = false, assigneeName = "Ayşe", createdAt = 1L)
    override val chatSupported: Boolean get() = chatAvailable
    override val chatVisitorId: String? get() = "vis_1"
    override val chatUnreadCount: Int get() = 2
    override var chatIsListening = false
    override fun addChatListener(listener: PushizoChatListener): PushizoListenerRegistration {
        chatListeners += listener
        return PushizoListenerRegistration { chatListeners -= listener }
    }
    override fun addChatOpenListener(listener: (PushizoChatPushInfo) -> Unit): PushizoListenerRegistration {
        chatOpenListeners += listener
        return PushizoListenerRegistration { chatOpenListeners -= listener }
    }
    override suspend fun chatStart(name: String?, email: String?, language: String?): PushizoChatIdentifyResult {
        rec("chatStart $name $email $language"); chatIsListening = true
        return PushizoChatIdentifyResult("usr_1", chatConversation)
    }
    override suspend fun chatIdentify(name: String?, email: String?, language: String?): PushizoChatIdentifyResult {
        rec("chatIdentify $name $email $language")
        return PushizoChatIdentifyResult("usr_1", null)
    }
    override suspend fun chatGetConfig(language: String?): PushizoChatConfig {
        rec("chatConfig $language")
        return PushizoChatConfig(true, true, true, "#000", "#fff", "right", "auto", "Destek", "Selam", null, false, false, true, 1024, listOf("image/png"), "tr")
    }
    override suspend fun chatGetConversation(): PushizoChatConversationState { rec("chatConversation"); return PushizoChatConversationState(chatConversation, 2) }
    override suspend fun chatGetMessages(afterSeq: Long?, beforeSeq: Long?, limit: Int): PushizoChatMessagePage {
        rec("chatMessages $afterSeq $beforeSeq $limit")
        return PushizoChatMessagePage(listOf(chatMessage), hasMore = true)
    }
    override suspend fun chatSendMessage(text: String, optionId: String?): PushizoChatMessage {
        rec("chatSend $text $optionId")
        return chatMessage.copy(sender = "visitor", text = text, status = "pending")
    }
    override suspend fun chatSendAttachment(bytes: ByteArray, fileName: String, mimeType: String, text: String?): PushizoChatMessage {
        rec("chatAttachment ${bytes.size} $fileName $mimeType $text")
        return chatMessage
    }
    override suspend fun chatMarkRead(seq: Long): Int { rec("chatMarkRead $seq"); return 0 }
    override suspend fun chatRefreshUnreadCount(): Int { rec("chatRefreshUnread"); return 2 }
    override suspend fun chatLeaveOfflineMessage(email: String, text: String, name: String?): PushizoChatMessage {
        rec("chatOffline $email $text $name")
        return chatMessage
    }
    override fun chatSetTyping(typing: Boolean) = rec("chatTyping $typing")
    override fun chatStartListening() { rec("chatListen"); chatIsListening = true }
    override fun chatStopListening() { rec("chatStop"); chatIsListening = false }
    override fun chatAttachmentUrl(attachmentId: String, download: Boolean): String? =
        if (chatAvailable) "https://api/chat/attachments/$attachmentId?download=$download" else null

    override suspend fun refreshFeatureFlags(): Boolean { rec("flagsRefresh"); return true }
    override fun isFeatureEnabled(key: String, default: Boolean) = if (key == "yeni_sepet") true else default
    override fun featureFlagValue(key: String): Any? = if (key == "limit") 3 else null
}

/** Sahte platform eşzamanlı tamamlandığı için askıya alınan çağrı ek bağımlılık olmadan çalıştırılır. */
private fun <T> runSuspending(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "askıya alınan çağrı eşzamanlı tamamlanmadı" }.getOrThrow()
}

class PushizoTest {
    private val fake = RecordingPlatform()
    private lateinit var original: PushizoPlatform

    @BeforeTest
    fun setUp() {
        original = Pushizo.platform
        Pushizo.platform = fake
    }

    @AfterTest
    fun tearDown() {
        Pushizo.platform = original
    }

    @Test
    fun loginVeOturum() {
        Pushizo.setConsentGiven(true)
        Pushizo.login("user_42")
        Pushizo.login("user_42", identityHash = "h", jwt = "j")
        Pushizo.updateUserJwt("j2")
        Pushizo.logout()
        Pushizo.Debug.setLogLevel(PushizoLogLevel.DEBUG)
        assertEquals(
            listOf("consent true", "login user_42 null null", "login user_42 h j", "jwt j2", "logout", "log DEBUG"),
            fake.calls,
        )
        assertTrue(Pushizo.isInitialized)
    }

    @Test
    fun profilliGiris() {
        Pushizo.login("user_42", PushizoUserProfile(email = "a@ornek.com", tags = mapOf("plan" to "pro")), jwt = "j")
        assertEquals(listOf("loginProfile user_42 a@ornek.com {plan=pro} j"), fake.calls)
    }

    @Test
    fun kullaniciTekilCagrilariTopluIslemeGider() {
        Pushizo.User.addTag("plan", "pro")
        Pushizo.User.removeTag("plan")
        Pushizo.User.addAlias("crm", "c1")
        Pushizo.User.removeAlias("crm")
        Pushizo.User.setLanguage("tr")
        Pushizo.User.addEmail("a@ornek.com")
        Pushizo.User.removeEmail("a@ornek.com")
        Pushizo.User.addSms("+905550000000")
        Pushizo.User.removeSms("+905550000000")
        assertEquals(
            listOf(
                "addTags {plan=pro}", "removeTags [plan]", "addAliases {crm=c1}", "removeAlias crm", "language tr",
                "addEmail a@ornek.com", "removeEmail a@ornek.com", "addSms +905550000000", "removeSms +905550000000",
            ),
            fake.calls,
        )
        assertEquals(mapOf("plan" to "pro"), Pushizo.User.getTags())
        assertEquals(listOf("a@ornek.com"), Pushizo.User.emails)
        assertEquals(listOf("+905550000000"), Pushizo.User.smsNumbers)
        assertEquals("user_42", Pushizo.User.externalId)
        assertEquals("usr_1", Pushizo.User.id)
    }

    @Test
    fun abonelikDurumuVeDinleyiciKaldirma() {
        assertEquals("sub_1", Pushizo.User.pushSubscription.id)
        assertEquals("tok", Pushizo.User.pushSubscription.token)
        assertTrue(Pushizo.User.pushSubscription.optedIn)
        Pushizo.User.pushSubscription.optOut()
        Pushizo.User.pushSubscription.optIn()
        assertEquals(listOf("optOut", "optIn"), fake.calls)

        val seen = mutableListOf<PushizoSubscriptionState>()
        val registration = Pushizo.User.pushSubscription.addObserver { seen += it }
        fake.subscriptionObservers.forEach { it(fake.state) }
        assertEquals(listOf(fake.state), seen)
        registration.remove()
        registration.remove()
        assertTrue(fake.subscriptionObservers.isEmpty())
    }

    @Test
    fun bildirimDinleyicileriKaldirilabilir() {
        assertTrue(Pushizo.Notifications.permission)
        Pushizo.Notifications.addPermissionObserver { }.remove()
        Pushizo.Notifications.addClickListener { }.remove()
        Pushizo.Notifications.addForegroundLifecycleListener { }.remove()
        Pushizo.Notifications.clearAll()
        Pushizo.Notifications.onPermissionResult()
        Pushizo.Location.onPermissionResult()
        assertEquals(
            listOf("permission removed", "click removed", "foreground removed", "clearAll", "permissionResult", "locationPermissionResult"),
            fake.calls,
        )
    }

    @Test
    fun olayVarsayilanlari() {
        Pushizo.Session.sendEvent("open")
        Pushizo.Session.sendEvent("purchase", mapOf("sku" to "a1"), value = 49.9)
        assertEquals(listOf("event open {} null", "event purchase {sku=a1} 49.9"), fake.calls)
    }

    @Test
    fun uygulamaIciVeKonum() {
        Pushizo.InAppMessages.addTrigger("level", "gold")
        Pushizo.InAppMessages.removeTrigger("level")
        Pushizo.InAppMessages.clearTriggers()
        Pushizo.InAppMessages.setScreen("home")
        Pushizo.Location.setShared(true)
        assertEquals(
            listOf("addTriggers {level=gold}", "removeTriggers [level]", "clearTriggers", "screen home", "location true"),
            fake.calls,
        )
        assertEquals(mapOf("level" to "gold"), Pushizo.InAppMessages.getTriggers())
        assertFalse(Pushizo.InAppMessages.paused)
        Pushizo.InAppMessages.paused = true
        assertTrue(fake.inAppPaused)
        assertTrue(Pushizo.Location.isShared)
        assertFalse(Pushizo.Location.permissionGranted)
    }

    @Test
    fun geofencingVarsayilanKapaliVePlatformaGecer() {
        assertFalse(Pushizo.Location.isGeofencingEnabled)
        Pushizo.Location.setGeofencingEnabled(true)
        assertTrue(Pushizo.Location.isGeofencingEnabled)
        assertEquals(listOf("geofencing true"), fake.calls)
    }

    @Test
    fun teslimDurumuPlatformdanGelirVeVarsayilanDesteklenmiyorRiskYok() {
        val unsupported = Pushizo.Delivery.status()
        assertFalse(unsupported.supported)
        assertEquals(PushizoDeliveryRisk.NONE, unsupported.risk)
        assertFalse(unsupported.restricted)
        assertTrue(unsupported.recommendedSettings.isEmpty())
        assertEquals("other", unsupported.vendor)
        assertEquals(PushizoDeliveryStatus(supported = false), PushizoDeliveryStatus.UNSUPPORTED)

        fake.delivery = PushizoDeliveryStatus(
            supported = true, manufacturer = "xiaomi", brand = "redmi", vendor = "xiaomi", aggressiveVendor = true, rom = "hyperos",
            romVersion = "1.0.2.0", batteryOptimizationExempt = false, standbyBucket = "rare", restricted = true,
            risk = PushizoDeliveryRisk.ELEVATED,
            recommendedSettings = listOf(PushizoDeliverySetting.AUTO_START, PushizoDeliverySetting.BATTERY_OPTIMIZATION),
        )
        val status = Pushizo.Delivery.status()
        assertTrue(status.supported)
        assertEquals(PushizoDeliveryRisk.ELEVATED, status.risk)
        assertEquals(listOf(PushizoDeliverySetting.AUTO_START, PushizoDeliverySetting.BATTERY_OPTIMIZATION), status.recommendedSettings)
        assertEquals(listOf("deliveryStatus", "deliveryStatus"), fake.calls)
    }

    @Test
    fun teslimYonlendirmeCagrilariVarsayilanlarlaPlatformaGider() {
        assertEquals(14, Pushizo.Delivery.DEFAULT_GUIDANCE_INTERVAL_DAYS)
        assertFalse(Pushizo.Delivery.shouldShowGuidance())
        fake.deliveryResult = true
        assertTrue(Pushizo.Delivery.shouldShowGuidance(minIntervalDays = 30))
        assertEquals(listOf("deliveryShould 14", "deliveryShould 30"), fake.calls)
    }

    @Test
    fun teslimAyarAdlariYerelSdkIleAynidir() {
        assertEquals(listOf("AUTO", "NOTIFICATIONS", "AUTO_START", "BATTERY_OPTIMIZATION", "APP_DETAILS"), PushizoDeliverySetting.entries.map { it.name })
        assertEquals(listOf("NONE", "ELEVATED", "HIGH"), PushizoDeliveryRisk.entries.map { it.name })
    }

    @Test
    fun gelenKutusuCagrilariPlatformaAynenGider() {
        val page = runSuspending { Pushizo.Inbox.list(limit = 20, cursor = "c1") }
        assertEquals("inb_1", page.items.single().id)
        assertEquals(listOf(1, "c2", true), listOf(page.unreadCount, page.nextCursor, page.hasMore))
        assertTrue(runSuspending { Pushizo.Inbox.markRead("inb_1") }.read)
        assertEquals(3, runSuspending { Pushizo.Inbox.markAllRead() })
        runSuspending { Pushizo.Inbox.archive("inb_1") }
        assertEquals(1, runSuspending { Pushizo.Inbox.unreadCount() })
        assertEquals(listOf("inboxList 20 c1", "inboxMarkRead inb_1", "inboxMarkAllRead", "inboxArchive inb_1", "inboxUnreadCount"), fake.calls)
    }

    @Test
    fun icerikKartlariPlatformaGiderVeDogrulanir() {
        val feed = runSuspending { Pushizo.ContentCards.list(limit = 20) }
        assertEquals(1, feed.unviewedCount)
        assertEquals(PushizoContentCardType.CAPTIONED_IMAGE, feed.cards.single().type)
        assertTrue(runSuspending { Pushizo.ContentCards.logImpression("cc_1") }.viewed)
        assertTrue(runSuspending { Pushizo.ContentCards.logClick("cc_1") }.clicked)
        runSuspending { Pushizo.ContentCards.dismiss("cc_1") }
        assertEquals(listOf("cardsList 20", "cardImpression cc_1", "cardClick cc_1", "cardDismiss cc_1"), fake.calls)
        assertFailsWith<IllegalArgumentException> { runSuspending { Pushizo.ContentCards.list(limit = 101) } }
        assertFailsWith<IllegalArgumentException> { runSuspending { Pushizo.ContentCards.dismiss(" ") } }
        assertEquals(PushizoContentCardType.CLASSIC, PushizoContentCardType.from("bilinmeyen"))
    }

    @Test
    fun tercihlerVeRizalar() {
        val result = runSuspending { Pushizo.Preferences.set(listOf(PushizoPreferenceInput("promo", optedIn = false))) }
        assertEquals("Kampanyalar", result.categories.single().localizedName("tr"))
        assertEquals("Promotions", result.categories.single().localizedName("de"))
        assertEquals(listOf("ads"), runSuspending { Pushizo.Preferences.consents() }.map { it.purpose })
        assertEquals(listOf("setPreferences [PushizoPreferenceInput(category=promo, channel=null, optedIn=false)]", "getPreferences"), fake.calls)
    }

    @Test
    fun ozellikBayraklari() {
        assertTrue(runSuspending { Pushizo.FeatureFlags.refresh() })
        assertTrue(Pushizo.FeatureFlags.isEnabled("yeni_sepet"))
        assertFalse(Pushizo.FeatureFlags.isEnabled("yok"))
        assertTrue(Pushizo.FeatureFlags.isEnabled("yok", default = true))
        assertEquals(3, Pushizo.FeatureFlags.getValue("limit"))
        assertEquals("varsayılan", Pushizo.FeatureFlags.getValue("yok", "varsayılan"))
        assertEquals(listOf("flagsRefresh"), fake.calls)
    }

    @Test
    fun reklamRizasiVeRizaKayitlariTercihUcunaGider() {
        val result = runSuspending { Pushizo.User.setAdsPersonalizationConsent(true) }
        assertEquals(fake.preferencesResult, result)
        runSuspending { Pushizo.User.setAdsPersonalizationConsent(false) }
        assertEquals(listOf("ads"), runSuspending { Pushizo.User.getConsents() }.map { it.purpose })
        assertEquals("ads_personalization", Pushizo.User.ADS_PERSONALIZATION)
        assertEquals(
            listOf(
                "setPreferences [PushizoPreferenceInput(category=ads_personalization, channel=null, optedIn=true)]",
                "setPreferences [PushizoPreferenceInput(category=ads_personalization, channel=null, optedIn=false)]",
                "getPreferences",
            ),
            fake.calls,
        )
        // `User` altındaki tercih çağrıları `Preferences` ile aynı platform işlemine gider.
        fake.calls.clear()
        runSuspending { Pushizo.User.getPreferences() }
        runSuspending { Pushizo.User.setPreferences(listOf(PushizoPreferenceInput("promo", "email", optedIn = true))) }
        assertEquals(listOf("getPreferences", "setPreferences [PushizoPreferenceInput(category=promo, channel=email, optedIn=true)]"), fake.calls)
    }

    @Test
    fun reklamKimligiVeAdmKaydi() {
        Pushizo.Ads.setAdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d")
        Pushizo.Ads.setAdvertisingId(null)
        Pushizo.User.registerAdm("amzn1.adm-registration.v3.x")
        assertEquals(listOf("adId 38400000-8cf0-11bd-b23e-10b96e40000d", "adId null", "registerAdm amzn1.adm-registration.v3.x"), fake.calls)
    }

    @Test
    fun bildirimKaldirmaVeRozet() {
        Pushizo.Notifications.removeNotification(42)
        Pushizo.Notifications.removeNotification("req_1")
        Pushizo.Notifications.removeGroupedNotifications("siparis")
        Pushizo.Notifications.setBadgeCount(3)
        Pushizo.Notifications.clearBadge()
        assertEquals(listOf("removeNotificationId 42", "removeNotification req_1", "removeGroup siparis", "badge 3", "badge 0"), fake.calls)
        assertFailsWith<IllegalArgumentException> { Pushizo.Notifications.setBadgeCount(-1) }
        assertEquals(5, fake.calls.size)
    }

    @Test
    fun arkaPlanKonumIzni() {
        assertFalse(Pushizo.Location.backgroundPermissionGranted)
        fake.backgroundGranted = true
        assertTrue(Pushizo.Location.backgroundPermissionGranted)
        val host = testPermissionHost()
        Pushizo.Location.requestBackgroundPermission(host, "Yakındaki mağazalar için")
        Pushizo.Location.requestBackgroundPermission(host)
        Pushizo.Location.requestAlwaysPermission(host)
        assertEquals(listOf("backgroundPermission Yakındaki mağazalar için", "backgroundPermission null", "backgroundPermission null"), fake.calls)
    }

    @Test
    fun uygulamaIciDinleyicileriKaldirilabilir() {
        val clicks = mutableListOf<PushizoInAppClickEvent>()
        val click = Pushizo.InAppMessages.addClickListener { clicks += it }
        fake.inAppClickListeners.forEach { it(PushizoInAppClickEvent("iam_1", "btn_1", "https://ornek.com")) }
        assertEquals(listOf(PushizoInAppClickEvent("iam_1", "btn_1", "https://ornek.com")), clicks)
        click.remove()
        click.remove()
        assertTrue(fake.inAppClickListeners.isEmpty())

        val seen = mutableListOf<String>()
        val listener = object : PushizoInAppLifecycleListener {
            override fun onWillDisplay(event: PushizoInAppLifecycleEvent) { seen += "will ${event.message.id}" }
            override fun onDidDismiss(event: PushizoInAppLifecycleEvent) { seen += "dismissed ${event.message.id}" }
        }
        val lifecycle = Pushizo.InAppMessages.addLifecycleListener(listener)
        val event = PushizoInAppLifecycleEvent(fake.placement)
        fake.inAppLifecycleListeners.forEach { it.onWillDisplay(event); it.onDidDisplay(event); it.onWillDismiss(event); it.onDidDismiss(event) }
        assertEquals(listOf("will iam_1", "dismissed iam_1"), seen)
        lifecycle.remove()
        assertTrue(fake.inAppLifecycleListeners.isEmpty())
    }

    @Test
    fun yerlesimlerDogrulanirVePlatformaGider() {
        val messages = runSuspending { Pushizo.Placements.list("home_banner", limit = 3) }
        val message = messages.single()
        assertEquals(listOf("iam_1", "var_a", "banner_top", "blocks"), listOf(message.id, message.variantId, message.layout, message.contentType))
        assertEquals("btn_1", message.blocks.last().button?.id)
        runSuspending { Pushizo.Placements.list("home_banner") }
        Pushizo.Placements.reportImpression(message)
        Pushizo.Placements.reportClick(message, "btn_1")
        Pushizo.Placements.reportDismiss(message)
        assertEquals(
            listOf("placements home_banner 3", "placements home_banner 1", "placementImpression iam_1", "placementClick iam_1 btn_1", "placementDismiss iam_1"),
            fake.calls,
        )
        assertFailsWith<IllegalArgumentException> { runSuspending { Pushizo.Placements.list("Home Banner") } }
        assertFailsWith<IllegalArgumentException> { runSuspending { Pushizo.Placements.list("home", limit = 11) } }
        assertFailsWith<IllegalArgumentException> { Pushizo.Placements.reportClick(message, " ") }
        assertEquals(5, fake.calls.size)
    }

    @Test
    fun sohbetCagrilariPlatformaAynenGider() {
        assertTrue(Pushizo.Chat.isSupported)
        assertEquals("vis_1", Pushizo.Chat.visitorId)
        assertEquals(2, Pushizo.Chat.unreadCount)
        assertFalse(Pushizo.Chat.isListening)
        assertEquals("conv_1", runSuspending { Pushizo.Chat.start(name = "Ali", email = "a@ornek.com", language = "tr") }.conversation?.id)
        assertTrue(Pushizo.Chat.isListening)
        assertEquals(null, runSuspending { Pushizo.Chat.identify() }.conversation)
        assertEquals("Destek", runSuspending { Pushizo.Chat.getConfig("tr") }.title)
        assertEquals(2, runSuspending { Pushizo.Chat.getConversation() }.unreadCount)
        val page = runSuspending { Pushizo.Chat.getMessages(afterSeq = 5) }
        assertTrue(page.hasMore)
        assertFalse(page.messages.single().isFromVisitor)
        val sent = runSuspending { Pushizo.Chat.sendMessage("Selam", optionId = "q1") }
        assertTrue(sent.isFromVisitor)
        assertEquals("pending", sent.status)
        runSuspending { Pushizo.Chat.sendAttachment(ByteArray(3), "a.png", "image/png", text = "ek") }
        assertEquals(0, runSuspending { Pushizo.Chat.markRead(7) })
        assertEquals(2, runSuspending { Pushizo.Chat.refreshUnreadCount() })
        runSuspending { Pushizo.Chat.leaveOfflineMessage("a@ornek.com", "Dönüş yapın") }
        Pushizo.Chat.setTyping(true)
        Pushizo.Chat.stopListening()
        assertFalse(Pushizo.Chat.isListening)
        Pushizo.Chat.startListening()
        assertEquals("https://api/chat/attachments/att_1?download=false", Pushizo.Chat.attachmentUrl("att_1"))
        assertEquals("https://api/chat/attachments/att_1?download=true", Pushizo.Chat.attachmentUrl("att_1", download = true))
        assertEquals(
            listOf(
                "chatStart Ali a@ornek.com tr", "chatIdentify null null null", "chatConfig tr", "chatConversation", "chatMessages 5 null 50",
                "chatSend Selam q1", "chatAttachment 3 a.png image/png ek", "chatMarkRead 7", "chatRefreshUnread",
                "chatOffline a@ornek.com Dönüş yapın null", "chatTyping true", "chatStop", "chatListen",
            ),
            fake.calls,
        )
    }

    @Test
    fun sohbetDinleyicileriKaldirilabilirVeDesteklenmeyenPlatformdaAdresNull() {
        val seen = mutableListOf<String>()
        val registration = Pushizo.Chat.addListener(object : PushizoChatListener {
            override fun onMessage(message: PushizoChatMessage) { seen += "message ${message.seq}" }
            override fun onUnreadCountChanged(count: Int) { seen += "unread $count" }
        })
        val open = Pushizo.Chat.addOpenListener { seen += "open ${it.conversationId} ${it.seq}" }
        fake.chatListeners.forEach { it.onMessage(fake.chatMessage); it.onTyping(true, "Ayşe"); it.onUnreadCountChanged(1); it.onError("x", null) }
        fake.chatOpenListeners.forEach { it(PushizoChatPushInfo("conv_1", 7)) }
        assertEquals(listOf("message 7", "unread 1", "open conv_1 7"), seen)
        registration.remove()
        open.remove()
        assertTrue(fake.chatListeners.isEmpty())
        assertTrue(fake.chatOpenListeners.isEmpty())

        fake.chatAvailable = false
        assertFalse(Pushizo.Chat.isSupported)
        assertEquals(null, Pushizo.Chat.attachmentUrl("att_1"))
    }

    @Test
    fun yerlesimMesajiEsitligiYerelNesneyiKapsamaz() {
        val a = fake.placement.copy()
        val b = fake.placement.copy().also { it.native = "yerel" }
        assertEquals(a, b)
        assertEquals(null, PushizoNotification(null, null, null, null, null, null, null, emptyList(), emptyMap()).androidNotificationId)
    }
}
