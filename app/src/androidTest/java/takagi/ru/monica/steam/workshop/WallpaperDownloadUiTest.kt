package takagi.ru.monica.steam.workshop

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import takagi.ru.monica.R
import takagi.ru.monica.steam.workshop.download.*
import takagi.ru.monica.ui.theme.MonicaTheme

class WallpaperDownloadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val sample = WorkshopItem("3818900270", WALLPAPER_ENGINE_APP_ID, "荷塘观鱼 2.1完整版",
        preview = "https://images.steamusercontent.com/ugc/10528085922897224548/83065E9B4F24A0AA50E2070F839250F19FEE8D17/",
        size = 13212093)
    private fun record() = WorkshopDownloadRecord(UUID.randomUUID().toString(),sample.appId,sample.id,sample.title,sample.preview,"content://test/folder")
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.filesDir,"wallpaper-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun wallpaperGridKeepsRealPreviewsAndClickableTitles() {
        var opened = ""
        compose.setContent { MonicaTheme(darkTheme = true) {
            Surface(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                WallpaperTile(sample,{opened=sample.id},Modifier.weight(1f))
                WallpaperTile(sample.copy(id="2",title="Dystopic City Port [4K, Ambient]",
                    preview="https://images.steamusercontent.com/ugc/16183377078069002425/D59212FA5735040944DFE5F461283944E28A8421/"),{},Modifier.weight(1f))
            } }
        } }
        compose.onNodeWithText(sample.title).assertIsDisplayed()
        compose.onNodeWithTag("wallpaper_${sample.id}").performClick()
        compose.runOnIdle { assertEquals(sample.id,opened) }
        compose.waitUntil(12_000) {
            compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.workshop_preview)).fetchSemanticsNodes().size == 2
        }
        screenshot("gallery")
    }
    @Test fun anonymousDialogRequiresFolderAndExplainsOriginalFiles() {
        compose.activity.getSharedPreferences("workshop_download_preferences",0).edit().clear().commit()
        compose.setContent { MonicaTheme { WorkshopDownloadDialog(sample,null,null,{}, {}) } }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_anonymous)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_download_start)).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_choose_folder)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_original_hint)).assertIsDisplayed()
    }
    @Test fun anonymousDetailStillOffersDownloadWhenSubscriptionCannotLoad() {
        var clicked = false
        compose.setContent { MonicaTheme {
            SteamWorkshopDetail(WorkshopUiState(selected=sample,detailFailure=WorkshopFailure.LOGIN),{_,_->},{},
                "",{},Modifier.fillMaxSize(),onDownload={clicked=true})
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_download_original)).performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun failureExplainsAccessAndRetryRemainsReachableAtLargeFont() {
        val denied = record().copy(failure="access")
        var retried = ""
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
                MonicaTheme(darkTheme=true) {
                    WorkshopDownloadsScreen(listOf(WorkshopDownloadRow(denied,null)),{}, {},{retried=it.id})
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_error_access)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.workshop_retry)).performClick()
        compose.runOnIdle { assertEquals(denied.id,retried) }
        screenshot("access-large")
    }
    @Test fun savedFilesOfferOpenInsteadOfRetry() {
        compose.setContent { MonicaTheme {
            WorkshopDownloadsScreen(listOf(WorkshopDownloadRow(record().copy(completed=true,savedRoot="content://test/done"),null)),{}, {},{})
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.wallpaper_saved)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wallpaper_open_folder)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.workshop_retry)).assertDoesNotExist()
    }
    @Test fun storageExportPreservesNestedNamesAndBytes() = runBlocking {
        val root = File(compose.activity.cacheDir,"wallpaper-export-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val stage = File(root,"staging").apply { mkdirs() }
            val nested = File(stage,"sub/project.json").apply { parentFile!!.mkdirs(); writeText("""{"type":"scene"}""") }
            val output = File(root,"output").apply { mkdirs() }
            WorkshopDownloadWorker.exportFiles(compose.activity,stage,listOf(nested),DocumentFile.fromFile(output))
            assertArrayEquals(nested.readBytes(),File(output,"sub/project.json").readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun zstandardChunkDecodesUsingBundledAndroidLibrary() {
        val original = "Wallpaper Engine scene data".repeat(100).toByteArray()
        val key = ByteArray(32) { it.toByte() }
        fun le(n: Int) = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(n).array()
        val framed = byteArrayOf(86,83,90,97) + le(0) + com.github.luben.zstd.Zstd.compress(original) +
            le(0) + le(original.size) + byteArrayOf(0,0,0,0,122,115,118)
        val secret = javax.crypto.spec.SecretKeySpec(key,"AES")
        val iv = ByteArray(16)
        val cipherIv = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE,secret); doFinal(iv)
        }
        val encrypted = cipherIv + javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(javax.crypto.Cipher.ENCRYPT_MODE,secret,javax.crypto.spec.IvParameterSpec(iv)); doFinal(framed)
        }
        var a=0L; var b=0L
        original.forEach { a=(a+(it.toInt() and 255))%65521; b=(b+a)%65521 }
        val chunk = WorkshopChunk(java.security.MessageDigest.getInstance("SHA-1").digest(original),
            (b shl 16) or a,0,original.size,encrypted.size)
        assertArrayEquals(original,WorkshopContentCodec.chunk(encrypted,chunk,key))
    }
}
