package com.pushizo.kmp

/**
 * Pushizo Kotlin Multiplatform SDK'sı: ortak koddan yerel SDK'ları çağırır.
 *
 * ```kotlin
 * Pushizo.initialize(context, "app_…", PushizoOptions())
 * Pushizo.login("user_42")
 * Pushizo.User.addTag("plan", "pro")
 * Pushizo.Notifications.requestPermission(activity)
 * Pushizo.Session.sendEvent("purchase", mapOf("sku" to "a1"), value = 49.9)
 * ```
 */
object Pushizo {
    internal var platform: PushizoPlatform = createPushizoPlatform()

    val isInitialized: Boolean get() = platform.isInitialized

    fun initialize(context: PushizoContext, appId: String, options: PushizoOptions = PushizoOptions()) =
        platform.initialize(context, appId, options)

    fun setConsentGiven(given: Boolean) = platform.setConsentGiven(given)

    /** Anonim cihazı kullanıcıya bağlar. Kimlik doğrulaması açıksa sunucunuzun ürettiği [identityHash] ya da [jwt] verilir. */
    fun login(externalId: String, identityHash: String? = null, jwt: String? = null) =
        platform.login(externalId, identityHash, jwt)

    /** Kullanıcıyı kimliklendirir ve [profile] alanlarını tek çağrıda ayarlar. */
    fun login(externalId: String, profile: PushizoUserProfile, identityHash: String? = null, jwt: String? = null) =
        platform.login(externalId, profile, identityHash, jwt)

    fun updateUserJwt(jwt: String) = platform.updateUserJwt(jwt)

    fun logout() = platform.logout()

    object Debug {
        fun setLogLevel(level: PushizoLogLevel) = platform.setLogLevel(level)
    }

    object User {
        fun addTag(key: String, value: String) = platform.addTags(mapOf(key to value))
        fun addTags(tags: Map<String, String>) = platform.addTags(tags)
        fun removeTag(key: String) = platform.removeTags(listOf(key))
        fun removeTags(keys: List<String>) = platform.removeTags(keys)
        fun getTags(): Map<String, String> = platform.getTags()
        fun addAlias(label: String, id: String) = platform.addAliases(mapOf(label to id))
        fun addAliases(aliases: Map<String, String>) = platform.addAliases(aliases)
        fun removeAlias(label: String) = platform.removeAlias(label)
        fun setLanguage(code: String) = platform.setLanguage(code)
        fun addEmail(email: String) = platform.addEmail(email)
        fun removeEmail(email: String) = platform.removeEmail(email)
        fun addSms(phoneNumber: String) = platform.addSms(phoneNumber)
        fun removeSms(phoneNumber: String) = platform.removeSms(phoneNumber)
        val emails: List<String> get() = platform.emails
        val smsNumbers: List<String> get() = platform.smsNumbers
        val externalId: String? get() = platform.externalId
        val id: String? get() = platform.userId

        @Suppress("ClassName")
        object pushSubscription {
            val state: PushizoSubscriptionState get() = platform.subscriptionState
            val id: String? get() = state.id
            val token: String? get() = state.token
            val optedIn: Boolean get() = state.optedIn
            fun optIn() = platform.optIn()
            fun optOut() = platform.optOut()
            fun addObserver(observer: (PushizoSubscriptionState) -> Unit): PushizoListenerRegistration =
                platform.addSubscriptionObserver(observer)
        }
    }

    object Notifications {
        val permission: Boolean get() = platform.permission
        fun requestPermission(host: PushizoPermissionHost) = platform.requestPermission(host)

        /** Android: Activity `onRequestPermissionsResult` içinden çağrılır; abonelik ve izin gözlemcileri güncellenir. */
        fun onPermissionResult() = platform.onPermissionResult()

        fun addPermissionObserver(observer: (Boolean) -> Unit): PushizoListenerRegistration =
            platform.addPermissionObserver(observer)
        fun addClickListener(listener: (PushizoClickEvent) -> Unit): PushizoListenerRegistration =
            platform.addClickListener(listener)
        fun addForegroundLifecycleListener(listener: (PushizoForegroundEvent) -> Unit): PushizoListenerRegistration =
            platform.addForegroundLifecycleListener(listener)
        fun clearAll() = platform.clearAllNotifications()
    }

    object Session {
        fun sendEvent(name: String, properties: Map<String, Any?> = emptyMap(), value: Double? = null) =
            platform.sendEvent(name, properties, value)
    }

    object InAppMessages {
        fun addTrigger(key: String, value: String) = platform.addTriggers(mapOf(key to value))
        fun addTriggers(triggers: Map<String, String>) = platform.addTriggers(triggers)
        fun removeTrigger(key: String) = platform.removeTriggers(listOf(key))
        fun removeTriggers(keys: Collection<String>) = platform.removeTriggers(keys)
        fun clearTriggers() = platform.clearTriggers()
        fun getTriggers(): Map<String, String> = platform.getTriggers()
        fun setScreen(name: String?) = platform.setScreen(name)
        var paused: Boolean
            get() = platform.inAppPaused
            set(value) { platform.inAppPaused = value }
    }

    object Location {
        val isShared: Boolean get() = platform.locationShared
        val permissionGranted: Boolean get() = platform.locationPermissionGranted
        fun setShared(shared: Boolean) = platform.setLocationShared(shared)
        fun requestPermission(host: PushizoPermissionHost, rationale: String? = null) =
            platform.requestLocationPermission(host, rationale)

        /** Android: Activity `onRequestPermissionsResult` içinden çağrılır. */
        fun onPermissionResult() = platform.onLocationPermissionResult()

        /**
         * Arka plan bölge izleme (varsayılan kapalı). Üç koşul birlikte gerekir: bu bayrak, `setShared(true)` ve
         * kullanıcının arka plan konum izni; izinleri uygulama ister, SDK manifeste izin eklemez.
         */
        val isGeofencingEnabled: Boolean get() = platform.geofencingEnabled
        fun setGeofencingEnabled(enabled: Boolean) = platform.setGeofencingEnabled(enabled)
    }

    /**
     * Android teslimat güçlendirme: bazı üreticilerin pil/otomatik başlatma yöneticisi arka plandaki uygulamayı durdurduğu
     * için bildirimler gecikir ya da gelmez. Bu API cihazdaki kısıtları okur ve kullanıcıyı ilgili ayar ekranına götürür.
     * Yalnızca Android'de etkilidir; iOS'ta [status] [PushizoDeliveryStatus.UNSUPPORTED] döner, [openSettings] ile
     * [showGuidance] hiçbir şey yapmadan false döner. Çağrılar istisna atmaz.
     */
    object Delivery {
        /** Hazır diyaloğun yeniden önerilme aralığı (gün; yerel SDK varsayılanıyla aynı). */
        const val DEFAULT_GUIDANCE_INTERVAL_DAYS = 14

        /** Cihazın teslim durumu: üretici, ROM, pil muafiyeti, arka plan kısıtı, risk ve önerilen ayar ekranları. */
        fun status(): PushizoDeliveryStatus = platform.deliveryStatus()

        /** Hazır açıklama diyaloğu şimdi gösterilmeli mi: teslim riski var ve diyalog son [minIntervalDays] günde gösterilmedi. */
        fun shouldShowGuidance(minIntervalDays: Int = DEFAULT_GUIDANCE_INTERVAL_DAYS): Boolean =
            platform.shouldShowDeliveryGuidance(minIntervalDays)

        /**
         * Ayar ekranını açar. [PushizoDeliverySetting.AUTO] duruma en uygun ekranı seçer; üreticiye özgü ekran bulunamazsa
         * standart Android ekranına düşülür. Bir ekran açıldıysa true. [context] Android'de Activity ise ekran aynı
         * görevde açılır.
         */
        fun openSettings(context: PushizoContext, setting: PushizoDeliverySetting = PushizoDeliverySetting.AUTO): Boolean =
            platform.openDeliverySettings(context, setting)

        /**
         * Hazır açıklama diyaloğunu gösterir (22 dil; [message] verilirse SDK metni yerine o gösterilir). Kullanıcı kabul
         * ederse [setting] ekranı açılır. [onResult]: ayar ekranı açıldıysa true; kullanıcı vazgeçtiyse ya da ekran
         * açılamadıysa false. SDK diyaloğu kendiliğinden göstermez; uygun anı uygulama seçer ([shouldShowGuidance]).
         */
        fun showGuidance(
            host: PushizoPermissionHost,
            setting: PushizoDeliverySetting = PushizoDeliverySetting.AUTO,
            message: String? = null,
            onResult: ((Boolean) -> Unit)? = null,
        ) = platform.showDeliveryGuidance(host, setting, message?.takeIf { it.isNotBlank() }, onResult)
    }

    /** Bildirim kutusu (`add_to_inbox` mesajları). Kullanıcı ve abonelik yoksa çağrılar hata fırlatır. */
    object Inbox {
        /** Öğeler (yeniden eskiye), okunmamış sayısı ve sonraki sayfa imleci; [limit] 1–200. */
        suspend fun list(limit: Int = 50, cursor: String? = null): PushizoInboxPage = platform.inboxList(limit, cursor)
        suspend fun markRead(id: String): PushizoInboxItem = platform.inboxMarkRead(id)
        /** Okundu işaretlenen öğe sayısını döndürür. */
        suspend fun markAllRead(): Int = platform.inboxMarkAllRead()
        suspend fun archive(id: String) = platform.inboxArchive(id)
        suspend fun unreadCount(): Int = platform.inboxUnreadCount()
    }

    /** İçerik kartları (ADR-0082): gösterimde [logImpression], tıklamada [logClick], kapatmada [dismiss]. */
    object ContentCards {
        /** Kart akışı (sabitlenmiş → öncelik → yeni); [limit] 1–100. */
        suspend fun list(limit: Int = 50): PushizoContentCardFeed {
            require(limit in 1..100) { "limit 1-100 olmalı." }
            return platform.contentCardsList(limit)
        }
        /** Kart ekranda gösterildi (ilk gösterim kaydedilir). */
        suspend fun logImpression(id: String): PushizoContentCard = platform.contentCardImpression(requireId(id))
        /** Karta tıklandı (gösterim de sayılır). */
        suspend fun logClick(id: String): PushizoContentCard = platform.contentCardClick(requireId(id))
        /** Kart kapatıldı; akıştan kalkar. */
        suspend fun dismiss(id: String): PushizoContentCard = platform.contentCardDismiss(requireId(id))

        private fun requireId(id: String): String {
            require(id.isNotBlank()) { "Kart kimliği gerekli." }
            return id
        }
    }

    /** Mesaj kategorisi tercihleri ve rıza kayıtları. */
    object Preferences {
        suspend fun get(): PushizoPreferencesResult = platform.getPreferences()
        suspend fun set(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult = platform.setPreferences(preferences)
        suspend fun consents(): List<PushizoConsent> = platform.getPreferences().consents
    }

    /** Özellik bayrakları: değerler oturum başında yenilenir ve cihazda saklanır. */
    object FeatureFlags {
        /** Değerleri hemen yeniler; hata son değerleri korur ve false döner. */
        suspend fun refresh(): Boolean = platform.refreshFeatureFlags()
        /** Bayrak bu kullanıcı için açık mı; değer henüz yoksa [default]. */
        fun isEnabled(key: String, default: Boolean = false): Boolean = platform.isFeatureEnabled(key, default)
        /** Bayrağın değeri (metin, sayı, mantıksal, `Map` veya `List`); değer yoksa [default]. */
        fun getValue(key: String, default: Any? = null): Any? = platform.featureFlagValue(key) ?: default
    }
}
