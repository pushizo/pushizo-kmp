package com.pushizo.kmp

/** Yerel SDK günlük düzeyi. */
enum class PushizoLogLevel { NONE, ERROR, WARN, INFO, DEBUG }

/**
 * Başlatma seçenekleri. [apiUrl] boş bırakılırsa SDK varsayılan global kökten başlar ve app'in bölgesel adresini kendisi
 * öğrenir. [requiresPrivacyConsent] açıkken `setConsentGiven(true)` çağrılana kadar ağ çağrısı yapılmaz (KVKK/GDPR).
 */
data class PushizoOptions(
    val apiUrl: String? = null,
    val requiresPrivacyConsent: Boolean = false,
    val logLevel: PushizoLogLevel = PushizoLogLevel.WARN,
)

/** Girişle birlikte ayarlanan profil. Geçersiz e-posta veya telefon girişten önce reddedilir. */
data class PushizoUserProfile(
    val email: String? = null,
    val sms: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val aliases: Map<String, String> = emptyMap(),
)

/** Bildirim düğmesi. */
data class PushizoNotificationAction(val id: String, val title: String, val url: String?)

/** Gelen bildirimin platformdan bağımsız görünümü. */
data class PushizoNotification(
    val title: String?,
    val subtitle: String?,
    val body: String?,
    val imageUrl: String?,
    val url: String?,
    val deliveryId: String?,
    val messageId: String?,
    val actions: List<PushizoNotificationAction>,
    val data: Map<String, String>,
)

/** Bildirim tıklaması. [actionId] düğmeye basıldıysa dolar. */
data class PushizoClickEvent(val notification: PushizoNotification, val actionId: String?, val url: String?)

/** Ön planda gelen bildirim: [preventDefault] gösterimi engeller, [display] sonradan gösterir. */
interface PushizoForegroundEvent {
    val notification: PushizoNotification
    fun preventDefault()
    fun display()
}

/** Push aboneliği durumu. */
data class PushizoSubscriptionState(val id: String?, val token: String?, val optedIn: Boolean, val permission: Boolean)

/** Eklenen dinleyiciyi kaldırır; birden çok kez çağrılması zararsızdır. */
fun interface PushizoListenerRegistration {
    fun remove()
}
