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

/** Bildirim kutusu öğesi. Zamanlar epoch milisaniyedir. */
data class PushizoInboxItem(
    val id: String,
    val messageId: String?,
    val title: String?,
    val body: String?,
    val url: String?,
    val imageUrl: String?,
    val data: Map<String, String>,
    val read: Boolean,
    val readAt: Long?,
    val archivedAt: Long?,
    val expiresAt: Long?,
    val createdAt: Long?,
)

/** İçerik kartı türü (ADR-0082); [wire] sunucudaki değerdir. */
enum class PushizoContentCardType(val wire: String) {
    CLASSIC("classic"),
    CAPTIONED_IMAGE("captioned_image"),
    IMAGE_ONLY("image_only");

    companion object {
        /** Bilinmeyen değer klasik kart sayılır. */
        fun from(wire: String?): PushizoContentCardType = entries.firstOrNull { it.wire == wire } ?: CLASSIC
    }
}

/** İçerik kartı (ADR-0082); kartlar bildirim kutusunda görünmez. Zamanlar epoch milisaniyedir. */
data class PushizoContentCard(
    val id: String,
    val messageId: String?,
    val type: PushizoContentCardType,
    val title: String?,
    val body: String?,
    val url: String?,
    val imageUrl: String?,
    val buttonText: String?,
    val data: Map<String, String>,
    val pinned: Boolean,
    val priority: Int,
    val viewed: Boolean,
    val clicked: Boolean,
    val expiresAt: Long?,
    val createdAt: Long?,
)

/** Kart akışı (sabitlenmiş → öncelik → yeni) ve görülmemiş kart sayısı. */
data class PushizoContentCardFeed(val cards: List<PushizoContentCard>, val unviewedCount: Int)

/** Bildirim kutusu sayfası (yeniden eskiye). */
data class PushizoInboxPage(val items: List<PushizoInboxItem>, val unreadCount: Int, val nextCursor: String?, val hasMore: Boolean)

/** Kullanıcı tercihi: kategori × kanal (`null` = tüm kanallar) → opt-in. [explicit] false ise kategori varsayılanından türetilmiştir. */
data class PushizoPreference(
    val category: String,
    /** `null` | `ios_push` | `android_push` | `web_push` | `email` | `sms`. */
    val channel: String?,
    val optedIn: Boolean,
    val explicit: Boolean,
    val source: String?,
    val updatedAt: Long?,
)

/** Mesaj kategorisi: çok dilli ad ve açıklama (`dil kodu → metin`), kanal kısıtı (`null` = tümü), varsayılan opt-in. */
data class PushizoMessageCategory(
    val key: String,
    val name: Map<String, String>,
    val description: Map<String, String>,
    val channels: List<String>?,
    val defaultOptedIn: Boolean,
) {
    /** Verilen dilde ad; yoksa İngilizce, o da yoksa ilk tanımlı dil. */
    fun localizedName(language: String): String = name[language] ?: name["en"] ?: name.values.firstOrNull() ?: key
}

/** Rıza kaydı (amaç → verildi mi). */
data class PushizoConsent(val purpose: String, val granted: Boolean, val source: String?, val updatedAt: Long?)

/** Tercih okuma/yazma sonucu: kullanıcının tercihleri, app'in kategorileri ve rıza kayıtları. */
data class PushizoPreferencesResult(
    val preferences: List<PushizoPreference>,
    val categories: List<PushizoMessageCategory>,
    val consents: List<PushizoConsent> = emptyList(),
)

/** `User.setPreferences` girdisi; [channel] null ise tüm kanallar. */
data class PushizoPreferenceInput(val category: String, val channel: String? = null, val optedIn: Boolean)

/** Bildirim teslim riski (yerel Android `HsDeliveryRisk` ile aynı adlar). */
enum class PushizoDeliveryRisk {
    /** Bilinen bir engel yok. */
    NONE,

    /** Bildirimler gecikebilir (saldırgan pil yönetimli üretici ve pil muafiyeti yok, seyrek kullanım kovası, veri tasarrufu). */
    ELEVATED,

    /** Bildirimler büyük olasılıkla gelmiyor (bildirim izni ya da kanalı kapalı, arka plan etkinliği kısıtlı). */
    HIGH,
}

/** `Pushizo.Delivery.openSettings` ve `showGuidance` ile açılabilen Android ayar ekranı (yerel `HsDeliverySetting` ile aynı adlar). */
enum class PushizoDeliverySetting {
    /** Duruma en uygun ekran ([PushizoDeliveryStatus.recommendedSettings] ilk öğesi; öneri yoksa pil optimizasyonu). */
    AUTO,

    /** Uygulamanın bildirim ayarları. */
    NOTIFICATIONS,

    /** Üreticinin otomatik başlatma / arka planda çalışma yöneticisi (yoksa uygulama ayrıntıları). */
    AUTO_START,

    /** Pil optimizasyonu: üreticinin pil yöneticisi, yoksa sistemin pil optimizasyonu listesi. */
    BATTERY_OPTIMIZATION,

    /** Uygulama ayrıntıları (arka plan kısıtı buradan kaldırılır). */
    APP_DETAILS,
}

/**
 * Cihazın bildirim teslim durumu (`Pushizo.Delivery.status()`; yerel Android `HsDeliveryStatus`). Kişisel veri içermez:
 * üretici, ROM ve kısıt bayrakları. Yalnızca Android'de doludur; iOS'ta ve Android'de SDK başlatılmadan önce [supported]
 * false, [risk] [PushizoDeliveryRisk.NONE] ve [recommendedSettings] boştur ([UNSUPPORTED]).
 */
data class PushizoDeliveryStatus(
    /** Teşhis bu platformda okunabildi mi. */
    val supported: Boolean,
    /** `Build.MANUFACTURER` (küçük harf). */
    val manufacturer: String = "",
    /** `Build.BRAND` (küçük harf). */
    val brand: String = "",
    /** Üretici ailesi: `xiaomi`, `huawei`, `honor`, `oppo`, `realme`, `oneplus`, `vivo`, `samsung`, `meizu`, `asus`, `transsion`, `other`. */
    val vendor: String = "other",
    /** Üreticinin kendi pil/otomatik başlatma yöneticisi arka plandaki uygulamaları durdurabiliyor mu. */
    val aggressiveVendor: Boolean = false,
    /** ROM kimliği: `miui`, `hyperos`, `emui`, `harmonyos`, `magicos`, `coloros`, `oxygenos`, `realmeui`, `funtouchos`, `originos`, `oneui`, `flyme`, `android`. */
    val rom: String = "",
    val romVersion: String? = null,
    /** Bildirim izni verilmiş ve bildirimler açık mı. */
    val notificationsEnabled: Boolean = true,
    /** Varsayılan bildirim kanalı kullanıcı tarafından kapatılmış mı. */
    val channelBlocked: Boolean = false,
    /** Uygulama pil optimizasyonundan muaf mı. */
    val batteryOptimizationExempt: Boolean = true,
    /** Kullanıcı arka plan etkinliğini kısıtlamış mı. */
    val backgroundRestricted: Boolean = false,
    /** Bekleme kovası: `exempted`, `active`, `working_set`, `frequent`, `rare`, `restricted` (okunamadıysa null). */
    val standbyBucket: String? = null,
    /** Pil tasarrufu kipi açık mı. */
    val powerSaveMode: Boolean = false,
    /** Veri tasarrufu uygulamanın arka plan verisini engelliyor mu. */
    val dataSaverEnabled: Boolean = false,
    /** Sistem arka plan kısıtı: kullanıcı kısıtlamış ya da uygulama `rare`/`restricted` kovasında. */
    val restricted: Boolean = false,
    val risk: PushizoDeliveryRisk = PushizoDeliveryRisk.NONE,
    /** Kullanıcının açması önerilen ayar ekranları (önem sırasıyla; öneri yoksa boş). */
    val recommendedSettings: List<PushizoDeliverySetting> = emptyList(),
) {
    companion object {
        /** Teşhisin desteklenmediği platformların durumu (risk yok). */
        val UNSUPPORTED = PushizoDeliveryStatus(supported = false)
    }
}
