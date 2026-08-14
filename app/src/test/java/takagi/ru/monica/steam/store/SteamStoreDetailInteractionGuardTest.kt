package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreDetailInteractionGuardTest {
    @Test
    fun screenshotsOpenTheDedicatedFullscreenViewer() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewerUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/gallery/SteamStoreScreenshotGallery.kt"
        )
        val sharedViewer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImageViewer.kt"
        )
        val sharedPage = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamFullscreenImagePage.kt"
        )
        val downloader = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/media/SteamImageDownloader.kt"
        )

        assertTrue(viewerUi.exists())
        assertTrue(sharedViewer.exists())
        assertTrue(sharedPage.exists())
        assertTrue(downloader.exists())
        val viewerSource = viewerUi.readText()
        val sharedViewerSource = sharedViewer.readText()
        val sharedPageSource = sharedPage.readText()
        assertTrue(detailUi.contains("itemsIndexed(detail.screenshots"))
        assertTrue(detailUi.contains("SteamStoreScreenshotViewer("))
        assertTrue(detailUi.contains("selectedScreenshotIndex = index"))
        assertTrue(viewerSource.contains("SteamFullscreenImageViewer("))
        assertTrue(sharedViewerSource.contains("DialogProperties("))
        assertTrue(sharedViewerSource.contains("usePlatformDefaultWidth = false"))
        assertTrue(sharedViewerSource.contains("HorizontalPager("))
        assertTrue(sharedViewerSource.contains("rememberPagerState("))
        assertTrue(sharedViewerSource.contains("initialPage = initialIndex.coerceIn"))
        assertTrue(sharedViewerSource.contains("pagerState.animateScrollToPage"))
        assertTrue(sharedViewerSource.contains("background(Color.Black)"))
        assertTrue(sharedViewerSource.contains("SteamImageDownloader("))
        assertTrue(sharedPageSource.contains("ContentScale.Fit"))
        assertTrue(sharedPageSource.contains("canPan = { scale > 1f }"))
        assertTrue(sharedPageSource.contains("detectTapGestures("))
        assertTrue(sharedPageSource.contains("onDoubleTap ="))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_previous"))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_next"))
        assertTrue(viewerSource.contains("R.string.steam_store_screenshot_download"))
    }

    @Test
    fun heroClearsTheStatusBarAndOpensTheNativeViewer() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val hero = detailUi.substring(
            detailUi.indexOf("val heroBackgroundUrl"),
            detailUi.indexOf("if (cached) item")
        )

        assertTrue(hero.contains("var showHeroViewer"))
        assertTrue(hero.contains(".statusBarsPadding()"))
        assertTrue(hero.contains(".clickable(enabled = heroViewerUrl.isNotBlank())"))
        assertTrue(hero.contains("showHeroViewer = true"))
        assertTrue(detailUi.contains("screenshots = listOf(heroViewerUrl)"))
    }

    @Test
    fun detailActionToolbarFloatsOutsideTheScrollableHero() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val detail = detailUi
            .substringAfter("private fun SteamStoreDetailContent(")
            .substringBefore("private fun SteamStorePurchaseActions(")
        val toolbar = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreDetailActionToolbar.kt"
        ).readText()
        val hero = detail
            .substringAfter("item {\n            Box(Modifier.fillMaxWidth().height(390.dp))")
            .substringBefore("if (showTags && detail.tags.isNotEmpty())")

        assertTrue(detail.contains("Box(modifier = modifier.fillMaxSize())"))
        assertTrue(detail.contains("modifier = Modifier.fillMaxSize(),\n            state = listState"))
        assertFalse(hero.contains("SteamStoreDetailActionToolbar("))
        assertTrue(detail.contains(".steamWindowTopPadding()"))
        assertTrue(detail.contains(".steamWindowBottomPadding()"))
        assertTrue(detail.contains(".padding(bottom = dockContentClearance)"))
        assertFalse(detail.contains(".padding(end = 12.dp"))
        assertTrue(toolbar.contains("BoxWithConstraints("))
        assertTrue(toolbar.contains("detectDragGestures("))
        assertTrue(toolbar.contains("AUTO_COLLAPSE_MILLIS"))
        assertTrue(toolbar.contains("SteamStoreDetailToolbarEdge.LEFT"))
        assertTrue(toolbar.contains("SteamStoreDetailToolbarEdge.RIGHT"))
        assertTrue(toolbar.contains("Icons.Default.MoreVert"))
        assertTrue(toolbar.contains("dragging = dragging"))
    }

    @Test
    fun fullDetailsAndInformationValuesAreSelectable() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(detailUi.contains("detail.about.ifBlank { detail.shortDescription }"))
        val detailSection = detailUi.substring(
            detailUi.indexOf("fun DetailTextSection"),
            detailUi.indexOf("fun DetailLine")
        )
        val detailLine = detailUi.substring(
            detailUi.indexOf("fun DetailLine"),
            detailUi.indexOf("fun PriceRow")
        )
        assertTrue(detailSection.contains("SelectionContainer"))
        assertTrue(detailLine.contains("SelectionContainer"))
    }

    @Test
    fun reviewExpansionActionFollowsThePreviewCards() {
        val reviewList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewList.kt"
        ).readText()
        val reviewSummary = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreReviewSummary.kt"
        ).readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        assertFalse(reviewSummary.contains("onOpenAll"))
        assertFalse(reviewSummary.contains("steam_store_reviews_view_all"))
        assertTrue(zhStrings.contains("<string name=\"steam_store_reviews_show_more\">查看更多</string>"))
        val reviewCardsIndex = reviewList.indexOf("visibleReviews.forEach")
        val moreButtonIndex = reviewList.indexOf("R.string.steam_store_reviews_show_more")
        assertTrue(reviewCardsIndex >= 0)
        assertTrue(moreButtonIndex > reviewCardsIndex)
        assertTrue(reviewList.contains("Modifier.fillMaxWidth().heightIn(min = 48.dp)"))
        assertTrue(reviewList.contains("onOpenAuthor"))
        assertTrue(reviewList.contains("review.authorSteamId"))
        assertTrue(reviewList.contains("R.string.steam_store_review_view_author"))
    }

    @Test
    fun websiteAndAboutSectionsUseFocusedActionsInsteadOfRawLinkRows() {
        val detailUi = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val websiteButton = detailUi.substring(
            detailUi.indexOf("fun SteamStoreWebsiteButton"),
            detailUi.indexOf("fun PriceRow")
        )
        val aboutSection = detailUi.substring(
            detailUi.indexOf("fun DetailTextSection"),
            detailUi.indexOf("fun DetailLine")
        )

        assertTrue(detailUi.contains("SteamStoreWebsiteButton("))
        assertTrue(websiteButton.contains("FilledTonalButton("))
        assertTrue(websiteButton.contains("R.string.steam_store_website"))
        assertFalse(websiteButton.contains("Text(url"))
        assertTrue(aboutSection.contains("rememberSaveable(text)"))
        assertTrue(aboutSection.contains("maxLines = if (expanded) Int.MAX_VALUE else 6"))
        assertTrue(aboutSection.contains("R.string.steam_store_about_expand"))
        assertTrue(aboutSection.contains("R.string.steam_store_about_collapse"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
