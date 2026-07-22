package takagi.ru.monica.keepass

import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object KeePassTotpCodec {
    const val FIELD_OTP = "otp"
    const val FIELD_TOTP_SEED = "TOTP Seed"
    const val FIELD_TOTP_SETTINGS = "TOTP Settings"
    const val FIELD_TOTP_PERIOD = "TOTP Period"
    const val FIELD_TOTP_DIGITS = "TOTP Digits"
    const val FIELD_TOTP_ALGORITHM = "TOTP Algorithm"
    const val FIELD_OTP_TYPE = "OTP Type"
    const val FIELD_HOTP_COUNTER = "HOTP Counter"

    data class Fields(
        val otp: String = "",
        val seed: String = "",
        val settings: String = "",
        val period: String = "",
        val digits: String = "",
        val algorithm: String = "",
        val counter: String = "",
        val type: String = "",
        val issuer: String = "",
        val accountName: String = "",
        val link: String = ""
    )

    fun parse(fields: Fields): TotpData? {
        parseOtpAuthUri(fields.otp, fields.issuer, fields.accountName, fields.link)?.let { return it }

        val secret = normalizeSecret(
            when {
                fields.seed.isNotBlank() -> fields.seed
                fields.otp.isNotBlank() && !fields.otp.contains("://") -> fields.otp
                else -> ""
            }
        )
        if (secret.isBlank()) return null

        val settings = parseSettings(fields)
        return TotpData(
            secret = secret,
            issuer = fields.issuer,
            accountName = fields.accountName,
            period = settings.period,
            digits = settings.digits,
            algorithm = settings.algorithm,
            otpType = settings.otpType,
            counter = settings.counter,
            link = fields.link
        )
    }

    fun toKeePassFields(data: TotpData, title: String): Map<String, String> {
        val normalized = data.copy(
            secret = normalizeSecret(data.secret),
            algorithm = data.algorithm.trim().uppercase(Locale.ROOT).ifBlank { "SHA1" },
            period = data.period.takeIf { it > 0 } ?: 30,
            digits = data.digits.takeIf { it > 0 } ?: 6,
            counter = data.counter.coerceAtLeast(0L)
        )
        if (normalized.secret.isBlank()) return emptyMap()

        val settings = buildList {
            add("period=${normalized.period}")
            add("digits=${normalized.digits}")
            add("algorithm=${normalized.algorithm}")
            if (normalized.otpType == OtpType.HOTP) {
                add("type=hotp")
                add("counter=${normalized.counter}")
            }
        }.joinToString(";")

        return buildMap {
            put(FIELD_OTP, buildOtpAuthUri(normalized, title))
            put(FIELD_TOTP_SEED, normalized.secret)
            put(FIELD_TOTP_SETTINGS, settings)
            put(FIELD_TOTP_PERIOD, normalized.period.toString())
            put(FIELD_TOTP_DIGITS, normalized.digits.toString())
            put(FIELD_TOTP_ALGORITHM, normalized.algorithm)
            if (normalized.otpType == OtpType.HOTP) {
                put(FIELD_OTP_TYPE, "HOTP")
                put(FIELD_HOTP_COUNTER, normalized.counter.toString())
            } else {
                put(FIELD_OTP_TYPE, "TOTP")
            }
        }
    }

    fun normalizeSecret(value: String): String {
        return value
            .replace(Regex("[\\s\\-]"), "")
            .uppercase(Locale.ROOT)
    }

    private data class ParsedSettings(
        val period: Int = 30,
        val digits: Int = 6,
        val algorithm: String = "SHA1",
        val otpType: OtpType = OtpType.TOTP,
        val counter: Long = 0L
    )

    private fun parseSettings(fields: Fields): ParsedSettings {
        var period = 30
        var digits = 6
        var algorithm = "SHA1"
        var otpType = OtpType.TOTP
        var counter = 0L

        val tokens = fields.settings.split(";", ",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        tokens.forEach { token ->
            if (token.contains("=")) {
                val parts = token.split("=", limit = 2)
                val key = parts[0].trim().lowercase(Locale.ROOT)
                val value = parts.getOrNull(1)?.trim().orEmpty()
                when (key) {
                    "period", "step", "time_step" -> value.toIntOrNull()?.let { period = it }
                    "digits", "length" -> value.toIntOrNull()?.let { digits = it }
                    "algorithm", "algo", "digest" -> if (value.isNotBlank()) algorithm = value.uppercase(Locale.ROOT)
                    "counter" -> value.toLongOrNull()?.let {
                        counter = it
                        otpType = OtpType.HOTP
                    }
                    "type", "otp_type" -> if (value.equals("hotp", ignoreCase = true)) otpType = OtpType.HOTP
                }
            } else {
                token.toIntOrNull()?.let { number ->
                    when {
                        period == 30 -> period = number
                        digits == 6 -> digits = number
                    }
                }
                if (token.startsWith("SHA", ignoreCase = true)) {
                    algorithm = token.uppercase(Locale.ROOT)
                }
                if (token.equals("HOTP", ignoreCase = true)) {
                    otpType = OtpType.HOTP
                }
            }
        }

        fields.period.toIntOrNull()?.let { period = it }
        fields.digits.toIntOrNull()?.let { digits = it }
        if (fields.algorithm.isNotBlank()) {
            algorithm = fields.algorithm.uppercase(Locale.ROOT)
        }
        fields.counter.toLongOrNull()?.let {
            counter = it
            otpType = OtpType.HOTP
        }
        if (fields.type.equals("hotp", ignoreCase = true)) {
            otpType = OtpType.HOTP
        }

        return ParsedSettings(
            period = period,
            digits = digits,
            algorithm = algorithm,
            otpType = otpType,
            counter = counter
        )
    }

    private fun parseOtpAuthUri(
        uri: String,
        fallbackIssuer: String,
        fallbackAccount: String,
        fallbackLink: String
    ): TotpData? {
        if (!uri.startsWith("otpauth://", ignoreCase = true)) return null
        return runCatching {
            val parsed = URI(uri)
            val typeRaw = parsed.host?.lowercase(Locale.ROOT).orEmpty()
            val otpType = if (typeRaw == "hotp") OtpType.HOTP else OtpType.TOTP

            val decodedLabel = URLDecoder.decode(parsed.path.trimStart('/'), "UTF-8")
            val (labelIssuer, labelAccount) = if (decodedLabel.contains(":")) {
                val parts = decodedLabel.split(":", limit = 2)
                parts[0] to parts[1]
            } else {
                "" to decodedLabel
            }

            val params = mutableMapOf<String, String>()
            parsed.query?.split("&")?.forEach { pair ->
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2) {
                    params[kv[0].lowercase(Locale.ROOT)] = URLDecoder.decode(kv[1], "UTF-8")
                }
            }

            val secret = normalizeSecret(params["secret"].orEmpty())
            if (secret.isBlank()) return null

            val issuer = params["issuer"].orEmpty().ifBlank { labelIssuer }.ifBlank { fallbackIssuer }
            val account = labelAccount.ifBlank { fallbackAccount }
            val algorithm = params["algorithm"]?.uppercase(Locale.ROOT) ?: "SHA1"
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            val counter = params["counter"]?.toLongOrNull() ?: 0L

            TotpData(
                secret = secret,
                issuer = issuer,
                accountName = account,
                period = period,
                digits = digits,
                algorithm = algorithm,
                otpType = otpType,
                counter = counter,
                link = fallbackLink
            )
        }.getOrNull()
    }

    private fun buildOtpAuthUri(data: TotpData, title: String): String {
        val type = if (data.otpType == OtpType.HOTP) "hotp" else "totp"
        val label = encodeUriComponent(
            when {
                data.issuer.isNotBlank() && data.accountName.isNotBlank() -> "${data.issuer}:${data.accountName}"
                data.accountName.isNotBlank() -> data.accountName
                data.issuer.isNotBlank() -> data.issuer
                title.isNotBlank() -> title
                else -> "Authenticator"
            }
        )
        val query = buildList {
            add("secret=${encodeUriComponent(data.secret)}")
            if (data.issuer.isNotBlank()) {
                add("issuer=${encodeUriComponent(data.issuer)}")
            }
            if (!data.algorithm.equals("SHA1", ignoreCase = true)) {
                add("algorithm=${encodeUriComponent(data.algorithm.uppercase(Locale.ROOT))}")
            }
            if (data.digits != 6) {
                add("digits=${data.digits}")
            }
            if (data.period != 30) {
                add("period=${data.period}")
            }
            if (data.otpType == OtpType.HOTP) {
                add("counter=${data.counter.coerceAtLeast(0L)}")
            }
        }.joinToString("&")
        return "otpauth://$type/$label?$query"
    }

    private fun encodeUriComponent(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
