package takagi.ru.monica.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import takagi.ru.monica.data.model.DocumentType

class DocumentNumberPreviewMaskTest {

    @Test
    fun idCardPreviewKeepsOnlyFirstThreeCharacters() {
        val masked = maskDocumentNumberForPreview("110101199001011234", DocumentType.ID_CARD)

        assertEquals("110••••••••••••", masked)
        assertFalse(masked.contains("1234"))
        assertFalse(masked.contains("19900101"))
    }

    @Test
    fun idCardMaskLengthDoesNotExposeOriginalLength() {
        assertEquals(
            maskDocumentNumberForPreview("110101199001011234", DocumentType.ID_CARD).length,
            maskDocumentNumberForPreview("110123456789", DocumentType.ID_CARD).length
        )
    }
}
