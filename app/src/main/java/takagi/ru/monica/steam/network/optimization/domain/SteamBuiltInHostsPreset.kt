package takagi.ru.monica.steam.network.optimization.domain

/**
 * Built-in static Steam Hosts preset verified by the project maintainer.
 *
 * This only changes the destination address selected for matching hostnames. HTTPS keeps the
 * original hostname for SNI and certificate verification, and the normal resolver fallback is
 * kept enabled when the preset is applied.
 */
object SteamBuiltInHostsPreset {
    const val VERSION = 1

    val hostsText: String = """
        # Monica Steam built-in optimized Hosts v1
        184.87.199.210 api.steampowered.com
        199.232.215.52 avatars.fastly.steamstatic.com
        146.75.47.52 cdn.steamstatic.com
        23.222.128.240 checkout.steampowered.com
        104.84.150.150 community.akamai.steamstatic.com
        173.222.146.99 help.steampowered.com
        104.102.49.106 login.steampowered.com
        23.49.104.45 media.steampowered.com
        23.203.76.5 s.team
        199.232.215.52 shared.steamstatic.com
        23.222.131.51 steam-chat.com
        23.49.104.183 steamcdn-a.akamaihd.net
        23.32.91.196 steamcommunity-a.akamaihd.net
        23.49.104.51 steamconnecttest.com
        23.33.126.178 steamuserimages-a.akamaihd.net
        23.33.126.191 store.akamai.steamstatic.com
        184.84.58.165 store.steampowered.com
        172.234.232.226 support.steampowered.com
    """.trimIndent()

    val parsed: SteamHostsParseResult by lazy { SteamHostsRuleParser.parse(hostsText) }
}
