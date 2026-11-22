package takagi.ru.monica.steam.pilot

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation-only draft for the isolated, non-debuggable R8 pilot APK.
 * Contains no app imports or class literals. Every protocol call invokes an
 * actual native method loaded from targetContext, bypassing Kotlin fallbacks.
 */
@RunWith(AndroidJUnit4::class)
class SteamR8NativeTest {
    private lateinit var bridgeClass: Class<*>
    private lateinit var bridgeInstance: Any

    @Before
    fun requireIsolatedNonDebuggableTargetAndLoadedNativeLibrary() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Refuse to inspect another installed app", TARGET_PACKAGE, target.packageName)
        assertEquals(TARGET_PACKAGE, target.applicationInfo.packageName)
        assertEquals(
            "This suite requires the non-debuggable R8 pilot artifact",
            0,
            target.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE,
        )

        bridgeClass = Class.forName(BRIDGE_CLASS, true, target.classLoader)
        bridgeInstance = requireNotNull(bridgeClass.getField("INSTANCE").get(null))
        val available = bridgeClass.getDeclaredMethod("isAvailable").invoke(bridgeInstance)
        assertEquals("The packaged JNI library must load; fallback is not a pass", true, available)
    }

    @Test
    fun nativeCmEncodeDecodePreservesHeaderUnicodeAndBody() {
        val body = byteArrayOf(0x08, 0x01)
        val steamId = 76_561_198_000_000_000L
        val jobSource = -1L
        val jobTarget = 4_294_967_297L
        val jobName = "pilot/测试"
        val encoded = bytes(
            "nativeEncodeCmMessage",
            arrayOf(
                Int::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                ByteArray::class.java,
                Long::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                String::class.java,
            ),
            151, steamId, 42, body, jobSource, jobTarget, jobName,
        )
        assertTrue("CM header and protobuf body must be present", encoded.size > 8 + body.size)
        val wire = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(151 or Int.MIN_VALUE, wire.int)
        val headerLength = wire.int
        assertEquals(encoded.size - 8 - body.size, headerLength)
        assertArrayEquals(body, encoded.copyOfRange(8 + headerLength, encoded.size))

        val decoded = bytes("nativeDecodeCmMessages", arrayOf(ByteArray::class.java), encoded)
        val bridge = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
        assertMagic(bridge, "MSC1")
        assertEquals(1, bridge.int)
        assertEquals(151, bridge.int)
        assertEquals(steamId, bridge.long)
        assertEquals(42, bridge.int)
        assertEquals(jobSource, bridge.long)
        assertEquals(jobTarget, bridge.long)
        assertEquals(Int.MIN_VALUE, bridge.int) // absent e_result
        assertEquals(Int.MIN_VALUE, bridge.int) // absent transport_error
        assertEquals(jobName, readRequiredString(bridge))
        assertEquals(-1, bridge.int) // absent error_message
        assertEquals(body.size, bridge.int)
        val restoredBody = ByteArray(body.size)
        bridge.get(restoredBody)
        assertArrayEquals(body, restoredBody)
        assertFalse("No unparsed bridge bytes", bridge.hasRemaining())
    }

    @Test
    fun nativeWorkshopOrderingKeepsSignedScoresUnsignedIdsAndStableTies() {
        val signature = arrayOf<Class<*>>(LongArray::class.java, LongArray::class.java)
        val ordinary = callNative(
            "nativeOrderWorkshopSubscriptions", signature,
            longArrayOf(9, 9, 10, 9), longArrayOf(2, 10, 8, 1),
        )
        assertTrue(ordinary is IntArray)
        assertArrayEquals(intArrayOf(2, 3, 1, 0), ordinary as IntArray)

        val boundaries = callNative(
            "nativeOrderWorkshopSubscriptions", signature,
            longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE, 0, 0, 0),
            longArrayOf(1, 2, -1, 9, -1), // -1 crosses JNI as u64::MAX.
        )
        assertTrue(boundaries is IntArray)
        assertArrayEquals(intArrayOf(1, 2, 4, 3, 0), boundaries as IntArray)

        val empty = callNative("nativeOrderWorkshopSubscriptions", signature, longArrayOf(), longArrayOf())
        assertTrue(empty is IntArray)
        assertArrayEquals(intArrayOf(), empty as IntArray)
    }

    @Test
    fun nativeOwnedGamesAcceptsMinimalSyntheticProtobuf() {
        // Declared count 1; one game with app_id 1 and name "QR". No account data.
        val response = byteArrayOf(0x08, 0x01, 0x12, 0x06, 0x08, 0x01, 0x12, 0x02, 0x51, 0x52)
        val parsed = bytes("nativeParseOwnedGames", arrayOf(ByteArray::class.java), response)
        val bridge = ByteBuffer.wrap(parsed).order(ByteOrder.LITTLE_ENDIAN)
        assertMagic(bridge, "MSL1")
        assertEquals(1, bridge.int)
        assertEquals(1, bridge.int)
        assertEquals(0, bridge.int)
        assertEquals(0, bridge.int)
        assertEquals(0L, bridge.long)
        assertEquals("QR", readRequiredString(bridge))
        assertEquals("", readRequiredString(bridge))
        assertFalse("No unparsed owned-games bytes", bridge.hasRemaining())
    }

    @Test
    fun nativeMalformedPayloadAndMismatchedArraysReturnNullWithoutFallback() {
        val malformed = byteArrayOf(0x12, 0x7f, 0x08)
        assertNull(callNative("nativeParseOwnedGames", arrayOf(ByteArray::class.java), malformed))
        assertNull(callNative("nativeDecodeCmMessages", arrayOf(ByteArray::class.java), malformed))
        assertNull(
            callNative(
                "nativeOrderWorkshopSubscriptions",
                arrayOf(LongArray::class.java, LongArray::class.java),
                longArrayOf(1), longArrayOf(),
            ),
        )
        // A failed native parse must not make the JNI library unavailable.
        assertEquals(true, bridgeClass.getDeclaredMethod("isAvailable").invoke(bridgeInstance))
    }

    private fun callNative(name: String, parameterTypes: Array<Class<*>>, vararg arguments: Any): Any? {
        val method: Method = bridgeClass.getDeclaredMethod(name, *parameterTypes)
        assertTrue("$name must be an actual JNI method", Modifier.isNative(method.modifiers))
        method.isAccessible = true
        val receiver = if (Modifier.isStatic(method.modifiers)) null else bridgeInstance
        return try {
            method.invoke(receiver, *arguments)
        } catch (failure: InvocationTargetException) {
            // Missing symbols or JNI failures must fail the test, never become a fallback result.
            throw AssertionError("JNI invocation failed: $name", failure.targetException)
        }
    }

    private fun bytes(name: String, parameterTypes: Array<Class<*>>, vararg arguments: Any): ByteArray {
        val result = callNative(name, parameterTypes, *arguments)
        assertTrue("$name must return native bytes", result is ByteArray)
        return result as ByteArray
    }

    private fun assertMagic(buffer: ByteBuffer, expected: String) {
        val actual = ByteArray(4)
        buffer.get(actual)
        assertArrayEquals(expected.toByteArray(Charsets.US_ASCII), actual)
    }

    private fun readRequiredString(buffer: ByteBuffer): String {
        val length = buffer.int
        assertTrue("Native string length must fit the remaining buffer", length in 0..buffer.remaining())
        val value = ByteArray(length)
        buffer.get(value)
        return String(value, Charsets.UTF_8)
    }

    private companion object {
        const val TARGET_PACKAGE = "takagi.ru.monica.steamapp.toolchainpilot"
        const val BRIDGE_CLASS = "takagi.ru.monica.steam.core.RustSteamCoreNative"
    }
}
