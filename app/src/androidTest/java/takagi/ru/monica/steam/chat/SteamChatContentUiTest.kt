package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.friends.chat.info.domain.*
import takagi.ru.monica.steam.friends.chat.info.ui.steamSharedHistory
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.ui.LocalReduceAnimations

/** Production profile content with cached synthetic media, not a mock media wall. */
class SteamChatContentUiTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val selected=mutableStateOf(SteamChatHistoryCategory.MEDIA)
    private val opened=mutableListOf<String>()
    private val changes=mutableListOf<SteamChatHistoryCategory>()
    private val blue="https://avatars.fastly.steamstatic.com/chat-content-blue.png"
    private val secret="https://avatars.fastly.steamstatic.com/chat-content-secret.png"
    private val now=1795226400L
    private val entries by lazy { listOf(
        SteamChatHistoryClassifier.item("image","Alex","[img]$blue[/img]",now),
        SteamChatHistoryClassifier.item("video","Me","[video]https://cdn.akamai.steamstatic.com/chat/clip.mp4[/video]",now-60),
        SteamChatHistoryClassifier.item("spoiler","Alex","[spoiler][img]$secret[/img][/spoiler]",now-120),
        SteamChatHistoryClassifier.item("old-image","Alex","[img]$blue[/img]",now-40*86400),
        SteamChatHistoryClassifier.item("link","Alex","Portal guide https://steamcommunity.com/sharedfiles/filedetails/?id=123",now-180),
        SteamChatHistoryClassifier.item("file","Me","[url=https://steamcommunity.com/pack.zip]pack.zip[/url]",now-240),
        SteamChatHistoryClassifier.item("invite","Alex","steam://joinlobby/730/109775241234567890/76561198000000001",now-300)) }
    private fun mount(liquid:Boolean=true,dark:Boolean=true,large:Boolean=false,rtl:Boolean=false,reduced:Boolean=false,empty:Boolean=false,narrow:Boolean=false,inviteCount:Int=1) {
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(40,90,180))
        val paint=Paint().apply { color=android.graphics.Color.rgb(240,170,60) };canvas.drawCircle(40f,40f,18f,paint)
        for(url in listOf(blue,secret)) steamRemoteImageCacheFile(instrumentation.targetContext,url).apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        rule.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalSteamDesignStyle provides if(liquid) SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                LocalReduceAnimations provides reduced,LocalDensity provides Density(density.density,if(large)1.5f else 1f),
                LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface(Modifier.then(if(narrow) Modifier.width(220.dp) else Modifier)) { LazyColumn(Modifier.fillMaxSize().testTag("content-list")) {
                    val history = entries + (1 until inviteCount).map { index ->
                        SteamChatHistoryClassifier.item("invite-$index", "Alex",
                            "steam://joinlobby/730/109775241234567890/76561198000000001", now-300-index)
                    }
                    steamSharedHistory(if(empty) emptyList() else history,selected.value,{selected.value=it;changes+=it},{opened+=it})
                } }
            }
        } }
        rule.waitForIdle();rule.waitUntil(5000) { rule.activity.hasWindowFocus() };SystemClock.sleep(400)
    }
    private fun text(id:Int)=rule.activity.getString(id)
    private fun tab(id:Int)=rule.onNodeWithText(text(id))
    private fun capture(name:String):Bitmap {
        rule.waitForIdle()
        val frame=CountDownLatch(1)
        rule.runOnUiThread { rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};rule.activity.window.decorView.invalidate() }
        assertTrue(frame.await(5,TimeUnit.SECONDS))
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(rule.activity.getExternalFilesDir(null),"chat-content/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(rule.activity.filesDir,"chat-content/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        return bitmap
    }
    private fun gap() {
        val title=rule.onNodeWithTag("chat-content-title").fetchSemanticsNode().boundsInRoot
        val filters=rule.onNodeWithTag("chat-content-filters").fetchSemanticsNode().boundsInRoot
        assertTrue("Title and category strip must have a clear gap",filters.top-title.bottom>=12*rule.activity.resources.displayMetrics.density)
    }
    private fun origin()=IntArray(2).also {a->rule.runOnUiThread {rule.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(a)}}
    private inner class Pointer(start:Offset) {
        private val o=origin();private val down=SystemClock.uptimeMillis();private var p=start
        init {send(MotionEvent.ACTION_DOWN)}
        private fun send(action:Int) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p.x+o[0],p.y+o[1],0);e.source=InputDevice.SOURCE_TOUCHSCREEN
            try {check(instrumentation.uiAutomation.injectInputEvent(e,true))}finally {e.recycle()}}
        fun move(to:Offset) {val start=p;repeat(24) {n->SystemClock.sleep(16);p=start+(to-start)*((n+1)/24f);send(MotionEvent.ACTION_MOVE);rule.mainClock.advanceTimeBy(16)}}
        fun finish(cancel:Boolean=false) {SystemClock.sleep(80);send(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)}
    }
    /** Semantics still contain the numeral when a layer tint paints it over. Check actual pixels. */
    private fun assertBadgePixels(label:Int, count:String, dark:Boolean, held:Boolean, name:String) {
        val slot=tab(label).fetchSemanticsNode().boundsInRoot
        val digit=rule.onAllNodes(hasText(count,substring=false) and
            !hasAnyAncestor(SemanticsMatcher("hidden glass capture") { it.config.isClearingSemantics }),
            useUnmergedTree=true).fetchSemanticsNodes()
            .single { slot.contains(it.boundsInRoot.center) }.boundsInRoot
        // Content grows about the slot center. The larger lens samples this content in
        // screen coordinates; its shell scale must not be applied to the numeral again.
        val scale=if(held) 1.2f else 1f
        val center=slot.center+(digit.center-slot.center)*scale
        // Exclude line-height padding at the round badge's edges so the surrounding
        // dark/light page cannot be mistaken for contrasting numeral pixels.
        val region=Rect(center.x-digit.width*scale/2,center.y-digit.height*scale*.3f,
            center.x+digit.width*scale/2,center.y+digit.height*scale*.3f)
        val colors=if(dark) darkColorScheme() else lightColorScheme()
        val foreground=colors.onPrimary.toArgb(); val background=colors.primary.toArgb()
        fun distance(a:Int,b:Int)=kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+
            kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+
            kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
        val bitmap=capture(name); val o=origin();var ink=0;var fill=0
        try {
            for(y in region.top.toInt() until region.bottom.toInt()) for(x in region.left.toInt() until region.right.toInt()) {
                val pixel=bitmap.getPixel(x+o[0],y+o[1])
                // Allow the lens's 10% light/dark overlay (up to 77 total RGB distance),
                // while keeping foreground and background color neighborhoods disjoint.
                val inkDistance=distance(pixel,foreground)
                val fillDistance=distance(pixel,background)
                if(inkDistance<100 && inkDistance*2<fillDistance) ink++
                if(fillDistance<100 && fillDistance*2<inkDistance) fill++
            }
        } finally {bitmap.recycle()}
        assertTrue("$name: numeral must retain contrasting ink, actual=$ink",ink>=8*count.length)
        assertTrue("$name: badge must retain its colored fill, actual=$fill",fill>=20)
    }
    private fun verifySelectedBadges(liquid:Boolean,dark:Boolean,large:Boolean=false) {
        mount(liquid=liquid,dark=dark,large=large,inviteCount=12)
        val prefix="badge-${if(liquid) "glass" else "m3"}-${if(dark) "dark" else "light"}"
        for((label,count) in listOf(R.string.steam_history_media to "4",R.string.steam_history_links to "1",R.string.steam_history_invites to "12")) {
            tab(label).performScrollTo().performClick().assertIsSelected();rule.waitForIdle()
            assertBadgePixels(label,count,dark,false,"$prefix-$count-rest")
            if(liquid) {
                val pointer=Pointer(tab(label).fetchSemanticsNode().boundsInRoot.center)
                try {
                    rule.mainClock.advanceTimeBy(300)
                    assertBadgePixels(label,count,dark,true,"$prefix-$count-held")
                } finally {pointer.finish()}
                rule.waitForIdle()
                assertBadgePixels(label,count,dark,false,"$prefix-$count-released")
            }
        }
    }
    @Test fun glassDarkSelectedCountsRemainVisibleAtRestHeldAndReleased()=verifySelectedBadges(true,true)
    @Test fun glassLightSelectedCountsRemainVisibleAtRestHeldAndReleased()=verifySelectedBadges(true,false)
    @Test fun m3DarkSelectedCountsRemainVisible()=verifySelectedBadges(false,true)
    @Test fun m3LightLargeSelectedCountsRemainVisible()=verifySelectedBadges(false,false,true)
    @Test fun glassTitleGapMediaGridAndHeldLensRender() {
        mount();gap();rule.onNodeWithText(text(R.string.steam_thread_shared_content)).assertIsDisplayed()
        val filters=rule.onNodeWithTag("chat-content-filters").fetchSemanticsNode().boundsInRoot
        val last=tab(R.string.steam_history_invites).fetchSemanticsNode().boundsInRoot
        assertTrue("Filter shell must end just after the last label",filters.right-last.right<=20*rule.activity.resources.displayMetrics.density)
        val node=tab(R.string.steam_history_media).fetchSemanticsNode().boundsInRoot
        val before=capture("glass-rest");val pointer=Pointer(node.center)
        try {
            rule.mainClock.advanceTimeBy(240);val held=capture("glass-held");val o=origin();var raised=0
            for(x in node.left.toInt()..node.right.toInt()) for(y in (node.top-16).toInt() until node.top.toInt()) {
                val a=before.getPixel(x+o[0],y+o[1]);val b=held.getPixel(x+o[0],y+o[1])
                val difference = kotlin.math.abs(android.graphics.Color.red(b)-android.graphics.Color.red(a)) +
                    kotlin.math.abs(android.graphics.Color.green(b)-android.graphics.Color.green(a)) +
                    kotlin.math.abs(android.graphics.Color.blue(b)-android.graphics.Color.blue(a))
                if(difference>20)raised++
            }
            held.recycle();assertTrue("Glass must lift above the category slot: $raised",raised>30)
        }finally {pointer.finish();before.recycle()}
        rule.waitForIdle();capture("glass-released").recycle()
        rule.onNodeWithTag("chat-content-media-image").assertIsDisplayed()
        rule.onNodeWithTag("chat-content-media-video").assertIsDisplayed()
    }
    @Test fun mediaImageIsRealVideoIsTypedAndSpoilerStaysCovered() {
        mount()
        val bounds=rule.onNodeWithTag("chat-content-media-image").fetchSemanticsNode().boundsInRoot;val o=origin()
        var found=false
        rule.waitUntil(5000) {
            val im=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val c=im.getPixel((bounds.left+bounds.width*.2f).toInt()+o[0],(bounds.top+bounds.height*.2f).toInt()+o[1]);im.recycle()
            found=android.graphics.Color.blue(c)>150 && android.graphics.Color.red(c)<80;found
        }
        assertTrue(found)
        rule.onNodeWithContentDescription(text(R.string.steam_chat_content_video),useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.steam_chat_content_spoiler),useUnmergedTree=true).assertIsDisplayed()
        rule.onNodeWithTag("chat-content-media-image").performClick();assertEquals(listOf("image"),opened)
        rule.onNodeWithTag("chat-content-media-video").performClick();assertEquals(listOf("image","video"),opened)
        capture("media-types").recycle()
    }
    @Test fun dragCommitsOnReleaseCancelRestoresAndRowsOpenOriginalMessages() {
        mount()
        val from=tab(R.string.steam_history_media).fetchSemanticsNode().boundsInRoot.center
        val to=tab(R.string.steam_history_files).fetchSemanticsNode().boundsInRoot.center
        Pointer(from).apply {move(to);assertEquals(SteamChatHistoryCategory.MEDIA,rule.runOnUiThread {selected.value});finish()}
        rule.waitForIdle();tab(R.string.steam_history_files).assertIsSelected()
        rule.onNodeWithTag("chat-content-row-file").performClick();assertEquals(listOf("file"),opened)
        Pointer(to).apply {move(from);finish(true)};rule.waitForIdle();tab(R.string.steam_history_files).assertIsSelected()
        tab(R.string.steam_history_links).performScrollTo().performClick()
        rule.onNodeWithText("steamcommunity.com").assertIsDisplayed()
        rule.onNodeWithTag("chat-content-row-link").performClick();assertEquals(listOf("file","link"),opened)
        capture("links").recycle()
    }
    @Test fun m3eLightLargeTextKeepsGapRowsAndIndependentScroll() {
        mount(liquid=false,dark=false,large=true);gap();capture("m3-light-large-media").recycle()
        tab(R.string.steam_history_files).performScrollTo().performClick()
        rule.onNodeWithTag("chat-content-row-file").assertIsDisplayed()
        rule.onNodeWithText("pack.zip").assertIsDisplayed()
        capture("m3-light-large-file").recycle()
    }
    @Test fun compactFiltersKeepLastCategoryReachableOnNarrowLargeText() {
        mount(liquid=false,dark=false,large=true,narrow=true)
        tab(R.string.steam_history_invites).performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("chat-content-row-invite").assertIsDisplayed()
        capture("compact-narrow-large").recycle()
    }
    @Test fun rtlReducedMotionEmptyStateUsesChatContentTitle() {
        mount(rtl=true,reduced=true,empty=true);gap()
        rule.onNodeWithText(text(R.string.steam_thread_no_shared_content)).assertIsDisplayed()
        assertEquals("Chat content",text(R.string.steam_thread_shared_content));capture("empty-rtl").recycle()
    }
    @Test fun rtlReducedMotionDragStillCommitsAndCancels() {
        mount(rtl=true,reduced=true)
        val from=tab(R.string.steam_history_media).fetchSemanticsNode().boundsInRoot.center
        val to=tab(R.string.steam_history_files).fetchSemanticsNode().boundsInRoot.center
        Pointer(from).apply {move(to);assertEquals(SteamChatHistoryCategory.MEDIA,rule.runOnUiThread {selected.value});finish()}
        rule.waitForIdle();tab(R.string.steam_history_files).assertIsSelected()
        Pointer(to).apply {move(from);finish(true)};rule.waitForIdle();tab(R.string.steam_history_files).assertIsSelected()
    }
    @Test fun titlesAndMediaTypesResolveInAllSupportedLanguages() {
        val expected=mapOf("en" to "Chat content","zh" to "聊天内容","ja" to "チャット内容","vi" to "Nội dung trò chuyện","ru" to "Содержимое чата")
        expected.forEach {(tag,title)->
            val config=android.content.res.Configuration(instrumentation.targetContext.resources.configuration);config.setLocale(Locale.forLanguageTag(tag))
            val ctx=instrumentation.targetContext.createConfigurationContext(config)
            assertEquals(title,ctx.getString(R.string.steam_thread_shared_content))
            assertTrue(ctx.getString(R.string.steam_chat_content_video).isNotBlank())
        }
    }
}
