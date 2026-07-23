package takagi.ru.monica.steam.profile.ui

internal data class SteamMiniProfileCropTransform(
    val scaleX: Float,
    val scaleY: Float,
)

internal fun calculateSteamMiniProfileCenterCrop(
    viewWidth: Int,
    viewHeight: Int,
    mediaWidth: Int,
    mediaHeight: Int,
): SteamMiniProfileCropTransform {
    if (viewWidth <= 0 || viewHeight <= 0 || mediaWidth <= 0 || mediaHeight <= 0) {
        return SteamMiniProfileCropTransform(scaleX = 1f, scaleY = 1f)
    }
    val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
    val mediaAspectRatio = mediaWidth.toFloat() / mediaHeight.toFloat()
    return if (mediaAspectRatio > viewAspectRatio) {
        SteamMiniProfileCropTransform(
            scaleX = mediaAspectRatio / viewAspectRatio,
            scaleY = 1f,
        )
    } else {
        SteamMiniProfileCropTransform(
            scaleX = 1f,
            scaleY = viewAspectRatio / mediaAspectRatio,
        )
    }
}
