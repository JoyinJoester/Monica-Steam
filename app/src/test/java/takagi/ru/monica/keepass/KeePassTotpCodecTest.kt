package takagi.ru.monica.keepass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

class KeePassTotpCodecTest {

    @Test
    fun parsesOtpAuthUri() {
        val data = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                otp = "otpauth://totp/GitHub:user%40example.com?secret=jbsw-y3dp%20ehpk3pxp&issuer=GitHub&algorithm=SHA256&digits=8&period=45",
                issuer = "Fallback",
                accountName = "fallback-user",
                link = "https://github.com"
            )
        )

        assertNotNull(data)
        requireNotNull(data)
        assertEquals("JBSWY3DPEHPK3PXP", data.secret)
        assertEquals("GitHub", data.issuer)
        assertEquals("user@example.com", data.accountName)
        assertEquals("SHA256", data.algorithm)
        assertEquals(8, data.digits)
        assertEquals(45, data.period)
        assertEquals(OtpType.TOTP, data.otpType)
        assertEquals("https://github.com", data.link)
    }

    @Test
    fun parsesPlainOtpSecretWithSettings() {
        val data = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                otp = "jbsw-y3dp ehpk3pxp",
                settings = "period=60;digits=8;algorithm=sha512",
                issuer = "GitLab",
                accountName = "user"
            )
        )

        assertNotNull(data)
        requireNotNull(data)
        assertEquals("JBSWY3DPEHPK3PXP", data.secret)
        assertEquals("GitLab", data.issuer)
        assertEquals("user", data.accountName)
        assertEquals(60, data.period)
        assertEquals(8, data.digits)
        assertEquals("SHA512", data.algorithm)
    }

    @Test
    fun parsesTotpSeedWithSeparateFields() {
        val data = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                seed = "abcd efgh ijkl mnop",
                period = "45",
                digits = "7",
                algorithm = "sha256",
                issuer = "KeePassDX",
                accountName = "alice"
            )
        )

        assertNotNull(data)
        requireNotNull(data)
        assertEquals("ABCDEFGHIJKLMNOP", data.secret)
        assertEquals(45, data.period)
        assertEquals(7, data.digits)
        assertEquals("SHA256", data.algorithm)
    }

    @Test
    fun parsesHotpCounterFromSettingsOrSeparateField() {
        val settingsData = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                seed = "JBSWY3DPEHPK3PXP",
                settings = "type=hotp counter=42"
            )
        )
        assertNotNull(settingsData)
        requireNotNull(settingsData)
        assertEquals(OtpType.HOTP, settingsData.otpType)
        assertEquals(42L, settingsData.counter)

        val separateData = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                seed = "JBSWY3DPEHPK3PXP",
                counter = "99"
            )
        )
        assertNotNull(separateData)
        requireNotNull(separateData)
        assertEquals(OtpType.HOTP, separateData.otpType)
        assertEquals(99L, separateData.counter)
    }

    @Test
    fun returnsNullWhenNoSecretExists() {
        val data = KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                settings = "period=60;digits=8",
                issuer = "No secret"
            )
        )

        assertNull(data)
    }

    @Test
    fun emitsKeePassCompatibleTotpFields() {
        val fields = KeePassTotpCodec.toKeePassFields(
            data = TotpData(
                secret = "jbsw-y3dp ehpk3pxp",
                issuer = "GitHub",
                accountName = "user@example.com",
                period = 45,
                digits = 8,
                algorithm = "sha256"
            ),
            title = "GitHub"
        )

        assertEquals("JBSWY3DPEHPK3PXP", fields.getValue(KeePassTotpCodec.FIELD_TOTP_SEED))
        assertEquals("45", fields.getValue(KeePassTotpCodec.FIELD_TOTP_PERIOD))
        assertEquals("8", fields.getValue(KeePassTotpCodec.FIELD_TOTP_DIGITS))
        assertEquals("SHA256", fields.getValue(KeePassTotpCodec.FIELD_TOTP_ALGORITHM))
        assertEquals("TOTP", fields.getValue(KeePassTotpCodec.FIELD_OTP_TYPE))
        assertEquals("period=45;digits=8;algorithm=SHA256", fields.getValue(KeePassTotpCodec.FIELD_TOTP_SETTINGS))
        assertEquals(
            "otpauth://totp/GitHub%3Auser%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA256&digits=8&period=45",
            fields.getValue(KeePassTotpCodec.FIELD_OTP)
        )
    }

    @Test
    fun emitsKeePassCompatibleHotpFields() {
        val fields = KeePassTotpCodec.toKeePassFields(
            data = TotpData(
                secret = "JBSWY3DPEHPK3PXP",
                issuer = "Example",
                accountName = "alice",
                otpType = OtpType.HOTP,
                counter = 12L
            ),
            title = "Example"
        )

        assertEquals("HOTP", fields.getValue(KeePassTotpCodec.FIELD_OTP_TYPE))
        assertEquals("12", fields.getValue(KeePassTotpCodec.FIELD_HOTP_COUNTER))
        assertEquals("period=30;digits=6;algorithm=SHA1;type=hotp;counter=12", fields.getValue(KeePassTotpCodec.FIELD_TOTP_SETTINGS))
        assertEquals(
            "otpauth://hotp/Example%3Aalice?secret=JBSWY3DPEHPK3PXP&issuer=Example&counter=12",
            fields.getValue(KeePassTotpCodec.FIELD_OTP)
        )
    }
}
