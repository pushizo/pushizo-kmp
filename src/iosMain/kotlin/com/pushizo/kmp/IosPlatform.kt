@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pushizo.kmp

import com.pushizo.kmp.ios.PZAds
import com.pushizo.kmp.ios.PZChat
import com.pushizo.kmp.ios.PZChatAttachment
import com.pushizo.kmp.ios.PZChatConfig
import com.pushizo.kmp.ios.PZChatConversation
import com.pushizo.kmp.ios.PZChatConversationState
import com.pushizo.kmp.ios.PZChatEvent
import com.pushizo.kmp.ios.PZChatIdentifyResult
import com.pushizo.kmp.ios.PZChatMessage
import com.pushizo.kmp.ios.PZChatMessagePage
import com.pushizo.kmp.ios.PZChatPushInfo
import com.pushizo.kmp.ios.PZChatQuickReply
import com.pushizo.kmp.ios.PZClickEvent
import com.pushizo.kmp.ios.PZFeatureFlags
import com.pushizo.kmp.ios.PZForegroundEvent
import com.pushizo.kmp.ios.PZInAppBlock
import com.pushizo.kmp.ios.PZInAppButton
import com.pushizo.kmp.ios.PZInAppCarouselCard
import com.pushizo.kmp.ios.PZInAppClickEvent
import com.pushizo.kmp.ios.PZInAppLifecycleListenerProtocol
import com.pushizo.kmp.ios.PZInAppMessage
import com.pushizo.kmp.ios.PZInAppMessages
import com.pushizo.kmp.ios.PZContentCard
import com.pushizo.kmp.ios.PZContentCardFeed
import com.pushizo.kmp.ios.PZContentCards
import com.pushizo.kmp.ios.PZInbox
import com.pushizo.kmp.ios.PZInboxChangeEvent
import com.pushizo.kmp.ios.PZInboxItem
import com.pushizo.kmp.ios.PZInboxPage
import com.pushizo.kmp.ios.PZLiveActivities
import com.pushizo.kmp.ios.PZLocation
import com.pushizo.kmp.ios.PZLogLevelDebug
import com.pushizo.kmp.ios.PZLogLevelError
import com.pushizo.kmp.ios.PZLogLevelInfo
import com.pushizo.kmp.ios.PZLogLevelNone
import com.pushizo.kmp.ios.PZLogLevelWarn
import com.pushizo.kmp.ios.PZMessageCategory
import com.pushizo.kmp.ios.PZNotification
import com.pushizo.kmp.ios.PZNotificationAction
import com.pushizo.kmp.ios.PZNotifications
import com.pushizo.kmp.ios.PZOptions
import com.pushizo.kmp.ios.PZPlacements
import com.pushizo.kmp.ios.PZPreference
import com.pushizo.kmp.ios.PZPreferenceInput
import com.pushizo.kmp.ios.PZPreferencesResult
import com.pushizo.kmp.ios.PZPushSubscription
import com.pushizo.kmp.ios.PZPushSubscriptionState
import com.pushizo.kmp.ios.PZPushizo
import com.pushizo.kmp.ios.PZSession
import com.pushizo.kmp.ios.PZUser
import com.pushizo.kmp.ios.PZUserConsent
import com.pushizo.kmp.ios.PZUserProfile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.concurrent.AtomicReference
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * iOS'ta başlatma bağlam nesnesi gerektirmez; ortak `initialize(context, …)` imzası için [PushizoApplicationContext]
 * verilir (`Pushizo.initialize(PushizoApplicationContext, "app_…")`).
 */
actual abstract class PushizoContext internal constructor()

/** iOS başlatma bağlamı: uygulamanın kendisi (`UIApplication.sharedApplication`). */
object PushizoApplicationContext : PushizoContext()

/** iOS izin istemleri sistem penceresi açar; ana bilgisayar nesnesi kullanılmaz ama tür ortak API'de korunur. */
actual typealias PushizoPermissionHost = UIViewController

internal actual fun createPushizoPlatform(): PushizoPlatform = IosPushizoPlatform

/** Objective-C cephesinin hata alanı (`PZErrors.domain`). */
internal const val PZ_ERROR_DOMAIN = "PZErrorDomain"

/**
 * Cephe hatasını Android köprüsüyle aynı istisna türlerine çevirir: başlatma/abonelik/kullanıcı eksikliği
 * `IllegalStateException`, geçersiz girdi `IllegalArgumentException`, platformun desteklemediği özellik
 * `UnsupportedOperationException`, diğerleri `RuntimeException`.
 */
internal fun NSError.toKotlinException(): Throwable {
    val message = localizedDescription
    if (domain != PZ_ERROR_DOMAIN) return RuntimeException(message)
    return when (code) {
        1L, 2L, 3L -> IllegalStateException(message)
        4L -> IllegalArgumentException(message)
        6L -> UnsupportedOperationException(message)
        else -> RuntimeException(message)
    }
}

internal fun NSDate?.toEpochMillis(): Long? = this?.let { (it.timeIntervalSince1970 * 1000).toLong() }

internal fun Map<Any?, *>.toStringMap(): Map<String, String> =
    entries.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap()

internal fun List<*>.toStringList(): List<String> = filterIsInstance<String>()

/** Objective-C sözlük parametresi (`NSDictionary`) için anahtar türü genişletilmiş kopya. */
internal fun Map<String, *>.toObjC(): Map<Any?, *> = HashMap<Any?, Any?>(this)

/**
 * `User.setProperty` değerini Foundation'a uygun biçime çevirir: iç içe harita/liste/dizi özyinelemeli, koleksiyon içindeki
 * null `NSNull` olur (`NSDictionary`/`NSArray` nil tutamaz); metin, sayı ve mantıksal aynen geçer, diğer türler metne
 * çevrilir (Android köprüsüyle aynı).
 */
internal fun Any?.toObjCPropertyValue(nested: Boolean = false): Any? = when (this) {
    null -> if (nested) NSNull.`null`() else null
    is String, is Boolean, is Number, is NSNumber, is NSNull -> this
    is Map<*, *> -> HashMap<Any?, Any?>().also { out -> forEach { (k, v) -> out[k.toString()] = v.toObjCPropertyValue(nested = true) } }
    is Iterable<*> -> map { it.toObjCPropertyValue(nested = true) }
    is Array<*> -> map { it.toObjCPropertyValue(nested = true) }
    else -> toString()
}

internal fun PushizoUserProfile.toNative() = PZUserProfile().also {
    it.setEmail(email)
    it.setSms(sms)
    it.setTags(tags.toObjC())
    it.setAliases(aliases.toObjC())
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/**
 * Bayrak değerindeki Foundation türleri ortak koleksiyonlara çevrilir: `NSNull` null, mantıksal `NSNumber` Boolean,
 * tam sayı Int (sığmazsa Long), ondalık Double; sözlük ve dizi özyinelemeli. Android tarafındaki JSON eşlemesiyle aynıdır.
 */
internal fun Any?.toCommonFlagValue(): Any? = when (this) {
    null, is NSNull -> null
    is NSNumber -> when (objCType?.toKString()) {
        "c", "B" -> boolValue
        "f", "d" -> doubleValue
        else -> longValue.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
    }
    is Map<*, *> -> entries.associate { (k, v) -> k.toString() to v.toCommonFlagValue() }
    is List<*> -> map { it.toCommonFlagValue() }
    else -> this
}

internal fun isPermissionGranted(status: UNAuthorizationStatus): Boolean =
    status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional || status == UNAuthorizationStatusEphemeral

internal fun PushizoLogLevel.toNative(): Long = when (this) {
    PushizoLogLevel.NONE -> PZLogLevelNone
    PushizoLogLevel.ERROR -> PZLogLevelError
    PushizoLogLevel.WARN -> PZLogLevelWarn
    PushizoLogLevel.INFO -> PZLogLevelInfo
    PushizoLogLevel.DEBUG -> PZLogLevelDebug
}

internal fun PZNotification.toCommon() = PushizoNotification(
    title = title(),
    subtitle = null,
    body = body(),
    imageUrl = imageUrl(),
    url = url(),
    deliveryId = deliveryId(),
    messageId = messageId(),
    actions = actions().filterIsInstance<PZNotificationAction>().map { PushizoNotificationAction(it.actionId(), it.title(), it.url()) },
    data = data().toStringMap(),
).also { it.identifier = identifier() }

internal fun PZInAppButton.toCommon() = PushizoInAppButton(
    id = buttonId(),
    text = text(),
    actionType = actionType(),
    actionValue = actionValue(),
    actionTags = actionTags().toStringMap(),
    backgroundColor = backgroundColor(),
    textColor = textColor(),
)

internal fun PZInAppBlock.toCommon() = PushizoInAppBlock(
    kind = kind(),
    text = text(),
    url = url(),
    alt = alt(),
    button = button()?.toCommon(),
    cards = cards().filterIsInstance<PZInAppCarouselCard>().map {
        PushizoInAppCarouselCard(it.cardId(), it.title(), it.body(), it.imageUrl(), it.imageAlt(), it.button()?.toCommon())
    },
)

internal fun PZInAppMessage.toCommon(): PushizoInAppMessage = PushizoInAppMessage(
    id = messageId(),
    variantId = variantId(),
    layout = layout(),
    contentType = contentType(),
    html = html(),
    backgroundColor = backgroundColor(),
    textColor = textColor(),
    dismissible = dismissible(),
    blocks = blocks().filterIsInstance<PZInAppBlock>().map { it.toCommon() },
).also { it.native = this }

/** Raporlama yalnızca `Placements.list` sonucundan gelen (cephe nesnesi olan) mesajla yapılır; diğerleri yok sayılır. */
internal fun PushizoInAppMessage.nativeIos(): PZInAppMessage? = native as? PZInAppMessage

internal fun PZChatMessage.toCommon() = PushizoChatMessage(
    id = messageId(),
    seq = seq(),
    clientMessageId = clientMessageId(),
    sender = sender(),
    authorName = authorName(),
    text = text(),
    attachments = attachments().filterIsInstance<PZChatAttachment>().map { PushizoChatAttachment(it.attachmentId(), it.name(), it.type(), it.size()) },
    // Cephe seçenek yokken boş dizi verir; ortak modelde (Android ile aynı) null'dır.
    quickReplies = quickReplies().filterIsInstance<PZChatQuickReply>().map { PushizoChatQuickReply(it.replyId(), it.label()) }.takeIf { it.isNotEmpty() },
    status = status(),
    createdAt = createdAt().toEpochMillis(),
)

internal fun PZChatConversation.toCommon() = PushizoChatConversation(
    id = conversationId(),
    status = status(),
    lastSeq = lastSeq(),
    agentLastReadSeq = agentLastReadSeq(),
    visitorLastReadSeq = visitorLastReadSeq(),
    unreadCount = unreadCount().toInt(),
    botActive = botActive(),
    assigneeName = assigneeName(),
    createdAt = createdAt().toEpochMillis(),
)

internal fun PZChatConversationState.toCommon() = PushizoChatConversationState(conversation()?.toCommon(), unreadCount().toInt())

internal fun PZChatMessagePage.toCommon() =
    PushizoChatMessagePage(messages().filterIsInstance<PZChatMessage>().map { it.toCommon() }, hasMore())

internal fun PZChatIdentifyResult.toCommon() = PushizoChatIdentifyResult(userId(), conversation()?.toCommon())

internal fun PZChatConfig.toCommon() = PushizoChatConfig(
    enabled = enabled(),
    online = online(),
    withinBusinessHours = withinBusinessHours(),
    color = color(),
    textColor = textColor(),
    position = position(),
    theme = theme(),
    title = title(),
    greeting = greeting(),
    offlineMessage = offlineMessage(),
    collectEmailOffline = collectEmailOffline(),
    requireEmail = requireEmail(),
    attachmentsEnabled = attachmentsEnabled(),
    attachmentMaxBytes = attachmentMaxBytes(),
    attachmentTypes = attachmentTypes().toStringList(),
    language = language(),
)

internal fun PZChatPushInfo.toCommon() = PushizoChatPushInfo(conversationId(), seq())

/**
 * Cephenin tek tip sohbet olayını (`PZChatEvent.type`) ortak dinleyicinin ilgili metoduna aktarır; bilinmeyen tür yok
 * sayılır. Alanlar ayrı verilir ki eşleme cephe nesnesi olmadan da sınanabilsin.
 */
internal fun PushizoChatListener.dispatchChatEvent(
    type: String,
    message: PushizoChatMessage? = null,
    typing: Boolean = false,
    name: String? = null,
    seq: Long = 0,
    unreadCount: Int = 0,
    conversationId: String? = null,
    status: String? = null,
    botActive: Boolean = false,
    connected: Boolean = false,
    code: String? = null,
    errorMessage: String? = null,
) {
    when (type) {
        "message" -> message?.let(::onMessage)
        "typing" -> onTyping(typing, name)
        "read" -> onAgentRead(seq)
        "unread" -> onUnreadCountChanged(unreadCount)
        "conversation" -> onConversationUpdated(conversationId.orEmpty(), status.orEmpty(), botActive)
        "connection" -> onConnectionChanged(connected)
        "error" -> onError(code.orEmpty(), errorMessage)
    }
}

internal fun PushizoChatListener.dispatch(event: PZChatEvent) = dispatchChatEvent(
    type = event.type(),
    message = event.message()?.toCommon(),
    typing = event.typing(),
    name = event.name(),
    seq = event.seq(),
    unreadCount = event.unreadCount().toInt(),
    conversationId = event.conversationId(),
    status = event.status(),
    botActive = event.botActive(),
    connected = event.connected(),
    code = event.code(),
    errorMessage = event.errorMessage(),
)

internal fun PZInboxChangeEvent.toCommon() = PushizoInboxChangeEvent(type(), itemId(), unreadCount().toInt())

/**
 * Ortak yaşam döngüsü dinleyicisini Objective-C protokolüne uyarlar. Cephe dinleyiciyi zayıf tuttuğundan köprü bu nesneyi
 * kayıt kaldırılana kadar güçlü tutar ([IosPushizoPlatform.addInAppLifecycleListener]).
 */
internal class IosInAppLifecycleAdapter(private val listener: PushizoInAppLifecycleListener) : NSObject(), PZInAppLifecycleListenerProtocol {
    private fun PZInAppMessage.event() = PushizoInAppLifecycleEvent(toCommon())
    override fun onWillDisplay(message: PZInAppMessage) = listener.onWillDisplay(message.event())
    override fun onDidDisplay(message: PZInAppMessage) = listener.onDidDisplay(message.event())
    override fun onWillDismiss(message: PZInAppMessage) = listener.onWillDismiss(message.event())
    override fun onDidDismiss(message: PZInAppMessage) = listener.onDidDismiss(message.event())
}

internal fun PZInboxItem.toCommon() = PushizoInboxItem(
    id = itemId(),
    messageId = messageId(),
    title = title(),
    body = body(),
    url = url(),
    imageUrl = imageUrl(),
    data = data().toStringMap(),
    read = read(),
    readAt = readAt().toEpochMillis(),
    archivedAt = archivedAt().toEpochMillis(),
    expiresAt = expiresAt().toEpochMillis(),
    createdAt = createdAt().toEpochMillis(),
)

internal fun PZContentCard.toCommon() = PushizoContentCard(
    id = cardId(),
    messageId = messageId(),
    type = PushizoContentCardType.from(type()),
    title = title(),
    body = body(),
    url = url(),
    imageUrl = imageUrl(),
    buttonText = buttonText(),
    data = data().toStringMap(),
    pinned = pinned(),
    priority = priority().toInt(),
    viewed = viewed(),
    clicked = clicked(),
    expiresAt = expiresAt().toEpochMillis(),
    createdAt = createdAt().toEpochMillis(),
)

internal fun PZContentCardFeed.toCommon() =
    PushizoContentCardFeed(cards().filterIsInstance<PZContentCard>().map { it.toCommon() }, unviewedCount().toInt())

internal fun PZInboxPage.toCommon() =
    PushizoInboxPage(items().filterIsInstance<PZInboxItem>().map { it.toCommon() }, unreadCount().toInt(), nextCursor(), hasMore())

internal fun PZPreferencesResult.toCommon() = PushizoPreferencesResult(
    preferences = preferences().filterIsInstance<PZPreference>().map {
        PushizoPreference(it.category(), it.channel(), it.optedIn(), it.explicit(), it.source(), it.updatedAt().toEpochMillis())
    },
    categories = categories().filterIsInstance<PZMessageCategory>().map {
        PushizoMessageCategory(it.key(), it.name().toStringMap(), it.categoryDescription().toStringMap(), it.channels()?.toStringList(), it.defaultOptedIn())
    },
    consents = consents().filterIsInstance<PZUserConsent>().map {
        PushizoConsent(it.purpose(), it.granted(), it.source(), it.updatedAt().toEpochMillis())
    },
)

/** Completion block'lu cephe çağrısını askıya alan fonksiyona çevirir; hata Kotlin istisnası olarak fırlatılır. */
private suspend fun <T : Any> awaitResult(call: ((T?, NSError?) -> Unit) -> Unit): T = suspendCoroutine { cont ->
    call { value, error ->
        when {
            error != null -> cont.resumeWithException(error.toKotlinException())
            value != null -> cont.resume(value)
            else -> cont.resumeWithException(IllegalStateException("Pushizo: boş yanıt"))
        }
    }
}

private class IosForegroundEvent(private val event: PZForegroundEvent) : PushizoForegroundEvent {
    override val notification: PushizoNotification = event.notification().toCommon()
    override fun preventDefault() = event.preventDefault()
    override fun display() = event.display()
}

internal object IosPushizoPlatform : PushizoPlatform {
    /**
     * iOS'ta bildirim izni yalnızca eşzamansız okunur; ortak API'nin eşzamanlı `permission` değeri için son bilinen
     * durum izin ve abonelik gözlemcileriyle güncel tutulur (başlatmada bir kez okunur).
     */
    private var cachedPermission = false
    private var cacheObserversRegistered = false

    /** Cephenin zayıf tuttuğu yaşam döngüsü uyarlayıcıları; kayıt kaldırılınca bırakılır. */
    private val lifecycleAdapters = AtomicReference<Set<IosInAppLifecycleAdapter>>(emptySet())

    internal val retainedLifecycleListenerCount: Int get() = lifecycleAdapters.value.size

    private inline fun updateLifecycleAdapters(change: (Set<IosInAppLifecycleAdapter>) -> Set<IosInAppLifecycleAdapter>) {
        while (true) {
            val current = lifecycleAdapters.value
            if (lifecycleAdapters.compareAndSet(current, change(current))) return
        }
    }

    /** `NSError**` alan cephe çağrısını çalıştırır; NO dönerse hata Kotlin istisnası olarak fırlatılır. */
    private inline fun throwing(fallback: String, call: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> Boolean) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!call(error.ptr)) throw error.value?.toKotlinException() ?: IllegalStateException(fallback)
        }
    }

    override val isInitialized: Boolean get() = PZPushizo.isInitialized()

    override fun initialize(context: PushizoContext, appId: String, options: PushizoOptions) {
        val native = PZOptions().apply {
            options.apiUrl?.takeIf { it.isNotBlank() }?.let { setApiUrl(it) }
            setRequiresPrivacyConsent(options.requiresPrivacyConsent)
            setLogLevel(options.logLevel.toNative())
        }
        PZPushizo.initializeWithAppId(appId, launchOptions = null, options = native)
        if (!cacheObserversRegistered) {
            cacheObserversRegistered = true
            PZNotifications.addPermissionObserver { granted -> cachedPermission = granted }
            PZPushSubscription.addObserver { state -> state?.let { cachedPermission = isPermissionGranted(it.permission()) } }
        }
        PZNotifications.isPermissionGrantedWithCompletion { granted -> cachedPermission = granted }
    }

    override fun setConsentGiven(given: Boolean) = PZPushizo.setConsentGiven(given)
    override fun login(externalId: String, identityHash: String?, jwt: String?) =
        PZPushizo.loginWithExternalId(externalId, identityHash = identityHash, jwt = jwt)

    /** Yerel profilli giriş: profil ya da kimlik kanıtı geçersizse giriş yapılmaz ve hata fırlatılır (Android ile aynı). */
    override fun login(externalId: String, profile: PushizoUserProfile, identityHash: String?, jwt: String?) =
        throwing("Giriş yapılamadı") {
            PZPushizo.loginWithExternalId(externalId, profile = profile.toNative(), identityHash = identityHash, jwt = jwt, error = it)
        }

    override fun updateUserJwt(jwt: String) = throwing("JWT güncellenemedi") { PZPushizo.updateUserJwt(jwt, it) }

    override fun logout() = PZPushizo.logout()
    override fun setLogLevel(level: PushizoLogLevel) = PZPushizo.setLogLevel(level.toNative())

    override fun addTags(tags: Map<String, String>) = PZUser.addTags(tags.toObjC())
    override fun removeTags(keys: List<String>) = PZUser.removeTags(keys)
    override fun getTags(): Map<String, String> = PZUser.getTags().toStringMap()
    override fun addAliases(aliases: Map<String, String>) = PZUser.addAliases(aliases.toObjC())
    override fun removeAlias(label: String) = PZUser.removeAlias(label)
    override fun setLanguage(code: String) = PZUser.setLanguage(code)
    override fun setProperty(key: String, value: Any?) = PZUser.setProperty(key, value = value.toObjCPropertyValue())
    override fun addEmail(email: String) = PZUser.addEmail(email)
    override fun removeEmail(email: String) = PZUser.removeEmail(email)
    override fun addSms(phoneNumber: String) = PZUser.addSms(phoneNumber)
    override fun removeSms(phoneNumber: String) = PZUser.removeSms(phoneNumber)
    override val emails: List<String> get() = PZUser.emails().toStringList()
    override val smsNumbers: List<String> get() = PZUser.smsNumbers().toStringList()
    override val externalId: String? get() = PZUser.externalId()
    override val userId: String? get() = PZUser.userId()

    /** ADM yalnızca Fire OS'ta vardır. */
    override fun registerAdm(token: String) = Unit

    override val subscriptionState: PushizoSubscriptionState
        get() = PushizoSubscriptionState(PZPushSubscription.subscriptionId(), PZPushSubscription.token(), PZPushSubscription.optedIn(), cachedPermission)

    override fun optIn() = PZPushSubscription.optIn()
    override fun optOut() = PZPushSubscription.optOut()

    override fun addSubscriptionObserver(observer: (PushizoSubscriptionState) -> Unit): PushizoListenerRegistration {
        val token = PZPushSubscription.addObserver { state: PZPushSubscriptionState? ->
            state?.let { observer(PushizoSubscriptionState(it.subscriptionId(), it.token(), it.optedIn(), isPermissionGranted(it.permission()))) }
        }
        return PushizoListenerRegistration { token.remove() }
    }

    override val permission: Boolean get() = cachedPermission

    override fun requestPermission(host: PushizoPermissionHost) =
        PZNotifications.requestPermissionWithCompletion { granted -> cachedPermission = granted }

    /** iOS'ta izin sonucu `requestPermission` içinde gelir; Android'deki Activity geri çağrısının karşılığı yoktur. */
    override fun onPermissionResult() = Unit

    override fun addPermissionObserver(observer: (Boolean) -> Unit): PushizoListenerRegistration {
        val token = PZNotifications.addPermissionObserver(observer)
        return PushizoListenerRegistration { token.remove() }
    }

    override fun addClickListener(listener: (PushizoClickEvent) -> Unit): PushizoListenerRegistration {
        val token = PZNotifications.addClickListener { event: PZClickEvent? ->
            event?.let { listener(PushizoClickEvent(it.notification().toCommon(), it.actionId(), it.url())) }
        }
        return PushizoListenerRegistration { token.remove() }
    }

    override fun addForegroundLifecycleListener(listener: (PushizoForegroundEvent) -> Unit): PushizoListenerRegistration {
        val token = PZNotifications.addForegroundLifecycleListener { event: PZForegroundEvent? -> event?.let { listener(IosForegroundEvent(it)) } }
        return PushizoListenerRegistration { token.remove() }
    }

    override fun clearAllNotifications() = PZNotifications.clearAll()

    /** Sayısal tepsi kimliği Android'e özgüdür. */
    override fun removeNotification(androidNotificationId: Int) = Unit
    override fun removeNotification(identifier: String) = PZNotifications.removeNotification(identifier)
    override fun removeGroupedNotifications(group: String) = PZNotifications.removeGroupedNotifications(group)
    /** iOS'ta rozet her zaman ayarlanır (NSE ile paylaşılan sayaç dahil). */
    override fun setBadgeCount(count: Int): Boolean {
        PZNotifications.setBadgeCount(count.toLong())
        return true
    }

    /** Yerel SDK'da rozet okuma yok: uygulama simgesindeki değer UIKit'ten okunur. */
    override val badgeCount: Int get() = UIApplication.sharedApplication.applicationIconBadgeNumber.toInt()

    override fun setAdvertisingId(id: String?) = PZAds.setAdvertisingId(id)

    override fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?, eventId: String?) =
        PZSession.sendEvent(name, properties = properties.toObjC(), value = value?.let { NSNumber(double = it) }, eventId = eventId)

    override fun addTriggers(triggers: Map<String, String>) = PZInAppMessages.addTriggers(triggers.toObjC())
    override fun removeTriggers(keys: Collection<String>) = PZInAppMessages.removeTriggers(keys.toList())
    override fun clearTriggers() = PZInAppMessages.clearTriggers()
    override fun getTriggers(): Map<String, String> = PZInAppMessages.getTriggers().toStringMap()
    override fun setScreen(name: String?) = PZInAppMessages.setScreen(name)
    override var inAppPaused: Boolean
        get() = PZInAppMessages.paused()
        set(value) = PZInAppMessages.setPaused(value)

    override fun addInAppClickListener(listener: (PushizoInAppClickEvent) -> Unit): PushizoListenerRegistration {
        val token = PZInAppMessages.addClickListener { event: PZInAppClickEvent? ->
            event?.let { listener(PushizoInAppClickEvent(it.messageId(), it.actionId(), it.url())) }
        }
        return PushizoListenerRegistration { token.remove() }
    }

    /** Cephe dinleyiciyi zayıf tutar: uyarlayıcı kayıt kaldırılana kadar burada güçlü tutulur. */
    override fun addInAppLifecycleListener(listener: PushizoInAppLifecycleListener): PushizoListenerRegistration {
        val adapter = IosInAppLifecycleAdapter(listener)
        updateLifecycleAdapters { it + adapter }
        PZInAppMessages.addLifecycleListener(adapter)
        return PushizoListenerRegistration {
            PZInAppMessages.removeLifecycleListener(adapter)
            updateLifecycleAdapters { it - adapter }
        }
    }

    override suspend fun placementsList(key: String, limit: Int): List<PushizoInAppMessage> =
        awaitResult<List<*>> { done -> PZPlacements.listWithKey(key, limit = limit.toLong(), completion = done) }
            .filterIsInstance<PZInAppMessage>().map { it.toCommon() }

    override fun placementReportImpression(message: PushizoInAppMessage) {
        message.nativeIos()?.let { PZPlacements.reportImpression(it) }
    }

    override fun placementReportClick(message: PushizoInAppMessage, buttonId: String) {
        message.nativeIos()?.let { PZPlacements.reportClick(it, buttonId = buttonId) }
    }

    override fun placementReportDismiss(message: PushizoInAppMessage) {
        message.nativeIos()?.let { PZPlacements.reportDismiss(it) }
    }

    override val locationShared: Boolean get() = PZLocation.isShared()

    override val locationPermissionGranted: Boolean get() = PZLocation.permissionGranted()

    override fun setLocationShared(shared: Boolean) = PZLocation.setShared(shared)

    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        PZLocation.requestPermissionWithRationale(rationale) { }

    override fun onLocationPermissionResult() = Unit
    override val geofencingEnabled: Boolean get() = PZLocation.isGeofencingEnabled()
    override fun setGeofencingEnabled(enabled: Boolean) = PZLocation.setGeofencingEnabled(enabled)

    override val locationBackgroundPermissionGranted: Boolean get() = PZLocation.backgroundPermissionGranted()

    /** iOS'ta arka plan izni "her zaman" iznidir; açıklama metni sistem isteminde Info.plist'ten gelir. */
    override fun requestBackgroundLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        PZLocation.requestAlwaysPermissionWithCompletion { }

    // Android teslimat güçlendirme (ADR-0092) iOS'ta yoktur: durum "desteklenmiyor / risk yok", diğerleri etkisiz false.
    override fun deliveryStatus(): PushizoDeliveryStatus = PushizoDeliveryStatus.UNSUPPORTED
    override fun shouldShowDeliveryGuidance(minIntervalDays: Int): Boolean = false
    override fun openDeliverySettings(context: PushizoContext, setting: PushizoDeliverySetting): Boolean = false
    override fun showDeliveryGuidance(host: PushizoPermissionHost, setting: PushizoDeliverySetting, message: String?, onResult: ((Boolean) -> Unit)?) {
        onResult?.invoke(false)
    }

    override suspend fun inboxList(limit: Int, cursor: String?): PushizoInboxPage =
        awaitResult<PZInboxPage> { done -> PZInbox.listWithLimit(limit.toLong(), cursor, done) }.toCommon()

    override suspend fun inboxMarkRead(id: String): PushizoInboxItem =
        awaitResult<PZInboxItem> { done -> PZInbox.markRead(id, done) }.toCommon()

    override suspend fun inboxMarkAllRead(): Int = awaitResult<NSNumber> { done -> PZInbox.markAllReadWithCompletion(done) }.intValue

    override suspend fun inboxArchive(id: String) = suspendCoroutine { cont ->
        PZInbox.archive(id) { error -> if (error != null) cont.resumeWithException(error.toKotlinException()) else cont.resume(Unit) }
    }

    override suspend fun inboxUnreadCount(): Int = awaitResult<NSNumber> { done -> PZInbox.unreadCountWithCompletion(done) }.intValue

    override fun addInboxObserver(observer: (PushizoInboxChangeEvent) -> Unit): PushizoListenerRegistration {
        val token = PZInbox.addObserver { event: PZInboxChangeEvent? -> event?.let { observer(it.toCommon()) } }
        return PushizoListenerRegistration { token.remove() }
    }

    override suspend fun contentCardsList(limit: Int): PushizoContentCardFeed =
        awaitResult<PZContentCardFeed> { done -> PZContentCards.listWithLimit(limit.toLong(), done) }.toCommon()

    override suspend fun contentCardImpression(id: String): PushizoContentCard =
        awaitResult<PZContentCard> { done -> PZContentCards.logImpression(id, done) }.toCommon()

    override suspend fun contentCardClick(id: String): PushizoContentCard =
        awaitResult<PZContentCard> { done -> PZContentCards.logClick(id, done) }.toCommon()

    override suspend fun contentCardDismiss(id: String): PushizoContentCard =
        awaitResult<PZContentCard> { done -> PZContentCards.dismiss(id, done) }.toCommon()

    override suspend fun getPreferences(): PushizoPreferencesResult =
        awaitResult<PZPreferencesResult> { done -> PZUser.getPreferencesWithCompletion(done) }.toCommon()

    override suspend fun setPreferences(preferences: List<PushizoPreferenceInput>): PushizoPreferencesResult {
        val input = preferences.map { PZPreferenceInput(category = it.category, channel = it.channel, optedIn = it.optedIn) }
        return awaitResult<PZPreferencesResult> { done -> PZUser.setPreferences(input, done) }.toCommon()
    }

    // Canlı sohbet (ADR-0040): Objective-C cephesindeki `PZChat`. Completion block'ları ve dinleyiciler ana iş parçacığında gelir.
    override val chatSupported: Boolean get() = true
    override val chatVisitorId: String? get() = PZChat.visitorId()
    override val chatUnreadCount: Int get() = PZChat.unreadCount().toInt()
    override val chatIsListening: Boolean get() = PZChat.isListening()

    override fun addChatListener(listener: PushizoChatListener): PushizoListenerRegistration {
        val token = PZChat.addListener { event: PZChatEvent? -> event?.let(listener::dispatch) }
        return PushizoListenerRegistration { token.remove() }
    }

    override fun addChatOpenListener(listener: (PushizoChatPushInfo) -> Unit): PushizoListenerRegistration {
        val token = PZChat.addOpenListener { info: PZChatPushInfo? -> info?.let { listener(it.toCommon()) } }
        return PushizoListenerRegistration { token.remove() }
    }

    override suspend fun chatStart(name: String?, email: String?, language: String?): PushizoChatIdentifyResult =
        awaitResult<PZChatIdentifyResult> { done -> PZChat.startWithName(name, email = email, language = language, completion = done) }.toCommon()

    override suspend fun chatIdentify(name: String?, email: String?, language: String?): PushizoChatIdentifyResult =
        awaitResult<PZChatIdentifyResult> { done -> PZChat.identifyWithName(name, email = email, language = language, completion = done) }.toCommon()

    override suspend fun chatGetConfig(language: String?): PushizoChatConfig =
        awaitResult<PZChatConfig> { done -> PZChat.getConfigWithLanguage(language, completion = done) }.toCommon()

    override suspend fun chatGetConversation(): PushizoChatConversationState =
        awaitResult<PZChatConversationState> { done -> PZChat.getConversationWithCompletion(done) }.toCommon()

    override suspend fun chatGetMessages(afterSeq: Long?, beforeSeq: Long?, limit: Int): PushizoChatMessagePage =
        awaitResult<PZChatMessagePage> { done ->
            PZChat.getMessagesAfterSeq(
                afterSeq?.let { NSNumber(longLong = it) },
                beforeSeq = beforeSeq?.let { NSNumber(longLong = it) },
                limit = limit.toLong(),
                completion = done,
            )
        }.toCommon()

    override suspend fun chatSendMessage(text: String, optionId: String?): PushizoChatMessage =
        awaitResult<PZChatMessage> { done -> PZChat.sendMessage(text, optionId = optionId, completion = done) }.toCommon()

    override suspend fun chatSendAttachment(bytes: ByteArray, fileName: String, mimeType: String, text: String?): PushizoChatMessage =
        awaitResult<PZChatMessage> { done ->
            PZChat.sendAttachmentData(bytes.toNSData(), fileName = fileName, mimeType = mimeType, text = text, completion = done)
        }.toCommon()

    override suspend fun chatMarkRead(seq: Long): Int = awaitResult<NSNumber> { done -> PZChat.markRead(seq, completion = done) }.intValue
    override suspend fun chatRefreshUnreadCount(): Int = awaitResult<NSNumber> { done -> PZChat.refreshUnreadCountWithCompletion(done) }.intValue

    override suspend fun chatLeaveOfflineMessage(email: String, text: String, name: String?): PushizoChatMessage =
        awaitResult<PZChatMessage> { done -> PZChat.leaveOfflineMessageWithEmail(email, text = text, name = name, completion = done) }.toCommon()

    override fun chatSetTyping(typing: Boolean) = PZChat.setTyping(typing)
    override fun chatStartListening() = PZChat.startListening()
    override fun chatStopListening() = PZChat.stopListening()

    /** SDK başlatılmadıysa cephe hata döndürür; Android'deki gibi `IllegalStateException` fırlatılır. */
    override fun chatAttachmentUrl(attachmentId: String, download: Boolean): String? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val url = PZChat.attachmentUrl(attachmentId, download = download, error = error.ptr)
        error.value?.let { throw it.toKotlinException() }
        url?.absoluteString
    }

    /** [host] verilmezse etkin sahnenin en üstteki görünüm denetleyicisi kullanılır ([context] iOS'ta taşıyıcı değildir). */
    override fun chatShow(context: PushizoContext?, host: PushizoPermissionHost?, animated: Boolean) =
        PZChat.showFromViewController(host, animated = animated)

    override fun chatHide(animated: Boolean): Boolean {
        val presented = PZChat.isPresented()
        PZChat.hideAnimated(animated)
        return presented
    }

    override val chatPresented: Boolean get() = PZChat.isPresented()

    // Live Activities: genel tür (`PushizoDefaultLiveActivityAttributes`). ActivityKit genelleri Swift'ten kullanılır.
    override val liveActivitiesSupported: Boolean get() = true
    override val defaultLiveActivityType: String? get() = PZLiveActivities.defaultActivityType()

    override fun setupDefaultLiveActivity(): Boolean {
        throwing("Live Activity türü kaydedilemedi") { PZLiveActivities.setupDefaultLiveActivityAndReturnError(it) }
        return true
    }

    override fun startDefaultLiveActivity(activityId: String, attributes: Map<String, Any>, contentState: Map<String, Any>): Boolean {
        throwing("Live Activity başlatılamadı") {
            PZLiveActivities.startDefaultLiveActivityWithActivityId(activityId, attributes = attributes.toObjC(), contentState = contentState.toObjC(), error = it)
        }
        return true
    }

    override suspend fun refreshFeatureFlags(): Boolean = suspendCoroutine { cont -> PZFeatureFlags.refreshWithCompletion { cont.resume(it) } }
    override fun isFeatureEnabled(key: String, default: Boolean): Boolean = PZFeatureFlags.isEnabled(key, defaultValue = default)
    override fun featureFlagValue(key: String): Any? = PZFeatureFlags.valueForKey(key, defaultValue = null).toCommonFlagValue()

    override fun addFeatureFlagObserver(observer: () -> Unit): PushizoListenerRegistration {
        val token = PZFeatureFlags.addObserver(observer)
        return PushizoListenerRegistration { token.remove() }
    }
}
