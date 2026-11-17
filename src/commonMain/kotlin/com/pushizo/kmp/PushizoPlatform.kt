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
    /** Fire OS ADM kayıt kimliği (yalnızca Android; iOS'ta etkisiz). */
    fun registerAdm(token: String)

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
    /** Android tepsi kimliğiyle kaldırır; iOS'ta etkisiz. */
    fun removeNotification(androidNotificationId: Int)
    /** iOS istek kimliğiyle kaldırır; Android'de sayısal kimlikse tepsi kimliği sayılır, değilse etkisiz. */
    fun removeNotification(identifier: String)
    fun removeGroupedNotifications(group: String)
    /** Uygulama rozeti (yalnızca iOS; Android'de etkisiz). */
    fun setBadgeCount(count: Int)

    /** Mobil reklam kimliği (ADR-0037). */
    fun setAdvertisingId(id: String?)

    fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?)

    fun addTriggers(triggers: Map<String, String>)
    fun removeTriggers(keys: Collection<String>)
    fun clearTriggers()
    fun getTriggers(): Map<String, String>
    fun setScreen(name: String?)
    var inAppPaused: Boolean
    fun addInAppClickListener(listener: (PushizoInAppClickEvent) -> Unit): PushizoListenerRegistration
    fun addInAppLifecycleListener(listener: PushizoInAppLifecycleListener): PushizoListenerRegistration

    suspend fun placementsList(key: String, limit: Int): List<PushizoInAppMessage>
    fun placementReportImpression(message: PushizoInAppMessage)
    fun placementReportClick(message: PushizoInAppMessage, buttonId: String)
    fun placementReportDismiss(message: PushizoInAppMessage)

    val locationShared: Boolean
    val locationPermissionGranted: Boolean
    fun setLocationShared(shared: Boolean)
    fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?)
    fun onLocationPermissionResult()
    val geofencingEnabled: Boolean
    fun setGeofencingEnabled(enabled: Boolean)
    val locationBackgroundPermissionGranted: Boolean
    /** Arka plan konum izni: Android `ACCESS_BACKGROUND_LOCATION`, iOS "her zaman" izni ([rationale] iOS'ta yok sayılır). */
    fun requestBackgroundLocationPermission(host: PushizoPermissionHost, rationale: String?)

    /** Android teslimat güçlendirme (ADR-0092). iOS'ta durum [PushizoDeliveryStatus.UNSUPPORTED], diğerleri etkisiz false. */
    fun deliveryStatus(): PushizoDeliveryStatus
    fun shouldShowDeliveryGuidance(minIntervalDays: Int): Boolean
    fun openDeliverySettings(context: PushizoContext, setting: PushizoDeliverySetting): Boolean
    fun showDeliveryGuidance(host: PushizoPermissionHost, setting: PushizoDeliverySetting, message: String?, onResult: ((Boolean) -> Unit)?)

    suspend fun inboxList(limit: Int, cursor: String?): PushizoInboxPage
    suspend fun inboxMarkRead(id: String): PushizoInboxItem
    suspend fun inboxMarkAllRead(): Int
    suspend fun inboxArchive(id: String)
    suspend fun inboxUnreadCount(): Int

    suspend fun contentCardsList(limit: Int): PushizoContentCardFeed
    suspend fun contentCardImpression(id: String): PushizoContentCard
    suspend fun contentCardClick(id: String): PushizoContentCard
    suspend fun contentCardDismiss(id: String): PushizoContentCard

    suspend fun getPreferences(): PushizoPreferencesResult
    suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult

    /** Canlı sohbet bu platformda bağlı mı (Android true; iOS'ta Objective-C cephesinde sohbet olmadığından false). */
    val chatSupported: Boolean
    val chatVisitorId: String?
    val chatUnreadCount: Int
    val chatIsListening: Boolean
    fun addChatListener(listener: PushizoChatListener): PushizoListenerRegistration
    fun addChatOpenListener(listener: (PushizoChatPushInfo) -> Unit): PushizoListenerRegistration
    suspend fun chatStart(name: String?, email: String?, language: String?): PushizoChatIdentifyResult
    suspend fun chatIdentify(name: String?, email: String?, language: String?): PushizoChatIdentifyResult
    suspend fun chatGetConfig(language: String?): PushizoChatConfig
    suspend fun chatGetConversation(): PushizoChatConversationState
    suspend fun chatGetMessages(afterSeq: Long?, beforeSeq: Long?, limit: Int): PushizoChatMessagePage
    suspend fun chatSendMessage(text: String, optionId: String?): PushizoChatMessage
    suspend fun chatSendAttachment(bytes: ByteArray, fileName: String, mimeType: String, text: String?): PushizoChatMessage
    suspend fun chatMarkRead(seq: Long): Int
    suspend fun chatRefreshUnreadCount(): Int
    suspend fun chatLeaveOfflineMessage(email: String, text: String, name: String?): PushizoChatMessage
    fun chatSetTyping(typing: Boolean)
    fun chatStartListening()
    fun chatStopListening()
    /** Ek adresi; sohbetin bağlı olmadığı platformda null. */
    fun chatAttachmentUrl(attachmentId: String, download: Boolean): String?

    suspend fun refreshFeatureFlags(): Boolean
    fun isFeatureEnabled(key: String, default: Boolean): Boolean
    /** Metin, sayı, mantıksal, `Map<String, Any?>` veya `List<Any?>`; değer yoksa null. */
    fun featureFlagValue(key: String): Any?
}

internal expect fun createPushizoPlatform(): PushizoPlatform
