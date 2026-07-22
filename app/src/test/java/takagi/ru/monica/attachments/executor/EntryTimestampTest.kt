package takagi.ru.monica.attachments.executor

import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.BinaryReference
import org.junit.Test
import okio.ByteString.Companion.toByteString
import java.util.UUID

/**
 * 测试 Entry.copy() 是否会自动更新 times.lastModificationTime
 */
class EntryTimestampTest {

    @Test
    fun entryCopy_withBinariesChange_shouldUpdateTimestamp() {
        val original = Entry(
            uuid = UUID.randomUUID(),
            fields = EntryFields.of(
                "Title" to EntryValue.Plain("Test")
            ),
            binaries = emptyList()
        )

        println("Original times: ${original.times}")
        println("Original lastModificationTime: ${original.times?.lastModificationTime}")

        Thread.sleep(100)  // 确保时间戳会不同

        val dummyHash = ByteArray(32) { 0xAB.toByte() }
        val modified = original.copy(
            binaries = listOf(BinaryReference(hash = dummyHash.toByteString(), name = "test.txt"))
        )

        println("Modified times: ${modified.times}")
        println("Modified lastModificationTime: ${modified.times?.lastModificationTime}")

        // 如果 kotpass 不自动更新时间戳，这两个会相等
        println("Times are equal: ${original.times == modified.times}")
        println(
            "LastModificationTime are equal: " +
                "${original.times?.lastModificationTime == modified.times?.lastModificationTime}"
        )
    }
}
