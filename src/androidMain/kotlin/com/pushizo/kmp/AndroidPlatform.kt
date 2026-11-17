package com.pushizo.kmp

import android.app.Activity
import android.content.Context
import com.pushizo.sdk.HsChatConfig
import com.pushizo.sdk.HsChatConversation
import com.pushizo.sdk.HsChatConversationState
import com.pushizo.sdk.HsChatIdentifyResult
import com.pushizo.sdk.HsChatListener
import com.pushizo.sdk.HsChatMessage
import com.pushizo.sdk.HsChatMessagePage
import com.pushizo.sdk.HsChatOpenListener
import com.pushizo.sdk.HsChatPushInfo
import com.pushizo.sdk.HsClickEvent
import com.pushizo.sdk.HsConsent
import com.pushizo.sdk.HsForegroundEvent
import com.pushizo.sdk.HsContentCard
import com.pushizo.sdk.HsContentCardFeed
import com.pushizo.sdk.HsDeliveryRisk
import com.pushizo.sdk.HsDeliverySetting
import com.pushizo.sdk.HsDeliveryStatus
import com.pushizo.sdk.HsInAppAction
import com.pushizo.sdk.HsInAppBlock
import com.pushizo.sdk.HsInAppClickEvent
import com.pushizo.sdk.HsInAppLifecycleEvent
import com.pushizo.sdk.HsInAppLifecycleListener
import com.pushizo.sdk.HsInAppMessage
import com.pushizo.sdk.HsInboxItem
import com.pushizo.sdk.HsInboxPage
import com.pushizo.sdk.HsLog
import com.pushizo.sdk.HsMessageCategory
import com.pushizo.sdk.HsNotification
import com.pushizo.sdk.HsPreference
import com.pushizo.sdk.HsPreferenceInput
import com.pushizo.sdk.HsPreferencesResult
import com.pushizo.sdk.HsPushSubscriptionState
import com.pushizo.sdk.HsUserProfile
import com.pushizo.sdk.Pushizo as Native
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.json.JSONArray
import org.json.JSONObject

actual typealias PushizoContext = Context

actual typealias PushizoPermissionHost = Activity

internal actual fun createPushizoPlatform(): PushizoPlatform = AndroidPushizoPlatform

internal fun PushizoLogLevel.toNative(): HsLog.Level = when (this) {
    PushizoLogLevel.NONE -> HsLog.Level.NONE
    PushizoLogLevel.ERROR -> HsLog.Level.ERROR
    PushizoLogLevel.WARN -> HsLog.Level.WARN
    PushizoLogLevel.INFO -> HsLog.Level.INFO
    PushizoLogLevel.DEBUG -> HsLog.Level.DEBUG
}

/** `apiUrl` verilmezse yerel varsayılan korunur; bölgesel adres öğrenme yalnızca o varsayılanda çalışır. */
internal fun PushizoOptions.toNative(): Native.Options {
    val base = Native.Options(requiresPrivacyConsent = requiresPrivacyConsent, logLevel = logLevel.toNative())
    return apiUrl?.takeIf { it.isNotBlank() }?.let { base.copy(apiUrl = it) } ?: base
}

internal fun PushizoUserProfile.toNative() = HsUserProfile(email, sms, tags, aliases)

internal fun HsNotification.toCommon() = PushizoNotification(
    title = title,
    subtitle = subtitle,
    body = body,
    imageUrl = imageUrl,
    url = url,
    deliveryId = deliveryId,
    messageId = messageId,
    actions = actions.map { PushizoNotificationAction(it.id, it.title, it.url) },
    data = data,
).also { it.androidNotificationId = androidNotificationId }

internal fun HsClickEvent.toCommon() = PushizoClickEvent(notification.toCommon(), actionId, url)

internal fun HsPushSubscriptionState.toCommon() = PushizoSubscriptionState(id, token, optedIn, permission)

internal fun HsInboxItem.toCommon() = PushizoInboxItem(id, messageId, title, body, url, imageUrl, data, read, readAt, archivedAt, expiresAt, createdAt)

internal fun HsInboxPage.toCommon() = PushizoInboxPage(items.map { it.toCommon() }, unreadCount, nextCursor, hasMore)

internal fun HsContentCard.toCommon() = PushizoContentCard(
    id, messageId, PushizoContentCardType.from(type.wire), title, body, url, imageUrl, buttonText, data,
    pinned, priority, viewed, clicked, expiresAt, createdAt,
)

internal fun HsContentCardFeed.toCommon() = PushizoContentCardFeed(cards.map { it.toCommon() }, unviewedCount)

internal fun HsPreference.toCommon() = PushizoPreference(category, channel, optedIn, explicit, source, updatedAt)

internal fun HsMessageCategory.toCommon() = PushizoMessageCategory(key, name, description, channels, defaultOptedIn)

internal fun HsConsent.toCommon() = PushizoConsent(purpose, granted, source, updatedAt)

internal fun HsPreferencesResult.toCommon() =
    PushizoPreferencesResult(preferences.map { it.toCommon() }, categories.map { it.toCommon() }, consents.map { it.toCommon() })

internal fun PushizoPreferenceInput.toNative() = HsPreferenceInput(category, channel, optedIn)

/** Risk ve ayar ekranı adları yerel enum'larla aynıdır; yerel SDK'nın ileride ekleyeceği, bu sürümün tanımadığı ad atlanır. */
internal fun HsDeliveryRisk.toCommon(): PushizoDeliveryRisk =
    PushizoDeliveryRisk.entries.firstOrNull { it.name == name } ?: PushizoDeliveryRisk.NONE

internal fun HsDeliverySetting.toCommon(): PushizoDeliverySetting? = PushizoDeliverySetting.entries.firstOrNull { it.name == name }

internal fun PushizoDeliverySetting.toNative(): HsDeliverySetting =
    HsDeliverySetting.entries.firstOrNull { it.name == name } ?: HsDeliverySetting.AUTO

internal fun HsDeliveryStatus.toCommon() = PushizoDeliveryStatus(
    supported = true,
    manufacturer = manufacturer,
    brand = brand,
    vendor = vendor.id,
    aggressiveVendor = vendor.aggressive,
    rom = rom,
    romVersion = romVersion,
    notificationsEnabled = notificationsEnabled,
    channelBlocked = channelBlocked,
    batteryOptimizationExempt = batteryOptimizationExempt,
    backgroundRestricted = backgroundRestricted,
    standbyBucket = standbyBucket?.id,
    powerSaveMode = powerSaveMode,
    dataSaverEnabled = dataSaverEnabled,
    restricted = restricted,
    risk = risk.toCommon(),
    recommendedSettings = recommendedSettings.mapNotNull { it.toCommon() },
)

internal fun HsInAppBlock.Button.toCommon(): PushizoInAppButton {
    val (type, value, tags) = when (val a = action) {
        HsInAppAction.Dismiss -> Triple("dismiss", null, emptyMap<String, String>())
        is HsInAppAction.Url -> Triple("url", a.url, emptyMap())
        is HsInAppAction.Deeplink -> Triple("deeplink", a.url, emptyMap())
        is HsInAppAction.Tags -> Triple("tags", null, a.tags)
        HsInAppAction.PushPrompt -> Triple("push_prompt", null, emptyMap())
        is HsInAppAction.Outcome -> Triple("outcome", a.name, emptyMap())
    }
    return PushizoInAppButton(id, text, type, value, tags, backgroundColor, textColor)
}

/** Blok türleri iOS Objective-C cephesiyle aynı düz biçime (`kind`) çevrilir. */
internal fun HsInAppBlock.toCommon(): PushizoInAppBlock = when (this) {
    is HsInAppBlock.Image -> PushizoInAppBlock("image", url = url, alt = alt)
    is HsInAppBlock.Title -> PushizoInAppBlock("title", text = text)
    is HsInAppBlock.Body -> PushizoInAppBlock("body", text = text)
    is HsInAppBlock.Button -> PushizoInAppBlock("button", button = toCommon())
    is HsInAppBlock.Carousel -> PushizoInAppBlock(
        "carousel",
        cards = cards.map { PushizoInAppCarouselCard(it.id, it.title, it.body, it.imageUrl, it.imageAlt, it.button?.toCommon()) },
    )
}

internal fun HsInAppMessage.toCommon(): PushizoInAppMessage = PushizoInAppMessage(
    id = id,
    variantId = variantId,
    layout = display.layout.wire,
    contentType = display.contentType.wire,
    html = display.html,
    backgroundColor = display.backgroundColor,
    textColor = display.textColor,
    dismissible = display.dismissible,
    blocks = display.blocks.map { it.toCommon() },
).also { it.native = this }

internal fun HsInAppClickEvent.toCommon() = PushizoInAppClickEvent(messageId, actionId, url)

internal fun HsChatMessage.toCommon() = PushizoChatMessage(
    id, seq, clientMessageId, sender, authorName, text,
    attachments.map { PushizoChatAttachment(it.id, it.name, it.type, it.size) },
    quickReplies?.map { PushizoChatQuickReply(it.id, it.label) },
    status, createdAt,
)

internal fun HsChatConversation.toCommon() =
    PushizoChatConversation(id, status, lastSeq, agentLastReadSeq, visitorLastReadSeq, unreadCount, botActive, assigneeName, createdAt)

internal fun HsChatConversationState.toCommon() = PushizoChatConversationState(conversation?.toCommon(), unreadCount)

internal fun HsChatMessagePage.toCommon() = PushizoChatMessagePage(messages.map { it.toCommon() }, hasMore)

internal fun HsChatIdentifyResult.toCommon() = PushizoChatIdentifyResult(userId, conversation?.toCommon())

internal fun HsChatConfig.toCommon() = PushizoChatConfig(
    enabled, online, withinBusinessHours, color, textColor, position, theme, title, greeting, offlineMessage,
    collectEmailOffline, requireEmail, attachmentsEnabled, attachmentMaxBytes, attachmentTypes, language,
)

internal fun HsChatPushInfo.toCommon() = PushizoChatPushInfo(conversationId, seq)

/** Ortak dinleyiciyi yerel sohbet dinleyicisine uyarlar. */
internal class AndroidChatListener(private val listener: PushizoChatListener) : HsChatListener {
    override fun onMessage(message: HsChatMessage) = listener.onMessage(message.toCommon())
    override fun onTyping(typing: Boolean, name: String?) = listener.onTyping(typing, name)
    override fun onAgentRead(seq: Long) = listener.onAgentRead(seq)
    override fun onUnreadCountChanged(count: Int) = listener.onUnreadCountChanged(count)
    override fun onConversationUpdated(conversationId: String, status: String, botActive: Boolean) =
        listener.onConversationUpdated(conversationId, status, botActive)
    override fun onConnectionChanged(connected: Boolean) = listener.onConnectionChanged(connected)
    override fun onError(code: String, message: String?) = listener.onError(code, message)
}

/** Ortak yaşam döngüsü dinleyicisini yerel dinleyiciye uyarlar. */
internal class AndroidInAppLifecycleListener(private val listener: PushizoInAppLifecycleListener) : HsInAppLifecycleListener {
    private fun HsInAppLifecycleEvent.common() = PushizoInAppLifecycleEvent(message.toCommon())
    override fun onWillDisplay(event: HsInAppLifecycleEvent) = listener.onWillDisplay(event.common())
    override fun onDidDisplay(event: HsInAppLifecycleEvent) = listener.onDidDisplay(event.common())
    override fun onWillDismiss(event: HsInAppLifecycleEvent) = listener.onWillDismiss(event.common())
    override fun onDidDismiss(event: HsInAppLifecycleEvent) = listener.onDidDismiss(event.common())
}

/** Raporlama yalnızca `Placements.list` sonucundan gelen (yerel nesnesi olan) mesajla yapılır; diğerleri yok sayılır. */
internal fun PushizoInAppMessage.nativeAndroid(): HsInAppMessage? = native as? HsInAppMessage

/** Bayrak değerindeki `org.json` türleri ortak koleksiyonlara çevrilir; `JSONObject.NULL` null olur. */
internal fun Any?.toCommonJson(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { get(it).toCommonJson() }
    is JSONArray -> (0 until length()).map { get(it).toCommonJson() }
    else -> this
}

private class AndroidForegroundEvent(private val event: HsForegroundEvent) : PushizoForegroundEvent {
    override val notification: PushizoNotification = event.notification.toCommon()
    override fun preventDefault() = event.preventDefault()
    override fun display() = event.display()
}

internal object AndroidPushizoPlatform : PushizoPlatform {
    override val isInitialized: Boolean get() = Native.isInitialized

    override fun initialize(context: PushizoContext, appId: String, options: PushizoOptions) =
        Native.initialize(context, appId, options.toNative())

    override fun setConsentGiven(given: Boolean) = Native.setConsentGiven(given)
    override fun login(externalId: String, identityHash: String?, jwt: String?) = Native.login(externalId, identityHash, jwt)
    override fun login(externalId: String, profile: PushizoUserProfile, identityHash: String?, jwt: String?) =
        Native.login(externalId, profile.toNative(), identityHash, jwt)

    override fun updateUserJwt(jwt: String) = Native.updateUserJwt(jwt)
    override fun logout() = Native.logout()
    override fun setLogLevel(level: PushizoLogLevel) = Native.Debug.setLogLevel(level.toNative())

    override fun addTags(tags: Map<String, String>) = Native.User.addTags(tags)
    override fun removeTags(keys: List<String>) = Native.User.removeTags(keys)
    override fun getTags(): Map<String, String> = Native.User.getTags()
    override fun addAliases(aliases: Map<String, String>) = Native.User.addAliases(aliases)
    override fun removeAlias(label: String) = Native.User.removeAlias(label)
    override fun setLanguage(code: String) = Native.User.setLanguage(code)
    override fun addEmail(email: String) = Native.User.addEmail(email)
    override fun removeEmail(email: String) = Native.User.removeEmail(email)
    override fun addSms(phoneNumber: String) = Native.User.addSms(phoneNumber)
    override fun removeSms(phoneNumber: String) = Native.User.removeSms(phoneNumber)
    override val emails: List<String> get() = Native.User.emails
    override val smsNumbers: List<String> get() = Native.User.smsNumbers
    override val externalId: String? get() = Native.User.externalId
    override val userId: String? get() = Native.User.id
    override fun registerAdm(token: String) = Native.User.registerAdm(token)

    override val subscriptionState: PushizoSubscriptionState get() = Native.User.pushSubscription.state.toCommon()
    override fun optIn() = Native.User.pushSubscription.optIn()
    override fun optOut() = Native.User.pushSubscription.optOut()

    override fun addSubscriptionObserver(observer: (PushizoSubscriptionState) -> Unit): PushizoListenerRegistration {
        val native: (HsPushSubscriptionState) -> Unit = { observer(it.toCommon()) }
        Native.User.pushSubscription.addObserver(native)
        return PushizoListenerRegistration { Native.User.pushSubscription.removeObserver(native) }
    }

    override val permission: Boolean get() = Native.Notifications.permission
    override fun requestPermission(host: PushizoPermissionHost) = Native.Notifications.requestPermission(host)
    override fun onPermissionResult() = Native.Notifications.onPermissionResult()

    override fun addPermissionObserver(observer: (Boolean) -> Unit): PushizoListenerRegistration {
        val native = Native.Notifications.PermissionObserver { observer(it) }
        Native.Notifications.addPermissionObserver(native)
        return PushizoListenerRegistration { Native.Notifications.removePermissionObserver(native) }
    }

    override fun addClickListener(listener: (PushizoClickEvent) -> Unit): PushizoListenerRegistration {
        val native: (HsClickEvent) -> Unit = { listener(it.toCommon()) }
        Native.Notifications.addClickListener(native)
        return PushizoListenerRegistration { Native.Notifications.removeClickListener(native) }
    }

    override fun addForegroundLifecycleListener(listener: (PushizoForegroundEvent) -> Unit): PushizoListenerRegistration {
        val native: (HsForegroundEvent) -> Unit = { listener(AndroidForegroundEvent(it)) }
        Native.Notifications.addForegroundLifecycleListener(native)
        return PushizoListenerRegistration { Native.Notifications.removeForegroundLifecycleListener(native) }
    }

    override fun clearAllNotifications() = Native.Notifications.clearAll()
    override fun removeNotification(androidNotificationId: Int) = Native.Notifications.removeNotification(androidNotificationId)

    /** Android'de bildirim kimliği sayıdır; sayı olmayan (iOS) kimlik yok sayılır. */
    override fun removeNotification(identifier: String) {
        identifier.trim().toIntOrNull()?.let(Native.Notifications::removeNotification)
    }

    override fun removeGroupedNotifications(group: String) = Native.Notifications.removeGroupedNotifications(group)

    /** Android'de rozet görünen bildirimlerden hesaplanır; ayarlanacak bir sayaç yoktur. */
    override fun setBadgeCount(count: Int) = Unit

    override fun setAdvertisingId(id: String?) = Native.Ads.setAdvertisingId(id)

    override fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?) =
        Native.Session.sendEvent(name, properties, value)

    override fun addTriggers(triggers: Map<String, String>) = Native.InAppMessages.addTriggers(triggers)
    override fun removeTriggers(keys: Collection<String>) = Native.InAppMessages.removeTriggers(keys)
    override fun clearTriggers() = Native.InAppMessages.clearTriggers()
    override fun getTriggers(): Map<String, String> = Native.InAppMessages.getTriggers()
    override fun setScreen(name: String?) = Native.InAppMessages.setScreen(name)
    override var inAppPaused: Boolean
        get() = Native.InAppMessages.paused
        set(value) { Native.InAppMessages.paused = value }

    override fun addInAppClickListener(listener: (PushizoInAppClickEvent) -> Unit): PushizoListenerRegistration {
        val native: (HsInAppClickEvent) -> Unit = { listener(it.toCommon()) }
        Native.InAppMessages.addClickListener(native)
        return PushizoListenerRegistration { Native.InAppMessages.removeClickListener(native) }
    }

    override fun addInAppLifecycleListener(listener: PushizoInAppLifecycleListener): PushizoListenerRegistration {
        val native = AndroidInAppLifecycleListener(listener)
        Native.InAppMessages.addLifecycleListener(native)
        return PushizoListenerRegistration { Native.InAppMessages.removeLifecycleListener(native) }
    }

    override suspend fun placementsList(key: String, limit: Int): List<PushizoInAppMessage> =
        suspendCoroutine { cont -> Native.Placements.list(key, limit) { messages -> cont.resume(messages.map { it.toCommon() }) } }

    override fun placementReportImpression(message: PushizoInAppMessage) {
        message.nativeAndroid()?.let(Native.Placements::reportImpression)
    }

    override fun placementReportClick(message: PushizoInAppMessage, buttonId: String) {
        message.nativeAndroid()?.let { Native.Placements.reportClick(it, buttonId) }
    }

    override fun placementReportDismiss(message: PushizoInAppMessage) {
        message.nativeAndroid()?.let(Native.Placements::reportDismiss)
    }

    override val locationShared: Boolean get() = Native.Location.isShared
    override val locationPermissionGranted: Boolean get() = Native.Location.permissionGranted
    override fun setLocationShared(shared: Boolean) = Native.Location.setShared(shared)
    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        Native.Location.requestPermission(host, rationale)

    override fun onLocationPermissionResult() = Native.Location.onPermissionResult()
    override val geofencingEnabled: Boolean get() = Native.Location.isGeofencingEnabled
    override fun setGeofencingEnabled(enabled: Boolean) = Native.Location.setGeofencingEnabled(enabled)
    override val locationBackgroundPermissionGranted: Boolean get() = Native.Location.backgroundPermissionGranted
    override fun requestBackgroundLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        Native.Location.requestBackgroundPermission(host, rationale)

    // Android teslimat güçlendirme (ADR-0092): teşhis, ayar ekranı adayları ve diyalog yerel SDK'dadır.
    override fun deliveryStatus(): PushizoDeliveryStatus = Native.Delivery.status()?.toCommon() ?: PushizoDeliveryStatus.UNSUPPORTED
    override fun shouldShowDeliveryGuidance(minIntervalDays: Int): Boolean = Native.Delivery.shouldShowGuidance(minIntervalDays)
    override fun openDeliverySettings(context: PushizoContext, setting: PushizoDeliverySetting): Boolean =
        Native.Delivery.openSettings(context, setting.toNative())

    override fun showDeliveryGuidance(host: PushizoPermissionHost, setting: PushizoDeliverySetting, message: String?, onResult: ((Boolean) -> Unit)?) =
        Native.Delivery.showGuidance(host, setting.toNative(), message, onResult)

    override suspend fun inboxList(limit: Int, cursor: String?): PushizoInboxPage = Native.Inbox.list(limit, cursor).toCommon()
    override suspend fun inboxMarkRead(id: String): PushizoInboxItem = Native.Inbox.markRead(id).toCommon()
    override suspend fun inboxMarkAllRead(): Int = Native.Inbox.markAllRead()
    override suspend fun inboxArchive(id: String) = Native.Inbox.archive(id)
    override suspend fun inboxUnreadCount(): Int = Native.Inbox.unreadCount()

    override suspend fun contentCardsList(limit: Int): PushizoContentCardFeed = Native.ContentCards.list(limit).toCommon()
    override suspend fun contentCardImpression(id: String): PushizoContentCard = Native.ContentCards.logImpression(id).toCommon()
    override suspend fun contentCardClick(id: String): PushizoContentCard = Native.ContentCards.logClick(id).toCommon()
    override suspend fun contentCardDismiss(id: String): PushizoContentCard = Native.ContentCards.dismiss(id).toCommon()

    override suspend fun getPreferences(): PushizoPreferencesResult = Native.User.getPreferences().toCommon()
    override suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult =
        Native.User.setPreferences(preferences.map { it.toNative() }).toCommon()

    // Canlı sohbet (ADR-0040): yerel `Pushizo.Chat`.
    override val chatSupported: Boolean get() = true
    override val chatVisitorId: String? get() = Native.Chat.visitorId
    override val chatUnreadCount: Int get() = Native.Chat.unreadCount
    override val chatIsListening: Boolean get() = Native.Chat.isListening

    override fun addChatListener(listener: PushizoChatListener): PushizoListenerRegistration {
        val native = AndroidChatListener(listener)
        Native.Chat.addListener(native)
        return PushizoListenerRegistration { Native.Chat.removeListener(native) }
    }

    override fun addChatOpenListener(listener: (PushizoChatPushInfo) -> Unit): PushizoListenerRegistration {
        val native = HsChatOpenListener { listener(it.toCommon()) }
        Native.Chat.addOpenListener(native)
        return PushizoListenerRegistration { Native.Chat.removeOpenListener(native) }
    }

    override suspend fun chatStart(name: String?, email: String?, language: String?): PushizoChatIdentifyResult =
        Native.Chat.start(name, email, language).toCommon()

    override suspend fun chatIdentify(name: String?, email: String?, language: String?): PushizoChatIdentifyResult =
        Native.Chat.identify(name, email, language).toCommon()

    override suspend fun chatGetConfig(language: String?): PushizoChatConfig = Native.Chat.getConfig(language).toCommon()
    override suspend fun chatGetConversation(): PushizoChatConversationState = Native.Chat.getConversation().toCommon()
    override suspend fun chatGetMessages(afterSeq: Long?, beforeSeq: Long?, limit: Int): PushizoChatMessagePage =
        Native.Chat.getMessages(afterSeq, beforeSeq, limit).toCommon()

    override suspend fun chatSendMessage(text: String, optionId: String?): PushizoChatMessage = Native.Chat.sendMessage(text, optionId).toCommon()
    override suspend fun chatSendAttachment(bytes: ByteArray, fileName: String, mimeType: String, text: String?): PushizoChatMessage =
        Native.Chat.sendAttachment(bytes, fileName, mimeType, text).toCommon()

    override suspend fun chatMarkRead(seq: Long): Int = Native.Chat.markRead(seq)
    override suspend fun chatRefreshUnreadCount(): Int = Native.Chat.refreshUnreadCount()
    override suspend fun chatLeaveOfflineMessage(email: String, text: String, name: String?): PushizoChatMessage =
        Native.Chat.leaveOfflineMessage(email, text, name).toCommon()

    override fun chatSetTyping(typing: Boolean) = Native.Chat.setTyping(typing)
    override fun chatStartListening() = Native.Chat.startListening()
    override fun chatStopListening() = Native.Chat.stopListening()
    override fun chatAttachmentUrl(attachmentId: String, download: Boolean): String? = Native.Chat.attachmentUrl(attachmentId, download)

    override suspend fun refreshFeatureFlags(): Boolean = suspendCoroutine { cont -> Native.FeatureFlags.refresh { cont.resume(it) } }
    override fun isFeatureEnabled(key: String, default: Boolean): Boolean = Native.FeatureFlags.isEnabled(key, default)
    override fun featureFlagValue(key: String): Any? = Native.FeatureFlags.getValue(key).toCommonJson()
}
