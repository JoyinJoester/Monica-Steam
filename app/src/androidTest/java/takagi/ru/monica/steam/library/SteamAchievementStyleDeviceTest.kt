package takagi.ru.monica.steam.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.SteamDockStyle
import takagi.ru.monica.steam.navigation.liquidglass.ui.LocalSteamDesignStyle
import takagi.ru.monica.steam.profile.SteamRemoteImageCache
import takagi.ru.monica.steam.store.ui.SteamGamePersonalStats
import takagi.ru.monica.steam.store.ui.SteamGamePersonalStatsContent
import takagi.ru.monica.ui.LocalReduceAnimations

/** Public image GET only; all player status/account data is synthetic. */
class SteamAchievementStyleDeviceTest {
    @get:Rule val rule=createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val icon="https://shared.akamai.steamstatic.com/community_assets/images/apps/714010/test-achievement-blue.png"
    private val publicGray="https://shared.akamai.steamstatic.com/community_assets/images/apps/714010/936ca407dd5b223be31ae010b32eec08987ef3c6.jpg"
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it.toInt() and 255)}
    private fun cached(url:String)=File(instrumentation.targetContext.cacheDir,"steam_library_images/${sha(url.toByteArray())}.img")
    private fun text(id:Int)=rule.activity.getString(id)
    private fun choice(name:String)=rule.onNode(hasText(name,substring=true) and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
    private fun status(id:Int)=rule.onNode(hasText(text(id),substring=false) and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected))
    private fun mount(liquid:Boolean=true,large:Boolean=false,rtl:Boolean=false,reduced:Boolean=false) {
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(30,110,190));val paint=Paint().apply {color=android.graphics.Color.rgb(240,180,40)};canvas.drawCircle(40f,40f,20f,paint)
        cached(icon).apply {parentFile!!.mkdirs()}.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val base=SteamAchievement("founder","Founder","Aim Lab Founder",false,null,
            "8d753512e8a958e2725a69b5b823c00cad0fceef.jpg","936ca407dd5b223be31ae010b32eec08987ef3c6.jpg")
        val entries=listOf(base,
            base.copy(apiName="done",displayName="Early Access User",description="A completed achievement",achieved=true,unlockTimeSeconds=1795226400,iconUrl=icon,lockedIconUrl=null),
            base.copy(apiName="legacy",displayName="Legacy cached icon",description="Normal image fallback",iconUrl="https://community.cloudflare.steamstatic.com/steamcommunity/public/images/apps/714010/test-achievement-blue.png",lockedIconUrl=null),
            base.copy(apiName="hidden",displayName="Secret title",description="Secret description",hidden=true,iconUrl=icon,lockedIconUrl=icon),
            base.copy(apiName="dlc",displayName="Expansion task",description="A collection-specific achievement",groupId=7,iconUrl=icon,lockedIconUrl=null))
        val data=SteamGameAchievements(1,714010,"Aim Lab",entries,0,listOf(SteamAchievementSet(0,"Base"),SteamAchievementSet(7,"Expansion",dlcAppId=1)))
        rule.setContent {MaterialTheme(colorScheme=if(liquid)darkColorScheme() else lightColorScheme()) {
            val d=LocalDensity.current
            CompositionLocalProvider(LocalSteamDesignStyle provides if(liquid)SteamDockStyle.LIQUID_GLASS else SteamDockStyle.FIXED,
                LocalDensity provides Density(d.density,if(large)1.5f else 1f),LocalLayoutDirection provides if(rtl)LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalReduceAnimations provides reduced) {
                SteamGamePersonalStatsContent("Aim Lab","Fixture player",SteamGamePersonalStats(game=SteamGame(714010,"Aim Lab",120,30),achievements=data,loading=false),
                    onChooseAccount={},onRetry={},onOpenGameData=null,onOpenScreenshots=null)
            }
        }}
        rule.waitForIdle();rule.waitUntil(5000) {rule.activity.hasWindowFocus()}
        rule.onNodeWithTag("game_personal_list").performScrollToKey("sets");rule.waitForIdle()
    }
    private fun origin()=IntArray(2).also {a->rule.runOnUiThread {rule.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(a)}}
    private inner class Pointer(start:Offset) {
        private val origin=origin();private val down=SystemClock.uptimeMillis();private var p=start
        init {send(MotionEvent.ACTION_DOWN)}
        private fun send(action:Int) {val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p.x+origin[0],p.y+origin[1],0);e.source=InputDevice.SOURCE_TOUCHSCREEN
            try {check(instrumentation.uiAutomation.injectInputEvent(e,true))}finally {e.recycle()}}
        fun move(to:Offset) {val from=p;repeat(24) {n->SystemClock.sleep(16);p=from+(to-from)*((n+1)/24f);send(MotionEvent.ACTION_MOVE);rule.mainClock.advanceTimeBy(16)}}
        fun finish(cancel:Boolean=false) {SystemClock.sleep(80);send(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)}
    }
    private fun capture(name:String):Bitmap {
        rule.waitForIdle();val frame=CountDownLatch(1)
        rule.runOnUiThread {rule.activity.window.decorView.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};rule.activity.window.decorView.invalidate()}
        assertTrue(frame.await(5,TimeUnit.SECONDS));val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(rule.activity.getExternalFilesDir(null),"achievement-style/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};return bitmap
    }
    private fun assertBlue(id:String) {
        val node=rule.onNodeWithTag(id).performScrollTo();rule.waitForIdle();val bounds=node.fetchSemanticsNode().boundsInRoot;val o=origin()
        rule.waitUntil(5000) {val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot());val p=bitmap.getPixel((bounds.left+bounds.width*.2f).toInt()+o[0],(bounds.top+bounds.height*.2f).toInt()+o[1]);bitmap.recycle()
            android.graphics.Color.blue(p)>160 && android.graphics.Color.red(p)<80}
    }
    @Test fun publicAimLabImageReallyDownloadsFromTheNewRoute() = runBlocking {
        val file=cached(publicGray);file.delete()
        val bitmap=SteamRemoteImageCache.get(instrumentation.targetContext).load(publicGray)
        assertNotNull("Public achievement JPEG must load through production Android HTTP/cache",bitmap)
        assertEquals("76a5b8c9a955fc0ff445a79dc4ea51d6f540af3fdc116cf6a143062852c14cd1",sha(file.readBytes()))
        assertEquals(64,requireNotNull(bitmap).width)
    }
    @Test fun titleHasClearSpacingAndBothGlassSelectorsLift() {
        mount()
        val title=rule.onNodeWithTag("achievement-sets-title").fetchSemanticsNode().boundsInRoot
        val filters=rule.onNodeWithTag("achievement-set-filters").fetchSemanticsNode().boundsInRoot
        assertTrue(filters.top-title.bottom>=12*rule.activity.resources.displayMetrics.density)
        val lastSet=choice("Expansion").fetchSemanticsNode().boundsInRoot
        val statuses=rule.onNodeWithTag("achievement-status-filters").fetchSemanticsNode().boundsInRoot
        val lastStatus=status(R.string.steam_game_stats_incomplete).fetchSemanticsNode().boundsInRoot
        val density=rule.activity.resources.displayMetrics.density
        assertTrue("Set shell must fit its labels",filters.right-lastSet.right<=20*density)
        assertTrue("Status shell must fit its labels",statuses.right-lastStatus.right<=20*density)
        val readable=capture("dark-text");val screenOrigin=origin()
        for (bounds in listOf(title,rule.onNodeWithTag("achievement_title_0_done").fetchSemanticsNode().boundsInRoot)) {
            var bright=0
            for(x in bounds.left.toInt() until bounds.right.toInt()) for(y in bounds.top.toInt() until bounds.bottom.toInt()) {
                val p=readable.getPixel(x+screenOrigin[0],y+screenOrigin[1])
                if(android.graphics.Color.red(p)>160 && android.graphics.Color.green(p)>160 && android.graphics.Color.blue(p)>160)bright++
            }
            assertTrue("Dark glass titles must retain readable theme text: $bright",bright>30)
        };readable.recycle()
        for ((name,tab) in listOf("collection" to choice(text(R.string.steam_achievement_sets_all)),"status" to status(R.string.steam_game_stats_all))) {
            val bounds=tab.fetchSemanticsNode().boundsInRoot;val before=capture("$name-rest");val pointer=Pointer(bounds.center)
            try {rule.mainClock.advanceTimeBy(240);val held=capture("$name-held");val o=origin();var changed=0
                for(x in bounds.left.toInt()..bounds.right.toInt()) for(y in (bounds.top-16).toInt() until bounds.top.toInt()) {
                    val a=before.getPixel(x+o[0],y+o[1]);val b=held.getPixel(x+o[0],y+o[1]);val diff=kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
                    if(diff>20)changed++
                };held.recycle();assertTrue("$name must lift above its resting tab: $changed",changed>30)
            } finally {pointer.finish();before.recycle()}
            rule.waitForIdle();capture("$name-released").recycle()
        }
    }
    @Test fun collectionAndCompletionComposeAndCancelRestores() {
        mount();choice("Expansion").performClick()
        rule.onNodeWithTag("achievement_row_7_dlc").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("achievement_row_0_done").assertDoesNotExist()
        rule.onNodeWithTag("game_personal_list").performScrollToKey("filters")
        val from=status(R.string.steam_game_stats_all).fetchSemanticsNode().boundsInRoot.center
        val to=status(R.string.steam_game_stats_completed).fetchSemanticsNode().boundsInRoot.center
        Pointer(from).apply {move(to);status(R.string.steam_game_stats_all).assertIsSelected();finish()};rule.waitForIdle()
        status(R.string.steam_game_stats_completed).assertIsSelected();rule.onNodeWithTag("achievement_row_7_dlc").assertDoesNotExist()
        Pointer(to).apply {move(from);finish(true)};rule.waitForIdle();status(R.string.steam_game_stats_completed).assertIsSelected()
        rule.onNodeWithTag("game_personal_list").performScrollToKey("sets");choice("Base").performClick()
        assertBlue("achievement_icon_0_done");capture("filtered-completed").recycle()
    }
    @Test fun m3eLargeTextAndHiddenAchievementsStayReadableAndConcealed() {
        mount(liquid=false,large=true);capture("m3-large").recycle()
        rule.onNodeWithTag("achievement_row_0_hidden").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.steam_achievement_hidden)).assertIsDisplayed()
        rule.onNodeWithText("Secret title",useUnmergedTree=true).assertDoesNotExist();rule.onNodeWithText("Secret description",useUnmergedTree=true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Secret title",useUnmergedTree=true).assertDoesNotExist();capture("hidden").recycle()
    }
    @Test fun legacyCachedUrlAndMissingGrayImageRenderInRtlReducedMotion() {
        mount(rtl=true,reduced=true);assertBlue("achievement_icon_0_legacy")
        rule.onNodeWithTag("game_personal_list").performScrollToKey("sets");choice("Expansion").performClick()
        assertBlue("achievement_icon_7_dlc");capture("rtl-reduced").recycle()
    }
    @Test fun setLabelsResolveInEverySupportedLanguage() {
        val expected=mapOf("en" to "Achievement sets","zh" to "成就集合","ja" to "実績セット","vi" to "Bộ thành tích","ru" to "Наборы достижений")
        expected.forEach {(tag,title)->val cfg=android.content.res.Configuration(instrumentation.targetContext.resources.configuration);cfg.setLocale(Locale.forLanguageTag(tag));val ctx=instrumentation.targetContext.createConfigurationContext(cfg)
            assertEquals(title,ctx.getString(R.string.steam_achievement_sets));assertTrue(ctx.getString(R.string.steam_achievement_hidden).isNotBlank())}
    }
}
