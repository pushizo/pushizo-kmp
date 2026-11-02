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
    }
}
