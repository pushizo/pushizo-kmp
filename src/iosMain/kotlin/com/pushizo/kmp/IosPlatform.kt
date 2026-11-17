@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.pushizo.kmp

import com.pushizo.kmp.ios.PZClickEvent
import com.pushizo.kmp.ios.PZFeatureFlags
import com.pushizo.kmp.ios.PZForegroundEvent
import com.pushizo.kmp.ios.PZInAppMessages
import com.pushizo.kmp.ios.PZContentCard
import com.pushizo.kmp.ios.PZContentCardFeed
import com.pushizo.kmp.ios.PZContentCards
import com.pushizo.kmp.ios.PZInbox
import com.pushizo.kmp.ios.PZInboxItem
import com.pushizo.kmp.ios.PZInboxPage
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
import com.pushizo.kmp.ios.PZPreference
import com.pushizo.kmp.ios.PZPreferenceInput
import com.pushizo.kmp.ios.PZPreferencesResult
import com.pushizo.kmp.ios.PZPushSubscription
import com.pushizo.kmp.ios.PZPushSubscriptionState
import com.pushizo.kmp.ios.PZPushizo
import com.pushizo.kmp.ios.PZSession
import com.pushizo.kmp.ios.PZUser
import com.pushizo.kmp.ios.PZUserConsent
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController
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
 * `IllegalStateException`, geçersiz girdi `IllegalArgumentException`, diğerleri `RuntimeException`.
 */
internal fun NSError.toKotlinException(): Throwable {
    val message = localizedDescription
    if (domain != PZ_ERROR_DOMAIN) return RuntimeException(message)
    return when (code) {
        1L, 2L, 3L -> IllegalStateException(message)
        4L -> IllegalArgumentException(message)
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
)

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
    private var lastLocationGrant: Boolean? = null

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

    /** iOS SDK'da tek çağrılık profil girişi yok: giriş yapılır, ardından alanlar ayrı ayrı eklenir (her biri SDK'da doğrulanır). */
    override fun login(externalId: String, profile: PushizoUserProfile, identityHash: String?, jwt: String?) {
        login(externalId, identityHash, jwt)
        profile.email?.let(PZUser::addEmail)
        profile.sms?.let(PZUser::addSms)
        if (profile.tags.isNotEmpty()) PZUser.addTags(profile.tags.toObjC())
        if (profile.aliases.isNotEmpty()) PZUser.addAliases(profile.aliases.toObjC())
    }

    override fun updateUserJwt(jwt: String) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!PZPushizo.updateUserJwt(jwt, error.ptr)) {
                throw error.value?.toKotlinException() ?: IllegalStateException("JWT güncellenemedi")
            }
        }
    }

    override fun logout() = PZPushizo.logout()
    override fun setLogLevel(level: PushizoLogLevel) = PZPushizo.setLogLevel(level.toNative())

    override fun addTags(tags: Map<String, String>) = PZUser.addTags(tags.toObjC())
    override fun removeTags(keys: List<String>) = PZUser.removeTags(keys)
    override fun getTags(): Map<String, String> = PZUser.getTags().toStringMap()
    override fun addAliases(aliases: Map<String, String>) = PZUser.addAliases(aliases.toObjC())
    override fun removeAlias(label: String) = PZUser.removeAlias(label)
    override fun setLanguage(code: String) = PZUser.setLanguage(code)
    override fun addEmail(email: String) = PZUser.addEmail(email)
    override fun removeEmail(email: String) = PZUser.removeEmail(email)
    override fun addSms(phoneNumber: String) = PZUser.addSms(phoneNumber)
    override fun removeSms(phoneNumber: String) = PZUser.removeSms(phoneNumber)
    override val emails: List<String> get() = PZUser.emails().toStringList()
    override val smsNumbers: List<String> get() = PZUser.smsNumbers().toStringList()
    override val externalId: String? get() = PZUser.externalId()
    override val userId: String? get() = PZUser.userId()

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

    override fun sendEvent(name: String, properties: Map<String, Any?>, value: Double?) =
        PZSession.sendEvent(name, properties = properties.toObjC(), value = value?.let { NSNumber(double = it) })

    override fun addTriggers(triggers: Map<String, String>) = PZInAppMessages.addTriggers(triggers.toObjC())
    override fun removeTriggers(keys: Collection<String>) = PZInAppMessages.removeTriggers(keys.toList())
    override fun clearTriggers() = PZInAppMessages.clearTriggers()
    override fun getTriggers(): Map<String, String> = PZInAppMessages.getTriggers().toStringMap()
    override fun setScreen(name: String?) = PZInAppMessages.setScreen(name)
    override var inAppPaused: Boolean
        get() = PZInAppMessages.paused()
        set(value) = PZInAppMessages.setPaused(value)

    override val locationShared: Boolean get() = PZLocation.isShared()

    override val locationPermissionGranted: Boolean
        get() = lastLocationGrant ?: CLLocationManager().authorizationStatus.let {
            it == kCLAuthorizationStatusAuthorizedWhenInUse || it == kCLAuthorizationStatusAuthorizedAlways
        }

    override fun setLocationShared(shared: Boolean) = PZLocation.setShared(shared)

    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        PZLocation.requestPermissionWithRationale(rationale) { granted -> lastLocationGrant = granted }

    override fun onLocationPermissionResult() = Unit
    override val geofencingEnabled: Boolean get() = PZLocation.isGeofencingEnabled()
    override fun setGeofencingEnabled(enabled: Boolean) = PZLocation.setGeofencingEnabled(enabled)

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

    override suspend fun refreshFeatureFlags(): Boolean = suspendCoroutine { cont -> PZFeatureFlags.refreshWithCompletion { cont.resume(it) } }
    override fun isFeatureEnabled(key: String, default: Boolean): Boolean = PZFeatureFlags.isEnabled(key, defaultValue = default)
    override fun featureFlagValue(key: String): Any? = PZFeatureFlags.valueForKey(key, defaultValue = null).toCommonFlagValue()
}
