package takagi.ru.monica.steam.io

import android.content.Context
import android.net.Uri
import java.io.OutputStream
import java.nio.charset.Charset

internal object SteamSafWriter {
    fun writeText(
        output: OutputStream?,
        text: String,
        charset: Charset = Charsets.UTF_8
    ): Boolean {
        if (output == null) return false
        return runCatching {
            output.bufferedWriter(charset).use { writer ->
                writer.write(text)
            }
        }.isSuccess
    }

    fun writeBytes(output: OutputStream?, bytes: ByteArray): Boolean {
        if (output == null) return false
        return runCatching {
            output.use { stream -> stream.write(bytes) }
        }.isSuccess
    }

    fun writeText(
        context: Context,
        uri: Uri,
        text: String,
        mode: String = "w",
        charset: Charset = Charsets.UTF_8
    ): Boolean {
        val output = runCatching {
            context.contentResolver.openOutputStream(uri, mode)
        }.getOrNull()
        return writeText(output, text, charset)
    }

    fun writeBytes(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        mode: String = "w"
    ): Boolean {
        val output = runCatching {
            context.contentResolver.openOutputStream(uri, mode)
        }.getOrNull()
        return writeBytes(output, bytes)
    }
}
