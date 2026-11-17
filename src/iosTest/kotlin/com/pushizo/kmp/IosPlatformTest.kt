package com.pushizo.kmp

import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** iOS köprüsü: Objective-C cephesinden gelen Foundation türlerinin ortak modele çevrilmesi ve gerçek cepheyle çağrılar. */
class IosPlatformTest {
    @Test
    fun hataAlaniAndroidIleAyniIstisnaTurlerineCevrilir() {
        fun error(code: Long, domain: String = PZ_ERROR_DOMAIN) =
            NSError.errorWithDomain(domain, code, mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "mesaj $code"))
        assertIs<IllegalStateException>(error(1).toKotlinException())
        assertIs<IllegalStateException>(error(3).toKotlinException())
        val invalid = error(4).toKotlinException()
        assertIs<IllegalArgumentException>(invalid)
        assertEquals("mesaj 4", invalid.message)
        assertIs<RuntimeException>(error(5).toKotlinException())
        assertIs<RuntimeException>(error(1, "NSURLErrorDomain").toKotlinException())
    }

    @Test
    fun tarihEpochMilisaniyeyeCevrilir() {
        assertEquals(1_700_000_000_500L, NSDate.dateWithTimeIntervalSince1970(1_700_000_000.5).toEpochMillis())
        assertNull((null as NSDate?).toEpochMillis())
    }

    @Test
    fun bayrakDegeriFoundationTurlerindenOrtakTurlereCevrilir() {
        assertEquals(true, NSNumber(bool = true).toCommonFlagValue())
        assertEquals(3, NSNumber(long = 3).toCommonFlagValue())
        assertEquals(5_000_000_000L, NSNumber(longLong = 5_000_000_000L).toCommonFlagValue())
        assertEquals(1.5, NSNumber(double = 1.5).toCommonFlagValue())
        assertNull(NSNull.`null`().toCommonFlagValue())
        assertEquals("metin", "metin".toCommonFlagValue())
        val nested = mapOf<Any?, Any?>("a" to NSNumber(long = 1), "b" to listOf("x", NSNull.`null`()), "c" to NSNumber(bool = false)).toCommonFlagValue()
        assertEquals(mapOf("a" to 1, "b" to listOf("x", null), "c" to false), nested)
    }

    @Test
    fun bildirimIzniTamVeGeciciIzindeVerilmisSayilir() {
        assertTrue(isPermissionGranted(UNAuthorizationStatusAuthorized))
        assertTrue(isPermissionGranted(UNAuthorizationStatusProvisional))
        assertFalse(isPermissionGranted(UNAuthorizationStatusDenied))
        assertFalse(isPermissionGranted(UNAuthorizationStatusNotDetermined))
    }

    @Test
    fun sozlukDonusumleri() {
        assertEquals(mapOf("plan" to "pro"), mapOf<Any?, Any?>("plan" to "pro", 1 to "x", "n" to NSNumber(long = 2)).toStringMap())
        assertEquals(mapOf<Any?, Any?>("a" to "b"), mapOf("a" to "b").toObjC())
    }

    @Test
    fun gercekCepheBaslatilmadanVarsayilanlariDondurur() {
        // Ortak API → iOS platformu → Pushizo.xcframework Objective-C cephesi (simülatörde gerçek framework).
        assertFalse(IosPushizoPlatform.isInitialized)
        assertNull(IosPushizoPlatform.externalId)
        assertTrue(IosPushizoPlatform.getTags().isEmpty())
        assertFalse(IosPushizoPlatform.geofencingEnabled)
        assertTrue(IosPushizoPlatform.isFeatureEnabled("yeni_sepet", default = true))
        assertNull(IosPushizoPlatform.featureFlagValue("yok"))
        assertFalse(IosPushizoPlatform.subscriptionState.optedIn)
    }

    @Test
    fun androidTeslimatGuclendirmeIosTaZararsizdir() {
        val status = IosPushizoPlatform.deliveryStatus()
        assertFalse(status.supported)
        assertEquals(PushizoDeliveryRisk.NONE, status.risk)
        assertTrue(status.recommendedSettings.isEmpty())
        assertFalse(IosPushizoPlatform.shouldShowDeliveryGuidance(14))
        assertFalse(IosPushizoPlatform.openDeliverySettings(PushizoApplicationContext, PushizoDeliverySetting.AUTO_START))
        var result: Boolean? = null
        IosPushizoPlatform.showDeliveryGuidance(platform.UIKit.UIViewController(), PushizoDeliverySetting.AUTO, null) { result = it }
        assertEquals(false, result)
    }
}
