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
    override fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?) = rec("event $name $properties $value")
    override fun addTriggers(triggers: Map<String, String>) = rec("addTriggers $triggers")
    override fun removeTriggers(keys: Collection<String>) = rec("removeTriggers $keys")
    override fun clearTriggers() = rec("clearTriggers")
    override fun getTriggers() = mapOf("level" to "gold")
    override fun setScreen(name: String?) = rec("screen $name")
    override var inAppPaused = false
    override val locationShared = true
    override val locationPermissionGranted = false
    override fun setLocationShared(shared: Boolean) = rec("location $shared")
    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) = rec("locationPermission $rationale")
    override fun onLocationPermissionResult() = rec("locationPermissionResult")
    private var geofencing = false
    override val geofencingEnabled: Boolean get() = geofencing
    override fun setGeofencingEnabled(enabled: Boolean) { rec("geofencing $enabled"); geofencing = enabled }

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
}
