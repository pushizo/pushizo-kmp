package com.pushizo.kmp

/** `initialize` için platform bağlamı. Android'de `android.content.Context`. */
expect abstract class PushizoContext

/** İzin istemini açan platform nesnesi. Android'de `android.app.Activity`. */
expect open class PushizoPermissionHost

/** Yerel SDK'ya köprü: mantık burada yeniden yazılmaz, her platform kendi SDK'sına devreder (ADR-0055). */
internal interface PushizoPlatform {
    val isInitialized: Boolean
    fun initialize(context: PushizoContext, appId: String, options: PushizoOptions)
    fun setConsentGiven(given: Boolean)
    fun login(externalId: String, identityHash: String?, jwt: String?)
    fun login(externalId: String, profile: PushizoUserProfile, identityHash: String?, jwt: String?)
    fun updateUserJwt(jwt: String)
    fun logout()
    fun setLogLevel(level: PushizoLogLevel)

    fun addTags(tags: Map<String, String>)
    fun removeTags(keys: List<String>)
    fun getTags(): Map<String, String>
    fun addAliases(aliases: Map<String, String>)
    fun removeAlias(label: String)
    fun setLanguage(code: String)
    fun addEmail(email: String)
    fun removeEmail(email: String)
    fun addSms(phoneNumber: String)
    fun removeSms(phoneNumber: String)
    val emails: List<String>
    val smsNumbers: List<String>
    val externalId: String?
    val userId: String?

    val subscriptionState: PushizoSubscriptionState
    fun optIn()
    fun optOut()
    fun addSubscriptionObserver(observer: (PushizoSubscriptionState) -> Unit): PushizoListenerRegistration

    val permission: Boolean
    fun requestPermission(host: PushizoPermissionHost)
    fun onPermissionResult()
    fun addPermissionObserver(observer: (Boolean) -> Unit): PushizoListenerRegistration
    fun addClickListener(listener: (PushizoClickEvent) -> Unit): PushizoListenerRegistration
    fun addForegroundLifecycleListener(listener: (PushizoForegroundEvent) -> Unit): PushizoListenerRegistration
    fun clearAllNotifications()

    fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?)

    fun addTriggers(triggers: Map<String, String>)
    fun removeTriggers(keys: Collection<String>)
    fun clearTriggers()
    fun getTriggers(): Map<String, String>
    fun setScreen(name: String?)
    var inAppPaused: Boolean

    val locationShared: Boolean
    val locationPermissionGranted: Boolean
    fun setLocationShared(shared: Boolean)
    fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?)
    fun onLocationPermissionResult()
    val geofencingEnabled: Boolean
    fun setGeofencingEnabled(enabled: Boolean)

    suspend fun inboxList(limit: Int, cursor: String?): PushizoInboxPage
    suspend fun inboxMarkRead(id: String): PushizoInboxItem
    suspend fun inboxMarkAllRead(): Int
    suspend fun inboxArchive(id: String)
    suspend fun inboxUnreadCount(): Int

    suspend fun getPreferences(): PushizoPreferencesResult
    suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult

    suspend fun refreshFeatureFlags(): Boolean
    fun isFeatureEnabled(key: String, default: Boolean): Boolean
    /** Metin, sayı, mantıksal, `Map<String, Any?>` veya `List<Any?>`; değer yoksa null. */
    fun featureFlagValue(key: String): Any?
}

internal expect fun createPushizoPlatform(): PushizoPlatform
