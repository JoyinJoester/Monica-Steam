package takagi.ru.monica.steam.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.renderSteamGroupAvatarBitmap
import takagi.ru.monica.steam.friends.groupchat.avatar.ui.steamGroupAvatarMembers

@RunWith(AndroidJUnit4::class)
class SteamGroupAvatarRendererTest {
    private val tile = Color.rgb(150, 185, 240)
    private fun members(count: Int) = steamGroupAvatarMembers(List(count) { index ->
        SteamFriend("member-$index", SteamFriendRelationship.FRIEND, personaName = listOf("小", "明", "林", "国")[index % 4])
    })

    @Test fun recordsRepeatedAvatarRenderCost() {
        val times = List(100) {
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            renderSteamGroupAvatarBitmap(members(4), emptyMap(), 147, tile, Color.BLACK, tile).recycle()
            (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        }.sorted()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "avatar-render-cost.txt")
            .writeText("n=100 size=147px p50=${times[50]}ms p95=${times[95]}ms max=${times.last()}ms")
    }

    @Test fun chineseInkStaysAwayFromTheCircleAndSeamsAtEveryAvatarSize() {
        for (size in listOf(40, 56, 88, 112, 176)) for (count in 1..4) {
            val bitmap = renderSteamGroupAvatarBitmap(members(count), emptyMap(), size, tile, Color.BLACK, tile)
            var ink = 0
            for (y in 0 until size) for (x in 0 until size) {
                val pixel = bitmap.getPixel(x, y)
                // Small CJK strokes are antialiased and can have no fully black pixel. Count
                // meaningful ink coverage against the known tile, rather than only solid ink.
                if (Color.alpha(pixel) < 200 || Color.red(pixel) > Color.red(tile) / 2 || Color.green(pixel) > Color.green(tile) / 2 || Color.blue(pixel) > Color.blue(tile) / 2) continue
                ink++
                val dx = x + 0.5f - size / 2f
                val dy = y + 0.5f - size / 2f
                assertTrue("$count members / $size px: ink touches circle at $x,$y", dx*dx + dy*dy < size*size*0.46f*0.46f)
                if (count > 1) assertTrue("Ink touches horizontal seam", kotlin.math.abs(dy) > size*0.035f)
                if (count == 4 || count == 3 && dy > 0) assertTrue("Ink touches vertical seam", kotlin.math.abs(dx) > size*0.035f)
            }
            assertTrue("$count members / $size px: Chinese initials remain visible ($ink ink pixels)", ink > count * 3)
            bitmap.recycle()
        }
    }

    @Test fun maskIsTransparentOutsideAndOpaqueInsideForEveryMemberCount() {
        for (count in 0..6) {
            val size = 112
            val bitmap = renderSteamGroupAvatarBitmap(members(count), emptyMap(), size, tile, tile, tile)
            for (y in 0 until size) for (x in 0 until size) {
                val dx = x + 0.5f - size / 2f
                val dy = y + 0.5f - size / 2f
                val radiusSquared = dx*dx + dy*dy
                if (radiusSquared < 54f*54f) assertEquals("Interior never leaks background", 255, Color.alpha(bitmap.getPixel(x,y)))
                if (radiusSquared > 58f*58f) assertEquals("Exterior stays transparent", 0, Color.alpha(bitmap.getPixel(x,y)))
            }
            bitmap.recycle()
        }
    }

    @Test fun fourPhotosFillTheCorrectQuadrantsWithCentreCropping() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val photos = colors.mapIndexed { index, color -> Bitmap.createBitmap(if (index % 2 == 0) 80 else 20, if (index % 2 == 0) 20 else 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
            // Only the central square has the member color. Stretching or a wrong
            // translation leaks magenta into samples near the tile's visible edge.
            val paint = android.graphics.Paint().apply { this.color = color }
            val edge = minOf(width, height).toFloat()
            android.graphics.Canvas(this).drawRect((width-edge)/2f, (height-edge)/2f, (width+edge)/2f, (height+edge)/2f, paint)
        } }
        val bitmap = renderSteamGroupAvatarBitmap(members(4), photos.mapIndexed { i, photo -> "member-$i" to photo }.toMap(), 112, tile, Color.BLACK, tile)
        listOf(28 to 28, 84 to 28, 28 to 84, 84 to 84).forEachIndexed { index, (x,y) ->
            for (offset in listOf(-8, 0, 8)) {
                assertEquals("Centered photo fills horizontal sample", colors[index], bitmap.getPixel(x+offset,y))
                assertEquals("Centered photo fills vertical sample", colors[index], bitmap.getPixel(x,y+offset))
            }
        }
        assertEquals(0, Color.alpha(bitmap.getPixel(0,0)))
        photos.forEach(Bitmap::recycle)
        bitmap.recycle()
    }

    @Test fun transparentPhotosKeepAnOpaqueGroupTone() {
        val transparent = Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888)
        val bitmap = renderSteamGroupAvatarBitmap(members(4), members(4).associate { it.steamId to transparent },112,tile,Color.BLACK,tile)
        assertEquals(tile,bitmap.getPixel(28,28))
        assertEquals(tile,bitmap.getPixel(84,84))
        transparent.recycle()
        bitmap.recycle()
    }
    @Test fun reorderedAndMissingPhotosStayWithTheirMember() {
        val red = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val bitmap = renderSteamGroupAvatarBitmap(members(4).reversed(), mapOf("member-0" to red, "member-2" to blue), 112, tile, tile, tile)
        assertEquals(tile, bitmap.getPixel(28,28))
        assertEquals(Color.BLUE, bitmap.getPixel(84,28))
        assertEquals(tile, bitmap.getPixel(28,84))
        assertEquals(Color.RED, bitmap.getPixel(84,84))
        bitmap.recycle(); red.recycle(); blue.recycle()
    }

}
