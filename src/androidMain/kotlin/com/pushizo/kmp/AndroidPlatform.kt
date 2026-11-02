package com.pushizo.kmp

import android.app.Activity
import android.content.Context
import com.pushizo.sdk.HsClickEvent
import com.pushizo.sdk.HsForegroundEvent
import com.pushizo.sdk.HsLog
import com.pushizo.sdk.HsNotification
import com.pushizo.sdk.HsPushSubscriptionState
import com.pushizo.sdk.Pushizo as Native

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
)

internal fun HsClickEvent.toCommon() = PushizoClickEvent(notification.toCommon(), actionId, url)

internal fun HsPushSubscriptionState.toCommon() = PushizoSubscriptionState(id, token, optedIn, permission)

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

    override val locationShared: Boolean get() = Native.Location.isShared
    override val locationPermissionGranted: Boolean get() = Native.Location.permissionGranted
    override fun setLocationShared(shared: Boolean) = Native.Location.setShared(shared)
    override fun requestLocationPermission(host: PushizoPermissionHost, rationale: String?) =
        Native.Location.requestPermission(host, rationale)

    override fun onLocationPermissionResult() = Native.Location.onPermissionResult()
}
