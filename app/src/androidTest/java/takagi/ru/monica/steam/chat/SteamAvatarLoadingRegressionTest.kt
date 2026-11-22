package takagi.ru.monica.steam.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monica.telegramchat.SteamAvatarView
import com.monica.telegramchat.upstream.DialogCell
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.TelegramSocialFixtureActivity
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationActions
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationListEntry
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationRow
import takagi.ru.monica.steam.friends.chat.ui.SteamConversationType
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.rememberSteamGroupAvatarBitmap
import takagi.ru.monica.ui.theme.MonicaTheme

/** Real asynchronous cache reads and bitmap decoding, using only owned synthetic images.
 * These tests do not simulate delayed network responses or prove network cancellation timing.
 */
@RunWith(AndroidJUnit4::class)
class SteamAvatarLoadingRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: TelegramSocialFixtureActivity? = null
    private val ownedCacheFiles = mutableListOf<File>()
    private val photos = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = check() }
            if (ready) return
            Thread.sleep(20)
        }
        var ready = false
        main { ready = check() }
        assertTrue("Synthetic avatar did not reach the expected state", ready)
    }

    private fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, TelegramSocialFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("route", "conversations")
        ) as TelegramSocialFixtureActivity
        await { activity!!.hasWindowFocus() }
    }

    @After fun close() {
        main { activity?.finish() }
        instrumentation.waitForIdleSync()
        ownedCacheFiles.forEach { file -> assertTrue("Remove only this test's cache entry", !file.exists() || file.delete()) }
    }

    private fun cachedPhoto(color: Int): String {
        // Use the production URL normalization and cache key; a fresh timestamp selects the
        // cache-hit branch before any HTTP request. UUID paths cannot overwrite another test.
        val url = "https://avatars.fastly.steamstatic.com/monica-avatar-test-${UUID.randomUUID()}.png"
        val destination = steamRemoteImageCacheFile(instrumentation.targetContext, url)
        check(!destination.exists())
        destination.parentFile!!.mkdirs()
        ownedCacheFiles += destination
        writePhoto(destination, color)
        return url
    }

    private fun writePhoto(destination: File, color: Int) {
        check(destination in ownedCacheFiles)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            destination.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        assertTrue(destination.setLastModified(System.currentTimeMillis()))
    }

    private fun member(id: String, url: String) = SteamFriend(
        id, SteamFriendRelationship.FRIEND, personaName = id, avatarUrl = url
    )

    private fun hasQuadrants(bitmap: Bitmap?, colors: List<Int>): Boolean {
        if (bitmap == null) return false
        val points = listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3)
        return points.indices.all { index ->
            val (x, y) = points[index]
            bitmap.getPixel(bitmap.width * x / 4, bitmap.height * y / 4) == colors[index]
        }
    }

    @Test fun cachedGroupPhotosPublishTogetherAndSurvivePresenceUpdates() {
        val members = mutableStateOf(photos.mapIndexed { index, color -> member("cached-$index", cachedPhoto(color)) })
        val seen = mutableListOf<Bitmap>()
        var current: Bitmap? = null
        var revisions = 0
        launch()
        main {
            activity!!.setContent {
                MonicaTheme {
                    val bitmap = rememberSteamGroupAvatarBitmap(members.value, "Cached group", 56.dp)
                    SideEffect {
                        current = bitmap
                        revisions++
                        if (seen.lastOrNull() !== bitmap) seen += bitmap
                    }
                }
            }
        }
        await { hasQuadrants(current, photos) }
        var loaded: Bitmap? = null
        var previousRevision = 0
        main {
            assertEquals("One initial disc and one completed composite, without partial-photo publications", 2, seen.size)
            assertFalse(hasQuadrants(seen.first(), photos))
            loaded = current
            previousRevision = revisions
            members.value = members.value.map { it.copy(gameName = "Portal 2") }
        }
        await { revisions > previousRevision }
        main {
            assertSame("Presence changes keep the already decoded composite", loaded, current)
            assertEquals(2, seen.size)
            assertTrue(hasQuadrants(current, photos))
        }
    }

    @Test fun replacingMembersInTheSameCompositionPublishesTheirOwnDiscAndPhotos() {
        val urls = photos.map(::cachedPhoto)
        val members = mutableStateOf(urls.mapIndexed { index, url -> member("old-$index", url) })
        val newDiscs = mutableListOf<Bitmap>()
        var current: Bitmap? = null
        launch()
        main {
            activity!!.setContent {
                MonicaTheme {
                    val group = members.value
                    val bitmap = rememberSteamGroupAvatarBitmap(group, "Cached group", 56.dp)
                    SideEffect {
                        current = bitmap
                        if (group.first().steamId.startsWith("new-") && newDiscs.lastOrNull() !== bitmap) newDiscs += bitmap
                    }
                }
            }
        }
        await { hasQuadrants(current, photos) }
        var previous: Bitmap? = null
        main {
            previous = current
            members.value = urls.reversed().mapIndexed { index, url -> member("new-$index", url) }
        }
        await { hasQuadrants(current, photos.reversed()) }
        main {
            assertEquals("Replacement exposes only its placeholder and completed photo disc", 2, newDiscs.size)
            assertNotSame("The old group's image must not be the new group's placeholder", previous, newDiscs.first())
            assertFalse("The replacement placeholder must not display the old photos", hasQuadrants(newDiscs.first(), photos))
            assertTrue(hasQuadrants(newDiscs.last(), photos.reversed()))
        }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun row(): DialogCell? = activity?.window?.decorView?.let(::descendants)
        ?.filterIsInstance<DialogCell>()?.singleOrNull()

    private fun avatarColor(cell: DialogCell): Int? {
        val avatar = descendants(cell).filterIsInstance<SteamAvatarView>().single()
        if (avatar.width == 0 || avatar.height == 0) return null
        val bitmap = Bitmap.createBitmap(avatar.width, avatar.height, Bitmap.Config.ARGB_8888)
        return try {
            avatar.draw(Canvas(bitmap))
            bitmap.getPixel(bitmap.width / 4, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun cancellingSelectionRestoresTheLoadedRemotePhotoOnItsFirstDraw() {
        val url = cachedPhoto(Color.RED)
        val selected = mutableStateOf(false)
        val restored = mutableStateOf(false)
        val entry = SteamConversationListEntry(
            SteamConversationType.DIRECT, "cached-peer", title = "Remote photo", subtitle = "",
            timestamp = 0, unreadCount = 0, pinned = false, avatarUrl = url
        )
        launch()
        main {
            activity!!.setContent {
                MonicaTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.statusBarsPadding()) {
                            SteamConversationRow(
                                entry.copy(title = if (restored.value) "Restored photo" else "Remote photo"),
                                selected.value, selected.value, SteamConversationActions(), {}, {}, {}, {}
                            )
                        }
                    }
                }
            }
        }
        await { row()?.let(::avatarColor) == Color.RED }
        main { selected.value = true }
        await { row()?.let(::avatarColor)?.let { it != Color.RED } == true }
        // A reload would now decode blue. Keeping the red native picture therefore proves
        // state retention even when a cache read is too fast to expose an initials frame.
        writePhoto(steamRemoteImageCacheFile(instrumentation.targetContext, url), Color.BLUE)
        val restoredFrames = mutableListOf<Int>()
        val listener = ViewTreeObserver.OnPreDrawListener {
            row()?.takeIf { it.contentDescription?.startsWith("Restored photo,") == true }?.let { cell ->
                avatarColor(cell)?.let(restoredFrames::add)
            }
            true
        }
        try {
            main {
                activity!!.window.decorView.viewTreeObserver.addOnPreDrawListener(listener)
                selected.value = false
                restored.value = true
            }
            await { restoredFrames.isNotEmpty() }
            main {
                assertTrue("The first restored native draw must contain the photo, without an initials frame", restoredFrames.all { it == Color.RED })
            }
        } finally {
            main { activity!!.window.decorView.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
    }
}
