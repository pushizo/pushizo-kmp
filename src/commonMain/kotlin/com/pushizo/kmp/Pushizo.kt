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
 * Pushizo.Chat.show(context)
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

    /**
     * Kullanıcıyı kimliklendirir ve [profile] alanlarını tek çağrıda ayarlar (iki platformda da yerel profilli girişe
     * gider). Geçersiz e-posta/telefon ya da kimlik kanıtında giriş yapılmaz ve `IllegalArgumentException`, SDK
     * başlatılmadıysa `IllegalStateException` fırlatılır.
     */
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

        /**
         * Kullanıcı özelliği yazar (ör. `setProperty("first_name", "Ali")`). [value] metin, sayı, mantıksal ya da bunların
         * listesi/haritası olabilir; null özelliği siler. Boş anahtarda `IllegalArgumentException`. Çevrimdışıyken kuyrukta
         * bekler. iOS yerel SDK'sı 64 karakterden uzun anahtarı ve içinde null bulunan liste/haritayı günlüğe yazıp yok sayar.
         */
        fun setProperty(key: String, value: Any?) {
            require(key.isNotBlank()) { "Özellik anahtarı boş olamaz." }
            platform.setProperty(key, value)
        }
        fun addEmail(email: String) = platform.addEmail(email)
        fun removeEmail(email: String) = platform.removeEmail(email)
        fun addSms(phoneNumber: String) = platform.addSms(phoneNumber)
        fun removeSms(phoneNumber: String) = platform.removeSms(phoneNumber)
        val emails: List<String> get() = platform.emails
        val smsNumbers: List<String> get() = platform.smsNumbers
        val externalId: String? get() = platform.externalId
        val id: String? get() = platform.userId

        /**
         * Fire OS ADM kayıt kimliği (yalnızca Android; ana FCM aboneliğinin yerine geçmez). Geçersiz kimlikte
         * `IllegalArgumentException`. iOS'ta etkisizdir.
         */
        fun registerAdm(token: String) = platform.registerAdm(token)

        /** Kategori × kanal tercihleri ([Preferences.get] ile aynı). Abonelik yoksa `IllegalStateException`. */
        suspend fun getPreferences(): PushizoPreferencesResult = platform.getPreferences()

        /** Tercihleri yazar ([Preferences.set] ile aynı). */
        suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult = platform.setPreferences(preferences)

        /**
         * Reklam kişiselleştirme rızası (ADR-0037): kullanıcının e-posta/telefon özetinin reklam platformu kitlelerine
         * gönderilmesine izin. `false` rızayı geri alır. Tercih ucundaki ayrılmış `ads_personalization` anahtarına yazılır.
         */
        suspend fun setAdsPersonalizationConsent(granted: Boolean): PushizoPreferencesResult =
            platform.setPreferences(listOf(PushizoPreferenceInput(ADS_PERSONALIZATION, null, granted)))

        /** Rıza kayıtları (`ads_personalization` …). Abonelik yoksa `IllegalStateException`. */
        suspend fun getConsents(): List<PushizoConsent> = platform.getPreferences().consents

        /** Reklam kişiselleştirme rızasının tercih ucundaki ayrılmış kategori anahtarı. */
        const val ADS_PERSONALIZATION = "ads_personalization"

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

        /** Tek bildirimi Android tepsi kimliğiyle ([PushizoNotification.androidNotificationId]) kaldırır; iOS'ta etkisizdir. */
        fun removeNotification(androidNotificationId: Int) = platform.removeNotification(androidNotificationId)

        /**
         * Tek bildirimi kimliğiyle ([PushizoNotification.identifier]) kaldırır: iOS'ta istek kimliği
         * (`UNNotificationRequest.identifier`), Android'de tepsi kimliğinin metin hâli. Android'de sayı olmayan değer
         * etkisizdir.
         */
        fun removeNotification(identifier: String) = platform.removeNotification(identifier)

        /** Aynı gruptaki (Android `android_group`, iOS `thread-id`) tüm bildirimleri kaldırır. */
        fun removeGroupedNotifications(group: String) = platform.removeGroupedNotifications(group)

        /**
         * Uygulama rozetini ayarlar. iOS: rozet ayarlanır, NSE ile paylaşılan sayaç da eşitlenir. Android: başlatıcı rozeti
         * için "sayıyı ayarla" API'si olmadığından sayı saklanır ve SDK'nın bundan sonra gösterdiği bildirimlere yazılır
         * (sayı gösteren başlatıcılar, Android 8+). Negatif değerde `IllegalArgumentException`. Sonuç gerekiyorsa
         * [setBadgeCountWithResult].
         */
        fun setBadgeCount(count: Int) {
            setBadgeCountWithResult(count)
        }

        /**
         * [setBadgeCount] ile aynıdır ve sonucu döndürür. Android: sayı saklandı ve rozet bu cihazda gösterilebiliyorsa true;
         * Android 8 öncesinde, bildirimler kapalıyken, varsayılan kanalın rozeti kapatıldıysa ya da SDK başlatılmadıysa
         * false. iOS: her zaman true.
         */
        fun setBadgeCountWithResult(count: Int): Boolean {
            require(count >= 0) { "count negatif olamaz." }
            return platform.setBadgeCount(count)
        }

        /** Rozeti sıfırlar (`setBadgeCount(0)`). */
        fun clearBadge() {
            platform.setBadgeCount(0)
        }

        /**
         * Rozet sayısı. Android: [setBadgeCount] ile saklanan son sayı. iOS: uygulama simgesindeki rozet sayısı (ana iş
         * parçacığından okunur).
         */
        val badgeCount: Int get() = platform.badgeCount
    }

    object Session {
        fun sendEvent(name: String, properties: Map<String, Any?> = emptyMap(), value: Double? = null) =
            platform.sendEvent(name, properties, value, null)

        /**
         * [eventId] ile olay gönderir: reklam pikseli/Conversions API ile aynı kimlik verilirse sunucu olayı tekilleştirir.
         * Kimlik `^[A-Za-z0-9._:-]{8,64}$` biçiminde olmalıdır; geçersizse yerel SDK kendi kimliğini üretir.
         */
        fun sendEvent(name: String, properties: Map<String, Any?> = emptyMap(), value: Double? = null, eventId: String) =
            platform.sendEvent(name, properties, value, eventId)
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

        /** In-app mesaj düğmesi/bağlantısı tıklamaları. `initialize` sonrasında eklenmelidir. */
        fun addClickListener(listener: (PushizoInAppClickEvent) -> Unit): PushizoListenerRegistration =
            platform.addInAppClickListener(listener)

        /**
         * In-app yaşam döngüsü (willDisplay/didDisplay/willDismiss/didDismiss); iki platformda da ana iş parçacığında
         * çağrılır. Android'de `initialize` sonrasında eklenmelidir. Dinleyici, dönen kayıt `remove()` edilene kadar
         * köprüde tutulur.
         */
        fun addLifecycleListener(listener: PushizoInAppLifecycleListener): PushizoListenerRegistration =
            platform.addInAppLifecycleListener(listener)
    }

    /**
     * Gömülü yerleşimler (ADR-0068): uygulamanın ayırdığı alana içerik. Model döner, çizim uygulamanındır; içerik ekrana
     * gelince [reportImpression] çağrılır. Abonelik yoksa veya onay bekleniyorsa liste boştur.
     */
    object Placements {
        private val KEY = Regex("^[a-z][a-z0-9_.-]{0,63}$")

        /** Alanın içeriği. Anahtar `^[a-z][a-z0-9_.-]{0,63}$`, [limit] 1–10; aksi hâlde `IllegalArgumentException`. */
        suspend fun list(key: String, limit: Int = 1): List<PushizoInAppMessage> {
            require(KEY.matches(key)) { "Geçersiz yerleşim anahtarı." }
            require(limit in 1..10) { "limit 1-10 olmalı." }
            return platform.placementsList(key, limit)
        }

        fun reportImpression(message: PushizoInAppMessage) = platform.placementReportImpression(message)

        fun reportClick(message: PushizoInAppMessage, buttonId: String) {
            require(buttonId.isNotBlank()) { "buttonId boş olamaz." }
            platform.placementReportClick(message, buttonId)
        }

        fun reportDismiss(message: PushizoInAppMessage) = platform.placementReportDismiss(message)
    }

    /**
     * Mobil reklam kimliği (GAID/IDFA; ADR-0037). SDK reklam kitaplığı içermez: kimliği uygulama alıp verir. Kimlik yalnızca
     * `ads_personalization` rızası olan kullanıcıda ve SHA-256 özetiyle reklam kitlesine gider; `null` sunucudaki kaydı siler.
     * Android'de GAID, iOS'ta IDFA verilir (iOS'ta ATT iznini uygulama alır).
     */
    object Ads {
        fun setAdvertisingId(id: String?) = platform.setAdvertisingId(id)
    }

    object Location {
        val isShared: Boolean get() = platform.locationShared

        /** Konum izni verilmiş mi (Android ön plan izni; iOS "kullanırken" veya "her zaman"). İstem göstermez. */
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

        /** Arka plan konum izni verilmiş mi (Android `ACCESS_BACKGROUND_LOCATION`, iOS "her zaman" izni). */
        val backgroundPermissionGranted: Boolean get() = platform.locationBackgroundPermissionGranted

        /**
         * Arka plan konum iznini ister. Android: açıklama diyaloğu ([rationale]; null ise SDK metni) ve sistem istemi; sonuç
         * için Activity `onRequestPermissionsResult` içinden [onPermissionResult] çağrılır. iOS: "her zaman" izni istenir
         * ([rationale] yok sayılır). Önce ön plan konum izni verilmiş olmalıdır.
         */
        fun requestBackgroundPermission(host: PushizoPermissionHost, rationale: String? = null) =
            platform.requestBackgroundLocationPermission(host, rationale)

        /** [requestBackgroundPermission] ile aynıdır (iOS adlandırması). */
        fun requestAlwaysPermission(host: PushizoPermissionHost) = platform.requestBackgroundLocationPermission(host, null)
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

    /**
     * Canlı sohbet (ADR-0040). Android'de yerel `Pushizo.Chat`'e, iOS'ta Objective-C cephesindeki `PZChat`'e devreder.
     * Kendi arayüzünüz için veri API'si ([start], [getMessages], [sendMessage], [addListener] …), hazır ekran için
     * [show] / [hide] kullanılır. SDK başlatılmadıysa değer döndüren çağrılar `IllegalStateException`, geçersiz girdi
     * `IllegalArgumentException` fırlatır.
     */
    object Chat {
        /** Sohbet bu platformda bağlı mı (Android ve iOS'ta true). */
        val isSupported: Boolean get() = platform.chatSupported
        val visitorId: String? get() = platform.chatVisitorId

        /** Son bilinen okunmamış mesaj sayısı (ağ çağrısı yapmaz). */
        val unreadCount: Int get() = platform.chatUnreadCount

        /** Canlı akış ([startListening]) açık mı. */
        val isListening: Boolean get() = platform.chatIsListening

        fun addListener(listener: PushizoChatListener): PushizoListenerRegistration = platform.addChatListener(listener)

        /** Sohbet bildirimine tıklama. */
        fun addOpenListener(listener: (PushizoChatPushInfo) -> Unit): PushizoListenerRegistration =
            platform.addChatOpenListener(listener)

        /** Ziyaretçiyi tanıtır ve canlı akışı başlatır. */
        suspend fun start(name: String? = null, email: String? = null, language: String? = null): PushizoChatIdentifyResult =
            platform.chatStart(name, email, language)

        /** Ziyaretçiyi tanıtır (akışı başlatmaz). */
        suspend fun identify(name: String? = null, email: String? = null, language: String? = null): PushizoChatIdentifyResult =
            platform.chatIdentify(name, email, language)

        suspend fun getConfig(language: String? = null): PushizoChatConfig = platform.chatGetConfig(language)
        suspend fun getConversation(): PushizoChatConversationState = platform.chatGetConversation()

        suspend fun getMessages(afterSeq: Long? = null, beforeSeq: Long? = null, limit: Int = 50): PushizoChatMessagePage =
            platform.chatGetMessages(afterSeq, beforeSeq, limit)

        /** Mesaj gönderir; çevrimdışıyken kuyrukta bekler ve `pending` durumlu mesaj döner. */
        suspend fun sendMessage(text: String, optionId: String? = null): PushizoChatMessage = platform.chatSendMessage(text, optionId)

        suspend fun sendAttachment(bytes: ByteArray, fileName: String, mimeType: String, text: String? = null): PushizoChatMessage =
            platform.chatSendAttachment(bytes, fileName, mimeType, text)

        /** Verilen sıra numarasına kadar okundu işaretler; kalan okunmamış sayısını döner. */
        suspend fun markRead(seq: Long): Int = platform.chatMarkRead(seq)
        suspend fun refreshUnreadCount(): Int = platform.chatRefreshUnreadCount()

        suspend fun leaveOfflineMessage(email: String, text: String, name: String? = null): PushizoChatMessage =
            platform.chatLeaveOfflineMessage(email, text, name)

        fun setTyping(typing: Boolean) = platform.chatSetTyping(typing)
        fun startListening() = platform.chatStartListening()
        fun stopListening() = platform.chatStopListening()

        /**
         * Ek indirme/görüntüleme adresi (ziyaretçi belirtecini taşır; loglamayın). SDK başlatılmadıysa
         * `IllegalStateException`.
         */
        fun attachmentUrl(attachmentId: String, download: Boolean = false): String? = platform.chatAttachmentUrl(attachmentId, download)

        /**
         * Hazır sohbet ekranını açar; ekran zaten açıksa yenisi açılmaz. Android: `HsChatActivity` başlatılır ([context]
         * Activity değilse yeni görevde). iOS: etkin sahnenin en üstteki görünüm denetleyicisinden modal sunulur
         * (`PushizoApplicationContext` verilir). SDK başlatılmadıysa hiçbir şey yapılmaz.
         */
        fun show(context: PushizoContext) = platform.chatShow(context, null, true)

        /**
         * Hazır sohbet ekranını [host] üzerinden açar: Android'de Activity ile aynı görevde, iOS'ta verilen görünüm
         * denetleyicisinden modal ([animated] yalnızca iOS'ta kullanılır).
         */
        fun show(host: PushizoPermissionHost, animated: Boolean = true) = platform.chatShow(null, host, animated)

        /** [show] ile açılan sohbet ekranını kapatır; açık bir ekran varsa true ([animated] yalnızca iOS'ta kullanılır). */
        fun hide(animated: Boolean = true): Boolean = platform.chatHide(animated)

        /**
         * Hazır sohbet ekranı şu an açık mı. iOS'ta yerel değerdir. Android'de yerel karşılığı olmadığından köprü sohbet
         * ekranının yaşam döngüsünü izler: izleme köprünün `initialize` ya da [show] çağrısıyla başlar, öncesinde false
         * döner.
         */
        val isPresented: Boolean get() = platform.chatPresented
    }

    /**
     * iOS Live Activities (ADR-0024), genel `PushizoDefaultLiveActivityAttributes` türüyle. Yalnızca iOS'ta etkilidir;
     * Android'de [isSupported] false, [defaultActivityType] null döner ve çağrılar hiçbir şey yapmadan false döner.
     * ActivityKit genelleri (`register`, `startTracking`) Swift'ten kullanılır.
     */
    object LiveActivities {
        private val ACTIVITY_ID = Regex("^[A-Za-z0-9_.:-]{1,100}$")

        /** Sunucunun uzaktan başlatmada `attributes` içine yazdığı ayrılmış anahtar. */
        private const val RESERVED_KEY = "pushizo"

        /** Live Activities bu platformda var mı (iOS true, Android false). iOS sürümü ayrıca denetlenir (16.1+). */
        val isSupported: Boolean get() = platform.liveActivitiesSupported

        /** Genel türün sunucudaki `activity_type` değeri; Android'de null. */
        val defaultActivityType: String? get() = platform.defaultLiveActivityType

        /**
         * Genel türü izlemeye alır: push-to-start belirteci (iOS 17.2+) ve başlayan her etkinliğin güncelleme belirteci
         * sunucuya kaydedilir. `initialize` sonrasında, uygulama her açıldığında çağrılır. iOS'ta kayıt yapıldıysa true;
         * SDK başlatılmadıysa `IllegalStateException`, iOS 16.1 öncesinde ve Mac Catalyst'te
         * `UnsupportedOperationException`. Android'de etkisizdir ve false döner.
         */
        fun setupDefaultLiveActivity(): Boolean = platform.setupDefaultLiveActivity()

        /**
         * Genel türle yerel bir etkinlik başlatır (iOS 16.1+); sunucu sonraki `update`/`end` çağrılarını [activityId] ile
         * yapar. [activityId] 1-100 karakter (harf, rakam ve `_ . : -`); değerler metin, sonlu sayı veya mantıksal;
         * anahtarlar 1-64 karakter; [attributes] içinde `pushizo` anahtarı ayrılmıştır. Kurala uymayan girdi iki platformda
         * da `IllegalArgumentException` fırlatır. iOS'ta etkinlik başladıysa true; SDK başlatılmadıysa
         * `IllegalStateException`, sürüm desteklemiyorsa `UnsupportedOperationException`, sistem reddettiyse
         * `RuntimeException`. Android'de etkisizdir ve false döner.
         */
        fun startDefaultLiveActivity(
            activityId: String,
            attributes: Map<String, Any> = emptyMap(),
            contentState: Map<String, Any> = emptyMap(),
        ): Boolean {
            require(ACTIVITY_ID.matches(activityId)) { "activityId 1-100 karakter olmalı (harf, rakam ve _ . : -)." }
            checkValues(attributes, "attributes", RESERVED_KEY)
            checkValues(contentState, "contentState", null)
            return platform.startDefaultLiveActivity(activityId, attributes, contentState)
        }

        private fun checkValues(values: Map<String, Any>, name: String, reservedKey: String?) {
            for ((key, value) in values) {
                require(key.length in 1..64) { "$name anahtarları 1-64 karakter olmalı." }
                require(key != reservedKey) { "$name içinde '$reservedKey' anahtarı ayrılmıştır." }
                val valid = when (value) {
                    is String, is Boolean -> true
                    is Float -> value.isFinite()
                    is Double -> value.isFinite()
                    is Number -> true
                    else -> false
                }
                require(valid) { "$name.$key metin, sonlu sayı veya mantıksal olmalı." }
            }
        }
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

        /**
         * Kutu değişim gözlemcisi (ana iş parçacığında): `read`, `read_all` ve `archived` her zaman, `listed` ve `count`
         * yalnızca okunmamış sayısı değiştiğinde bildirilir. Dönen kayıt `remove()` ile kaldırılır.
         */
        fun addObserver(observer: (PushizoInboxChangeEvent) -> Unit): PushizoListenerRegistration =
            platform.addInboxObserver(observer)
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

        /**
         * Bayrak değerleri sunucudan her başarıyla yenilendiğinde (oturum başı ve [refresh]) ana iş parçacığında çağrılır;
         * güncel değerler [isEnabled] / [getValue] ile okunur. Dönen kayıt `remove()` ile kaldırılır.
         */
        fun addObserver(observer: () -> Unit): PushizoListenerRegistration = platform.addFeatureFlagObserver(observer)
    }
}
