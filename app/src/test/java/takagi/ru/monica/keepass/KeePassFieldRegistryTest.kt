package takagi.ru.monica.keepass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeePassFieldRegistryTest {

    @Test
    fun classifiesKeePassTotpAliasesCaseInsensitively() {
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("otp"))
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("OTP"))
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("TOTP Seed"))
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("totpsettings"))
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("OTP Type"))
        assertEquals(KeePassFieldRole.KEEPASS_TOTP, KeePassFieldRegistry.roleOf("HOTP Counter"))
        assertTrue(KeePassFieldRegistry.isKeePassTotpField("TOTP Seed"))
        assertTrue(KeePassFieldRegistry.isKeePassTotpField("HOTP Counter"))
    }

    @Test
    fun classifiesMonicaOwnedFieldsSeparatelyFromPreservedFields() {
        assertEquals(KeePassFieldRole.MONICA_PASSWORD, KeePassFieldRegistry.roleOf("MonicaLocalId"))
        assertEquals(KeePassFieldRole.MONICA_SECURE_ITEM, KeePassFieldRegistry.roleOf("MonicaItemData"))
        assertEquals(KeePassFieldRole.MONICA_PASSKEY, KeePassFieldRegistry.roleOf("MonicaPasskeyData"))

        assertTrue(KeePassFieldRegistry.isMonicaOwned("MonicaItemData"))
        assertFalse(KeePassFieldRegistry.isMonicaOwned("otp"))
        assertFalse(KeePassFieldRegistry.isMonicaOwned("KPEX_PASSKEY_CREDENTIAL_ID"))
        assertTrue(KeePassFieldRegistry.isPreservedByDefault("unknown-plugin-field"))
    }

    @Test
    fun classifiesKeepPassPluginAndPasskeyFieldsAsPreserved() {
        assertEquals(KeePassFieldRole.KEEPASS_PLUGIN, KeePassFieldRegistry.roleOf("_etm_template"))
        assertEquals(KeePassFieldRole.KEEPASS_PASSKEY, KeePassFieldRegistry.roleOf("KPEX_PASSKEY_PRIVATE_KEY_PEM"))
        assertEquals(KeePassFieldRole.UNKNOWN, KeePassFieldRegistry.roleOf("Security question"))

        assertTrue(KeePassFieldRegistry.isPreservedByDefault("_etm_template"))
        assertTrue(KeePassFieldRegistry.isPreservedByDefault("KPEX_PASSKEY_PRIVATE_KEY_PEM"))
        assertTrue(KeePassFieldRegistry.isPreservedByDefault("Security question"))
    }

    @Test
    fun exposesPasswordProjectionAndFallbackDecisions() {
        assertTrue(KeePassFieldRegistry.isReservedPasswordProjectionField("Title"))
        assertTrue(KeePassFieldRegistry.isReservedPasswordProjectionField("otp"))
        assertTrue(KeePassFieldRegistry.isReservedPasswordProjectionField("_etm_template"))
        assertFalse(KeePassFieldRegistry.isReservedPasswordProjectionField("Security question"))

        assertTrue(KeePassFieldRegistry.isPasswordEntryOverlayField("Title"))
        assertTrue(KeePassFieldRegistry.isPasswordEntryOverlayField("MonicaSshPrivateKey"))
        assertTrue(KeePassFieldRegistry.isPasswordEntryOverlayField("MonicaConflictCopy"))
        assertFalse(KeePassFieldRegistry.isPasswordEntryOverlayField("otp"))
        assertFalse(KeePassFieldRegistry.isPasswordEntryOverlayField("Security question"))

        assertTrue(KeePassFieldRegistry.isSecureItemOverlayField("Title"))
        assertTrue(KeePassFieldRegistry.isSecureItemOverlayField("Card Number"))
        assertTrue(KeePassFieldRegistry.isSecureItemOverlayField("MonicaItemData"))
        assertTrue(KeePassFieldRegistry.isSecureItemOverlayField("MonicaConflictCopy"))
        assertFalse(KeePassFieldRegistry.isSecureItemOverlayField("otp"))
        assertFalse(KeePassFieldRegistry.isSecureItemOverlayField("Security question"))

        assertTrue(KeePassFieldRegistry.isPasskeyEntryOverlayField("Title"))
        assertTrue(KeePassFieldRegistry.isPasskeyEntryOverlayField("MonicaPasskeyData"))
        assertTrue(KeePassFieldRegistry.isPasskeyEntryOverlayField("KPEX_PASSKEY_PRIVATE_KEY_PEM"))
        assertTrue(KeePassFieldRegistry.isPasskeyEntryOverlayField("MonicaConflictCopy"))
        assertFalse(KeePassFieldRegistry.isPasskeyEntryOverlayField("otp"))
        assertFalse(KeePassFieldRegistry.isPasskeyEntryOverlayField("Security question"))

        assertTrue(KeePassFieldRegistry.isPasswordSecretFallbackCandidateField("Security question"))
        assertFalse(KeePassFieldRegistry.isPasswordSecretFallbackCandidateField("Password"))
        assertFalse(KeePassFieldRegistry.isPasswordSecretFallbackCandidateField("KPEX_PASSKEY_PRIVATE_KEY_PEM"))
    }
}
