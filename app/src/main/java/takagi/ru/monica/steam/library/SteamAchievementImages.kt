package takagi.ru.monica.steam.library

import takagi.ru.monica.steam.space.data.SteamSpaceLinks

/** Steam definitions may contain a filename instead of an absolute image URL. */
internal fun steamAchievementImageUrls(appId: Int, achievement: SteamAchievement): List<String> {
    val normal = SteamSpaceLinks.achievementIcon(appId, achievement.iconUrl)
    val locked = SteamSpaceLinks.achievementIcon(appId, achievement.lockedIconUrl)
    return (if (achievement.achieved) listOfNotNull(normal, locked) else listOfNotNull(locked, normal)).distinct()
}
