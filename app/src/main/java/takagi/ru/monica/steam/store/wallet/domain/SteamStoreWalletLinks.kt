package takagi.ru.monica.steam.store.wallet.domain

/** Wallet operations remain on Steam's official, account-authenticated pages. */
internal object SteamStoreWalletLinks {
    const val REDEEM_GIFT_CARD_URL = "https://store.steampowered.com/account/redeemwalletcode?l=schinese"
    const val WALLET_BALANCE_URL = "https://store.steampowered.com/steamaccount/addfunds?l=schinese"
}
