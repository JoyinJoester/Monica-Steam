package takagi.ru.monica.steam.quickaccess

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.After
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import takagi.ru.monica.steam.foundation.ui.readCachedSteamAvatarBitmap
import takagi.ru.monica.steam.foundation.ui.refreshSteamAvatarBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.*
import takagi.ru.monica.steam.importer.SteamMaFilePayload
import takagi.ru.monica.steam.library.*
import takagi.ru.monica.steam.profile.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SteamWidgetInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val prefix = "widget_test_${UUID.randomUUID()}_"
    private val root = File(base.cacheDir, prefix).apply { mkdirs() }
    private val prefNames = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getApplicationInfo() = android.content.pm.ApplicationInfo(base.applicationInfo).apply { dataDir = root.path }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            prefNames.add(prefix + name)
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    @After fun cleanFixture() {
        prefNames.forEach(base::deleteSharedPreferences)
        check(root.parentFile == base.cacheDir && root.name.startsWith("widget_test_"))
        root.deleteRecursively()
    }

    @Test
    fun configuredMdbxAccountSurvivesWidgetRefreshLookup() {
        // MDBX uses stable negative runtime IDs in both the app and library cache.
        SteamWidgetPreferences.setAccountId(context, 42, -91234L)
        assertEquals(-91234L, SteamWidgetPreferences.accountId(context, 42))
    }

    @Test
    fun localAccountBindingRemainsIndependentPerWidget() {
        SteamWidgetPreferences.setAccountId(context, 42, 7L)
        SteamWidgetPreferences.setAccountId(context, 43, 9L)
        assertEquals(7L, SteamWidgetPreferences.accountId(context, 42))
        assertEquals(9L, SteamWidgetPreferences.accountId(context, 43))
    }

    @Test fun roomAndMdbxEncryptedLibraryCachesRenderWithoutNetworkImages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        try {
            val security = SecurityManager(context)
            val accounts = SteamAccountRepository(db.steamAccountDao(), security)
            val cache = SteamLibraryCacheRepository(context, db.steamLibraryCacheDao(), security)
            val localId = accounts.upsertFromMaFile(SteamMaFilePayload(
                "76561198000000000", "widget-fixture", "Alex", "synthetic-device", "synthetic-secret",
                null, null, null, null, null, null, "{}"
            ))
            val localAccount = requireNotNull(accounts.getAccount(localId))
            val avatarFile = File(context.cacheDir, "steam_avatars/${localAccount.steamId}.png").apply { parentFile!!.mkdirs() }
            InstrumentationRegistry.getInstrumentation().context.assets.open("widgets/steam-avatar.jpg").use { avatarFile.writeBytes(it.readBytes()) }
            val games = listOf(
                SteamGame(1, "Older but longer", 600, 500, lastPlayedAt = 100),
                SteamGame(2, "Most recent", 120, 20, lastPlayedAt = 200,
                    headerImageUrl = "https://shared.fastly.steamstatic.com/widget-test-missing-${UUID.randomUUID()}.jpg"),
                SteamGame(3, "Never played", 0, 0)
            )
            for (id in listOf(localId, -91234L)) {
                val library = SteamLibrarySnapshot(id, games, 123456L, currency = "CNY", inventoryItemCount = 8)
                cache.saveLibrary(library)
                SteamWidgetPreferences.setAccountId(context, 44, id, if (id < 0) 77L else null)
                val start = android.os.SystemClock.elapsedRealtime()
                val snapshot = requireNotNull(SteamWidgetDataLoader.loadCached(
                    accountId = requireNotNull(SteamWidgetPreferences.accountId(context, 44)),
                    readAccount = { if (it > 0) accounts.getAccount(it) else localAccount.copy(id = it) },
                    readLibrary = cache::getLibrary,
                    readDecor = { null },
                    readImage = SteamRemoteImageCache.get(context)::loadCached,
                    readAvatar = { readCachedSteamAvatarBitmap(context, it) }
                ))
                assertTrue("Cache read must not wait for CDN", android.os.SystemClock.elapsedRealtime() - start < 2000)
                assertEquals(720L, snapshot.totalPlaytimeMinutes)
                assertEquals(8, snapshot.inventoryCount)
                assertNull("Unpriced library is unknown, not free", snapshot.valueMinor)
                assertEquals(listOf("Most recent", "Older but longer"), snapshot.games.map { it.name })
                assertNull(snapshot.games.first().image)
                assertNotNull("Reuse the exact avatar cache used by the app", snapshot.avatar)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val view = SteamWidgetRenderer.accountStats(context, 44, snapshot).apply(context, FrameLayout(context))
                    assertEquals("12 h", view.findViewById<TextView>(R.id.steam_account_stats_playtime).text.toString())
                    assertEquals("8", view.findViewById<TextView>(R.id.steam_account_stats_inventory).text.toString())
                }
            }
        } finally { db.close() }
    }

    @Test fun pinnedWidgetReceivesSelectedAccountFromLauncherCallback() {
        val host = android.appwidget.AppWidgetHost(base, 713)
        val widgetId = host.allocateAppWidgetId()
        try {
            val manager = android.appwidget.AppWidgetManager.getInstance(base)
            assertTrue("Test host needs appwidget grantbind", manager.bindAppWidgetIdIfAllowed(widgetId,
                android.content.ComponentName(base, SteamAccountStatsWidgetProvider::class.java)))
            val callback = SteamWidgetPinReceiver.callback(base, SteamAccountStatsWidgetProvider::class.java.name, -812L, 99L)
            callback.send(base, 0, Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            val deadline = android.os.SystemClock.elapsedRealtime() + 3000
            while (SteamWidgetPreferences.accountId(base, widgetId) == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(-812L, SteamWidgetPreferences.accountId(base, widgetId))
            assertEquals(99L, SteamWidgetPreferences.databaseId(base, widgetId))
        } finally {
            host.deleteAppWidgetId(widgetId)
            SteamWidgetPreferences.remove(base, widgetId)
        }
    }

    @Test fun missingLibraryAndStaleOnlineStatusStayHonest() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        try {
            val repo = SteamAccountRepository(db.steamAccountDao(), SecurityManager(context))
            val id = repo.upsertFromMaFile(SteamMaFilePayload("76561198000000001", "alex", "Alex", "device", "secret",
                null, null, null, null, null, null, "{}"))
            val snapshot = requireNotNull(SteamWidgetDataLoader.loadCached(id, repo::getAccount, { null }, { null }, { null }))
            assertNull(snapshot.totalPlaytimeMinutes)
            assertNull(snapshot.inventoryCount)
            assertNull(snapshot.valueMinor)
            assertTrue(snapshot.games.isEmpty())
            val steamId = "76561198000000002"
            // Use the repository's shared storage, restoring it afterwards.
            val prefs = base.getSharedPreferences("steam_mini_profile_decor_metadata", Context.MODE_PRIVATE)
            val keys = listOf("${steamId}_fetched_at", "${steamId}_persona_name", "${steamId}_current_game_name")
            val previous = prefs.all.filterKeys { it in keys }
            try {
                prefs.edit().putLong(keys[0], System.currentTimeMillis() - 16 * 60_000L)
                    .putString(keys[1], "Alex").putString(keys[2], "Stale game").commit()
                val decor = SteamMiniProfileDecorRepository.get(base).loadCached(steamId)
                assertNotNull(decor)
                assertNull(decor?.currentGameName)
            } finally {
                prefs.edit().apply {
                    keys.forEach(::remove)
                    previous.forEach { (key, value) -> when (value) { is String -> putString(key,value); is Long -> putLong(key,value) } }
                }.commit()
            }
        } finally { db.close() }
    }

    @Test fun remoteViewsRenderAtSupportedSizesInLightAndDark() {
        val screenshotDir = File(base.getExternalFilesDir(null), "widget-render-tests").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (dark in listOf(false, true)) for (scale in listOf(1f, 1.5f)) {
                val config = Configuration(base.resources.configuration).apply {
                    fontScale = scale
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
                }
                val themed = base.createConfigurationContext(config)
                fun artwork(file: String) = InstrumentationRegistry.getInstrumentation().context.assets.open("widgets/$file").use { BitmapFactory.decodeStream(it) }
                val first = SteamWidgetGame("Hollow Knight", 720, artwork("hollow-knight.jpg"), true)
                val second = SteamWidgetGame("Stardew Valley", 480, artwork("stardew-valley.jpg"), false)
                val sample = SteamWidgetSnapshot("Steam 玩家", artwork("steam-avatar.jpg"), 72000, 128, 328000, "CNY", listOf(first, second), first, System.currentTimeMillis())
                val density = themed.resources.displayMetrics.density
                fun pixels(dp: Int) = (dp * density).toInt()
                val sheet = Bitmap.createBitmap(pixels(412), pixels(1072), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(if (dark) android.graphics.Color.rgb(20,18,24) else android.graphics.Color.rgb(235,229,240))
                fun draw(remote: android.widget.RemoteViews, y: Int, width: Int, height: Int): View {
                    val view = remote.apply(themed, FrameLayout(themed))
                    view.measure(View.MeasureSpec.makeMeasureSpec(pixels(width), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(pixels(height), View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, pixels(width), pixels(height))
                    canvas.save(); canvas.translate(pixels(16).toFloat(), pixels(y).toFloat()); view.draw(canvas); canvas.restore()
                    return view
                }
                val stats = draw(SteamWidgetRenderer.accountStats(themed, 12345, sample), 16, 380, 152)
                assertEquals("1.2k h", stats.findViewById<TextView>(R.id.steam_account_stats_playtime).text.toString())
                val recent = draw(SteamWidgetRenderer.recentGames(themed, 12345, sample, true), 184, 380, 200)
                assertEquals(View.VISIBLE, recent.findViewById<View>(R.id.steam_recent_game_row_two).visibility)
                val compact = draw(SteamWidgetRenderer.recentGames(themed, 12345, sample, false), 400, 260, 132)
                assertEquals(View.GONE, compact.findViewById<View>(R.id.steam_recent_game_row_two).visibility)
                val narrow = draw(SteamWidgetRenderer.recentGames(themed, 12345, sample, false, narrow = true), 716, 180, 132)
                val smallStats = draw(SteamWidgetRenderer.accountStats(themed, 12345, sample), 864, 250, 152)
                val empty = draw(SteamWidgetRenderer.accountStats(themed, 12345, null), 548, 380, 152)
                assertEquals(themed.getString(R.string.steam_widget_choose_account), empty.findViewById<TextView>(R.id.steam_account_stats_name).text.toString())
                assertTrue(empty.hasOnClickListeners())
                for (root in listOf(stats, recent, compact, empty, narrow, smallStats)) assertContentFits(root as android.view.ViewGroup)
                val compactTitle = compact.findViewById<TextView>(R.id.steam_recent_game_name_one)
                assertTrue("Game title must be fully visible", (0 until compactTitle.layout.lineCount).all { compactTitle.layout.getEllipsisCount(it) == 0 })
                fun radii(view: View) = (view.background as android.graphics.drawable.GradientDrawable).cornerRadii!!
                val start = radii(stats.findViewById<TextView>(R.id.steam_account_stats_playtime).parent as View)
                val end = radii(stats.findViewById<TextView>(R.id.steam_account_stats_value).parent as View)
                assertTrue("Horizontal outside edge must be rounder than the joint", start[0] > start[2] && end[0] < end[2])
                val top = radii(recent.findViewById(R.id.steam_recent_game_row_one))
                val bottom = radii(recent.findViewById(R.id.steam_recent_game_row_two))
                assertTrue("Vertical joint must have small corners", top[0] > top[6] && bottom[0] < bottom[6])
                val single = compact.findViewById<View>(R.id.steam_recent_game_row_one).background as android.graphics.drawable.GradientDrawable
                assertEquals("Single row retains four large corners", 20 * density, single.cornerRadius, 0.1f)
                File(screenshotDir, (if (dark) "dark" else "light") + "-$scale.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun statsFitsLargeFontWithoutClippingTilesOrLabels() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val themed = base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = 1.5f })
            val density = themed.resources.displayMetrics.density
            val root = SteamWidgetRenderer.accountStats(themed, 12345,
                SteamWidgetSnapshot("Alex", null, 72000, 128, 328000, "CNY", emptyList(), null, 123456L))
                .apply(themed, FrameLayout(themed)) as android.view.ViewGroup
            root.measure(View.MeasureSpec.makeMeasureSpec((380 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((152 * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            assertContentFits(root)
        }
    }

    private fun assertContentFits(root: android.view.ViewGroup) {
        fun checkFits(view: View) {
            if (view.visibility != View.VISIBLE) return
            val rect = android.graphics.Rect(0, 0, view.width, view.height)
            root.offsetDescendantRectToMyCoords(view, rect)
            assertTrue("View ${view.id} extends below widget content: ${rect.bottom}", rect.bottom <= root.height - root.paddingBottom)
            assertTrue("View ${view.id} extends above widget content", rect.top >= root.paddingTop)
            assertTrue("View ${view.id} extends outside widget width", rect.left >= root.paddingLeft && rect.right <= root.width - root.paddingRight)
            if (view is TextView) assertTrue("Text ${view.id} clips vertically: ${view.layout.height} > ${view.height - view.totalPaddingTop - view.totalPaddingBottom}",
                view.layout.height <= view.height - view.totalPaddingTop - view.totalPaddingBottom)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) checkFits(view.getChildAt(i))
        }
        for (i in 0 until root.childCount) checkFits(root.getChildAt(i))
    }

    @Test fun fetchesRealSteamAvatarAndCoverWhenNetworkSmokeIsRequested() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("widgetNetworkSmoke") == "true")
        val avatar = refreshSteamAvatarBitmap(context, "76561197960287930")
        assertNotNull("Live Steam avatar should load using the app path", avatar)
        val cover = SteamRemoteImageCache.get(context).load("https://cdn.akamai.steamstatic.com/steam/apps/367520/header.jpg")
        assertNotNull("Live Steam game cover should load", cover)
    }
}
