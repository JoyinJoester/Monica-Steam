package takagi.ru.monica.steam.confirmations

import takagi.ru.monica.steam.network.SteamConfirmation

enum class SteamConfirmationKind {
    GIFT,
    TRADE,
    MARKET,
    SECURITY,
    FAMILY,
    OTHER
}

object SteamConfirmationKindClassifier {
    private val giftWords = setOf(
        "gift", "gifted", "present", "赠礼", "礼物", "贈禮", "禮物"
    )
    private val tradeWords = setOf(
        "trade", "offer", "交换", "交易", "交換"
    )
    private val marketWords = setOf(
        "market", "listing", "sell", "出售", "上架", "市场", "市場"
    )
    private val securityWords = setOf(
        "login", "sign in", "account", "recovery", "phone", "api key",
        "authenticator", "登录", "登入", "账户", "帳戶", "恢复", "恢復",
        "手机", "手機", "验证器", "驗證器"
    )
    private val familyWords = setOf(
        "family", "household", "家庭"
    )

    fun classify(confirmation: SteamConfirmation): SteamConfirmationKind {
        val rawType = confirmation.type.trim().lowercase()
        val numericType = rawType.toIntOrNull()
        val text = "$rawType ${confirmation.headline} ${confirmation.summary}".lowercase()

        // Generic confirmations may contain gifts, so textual gift context has
        // priority over the numeric fallback.
        if (giftWords.any(text::contains)) return SteamConfirmationKind.GIFT

        return when (numericType) {
            2 -> SteamConfirmationKind.TRADE
            3 -> SteamConfirmationKind.MARKET
            4, 5, 6, 7 -> SteamConfirmationKind.SECURITY
            8 -> SteamConfirmationKind.FAMILY
            else -> when {
                marketWords.any(text::contains) -> SteamConfirmationKind.MARKET
                tradeWords.any(text::contains) -> SteamConfirmationKind.TRADE
                familyWords.any(text::contains) -> SteamConfirmationKind.FAMILY
                securityWords.any(text::contains) -> SteamConfirmationKind.SECURITY
                else -> SteamConfirmationKind.OTHER
            }
        }
    }
}

