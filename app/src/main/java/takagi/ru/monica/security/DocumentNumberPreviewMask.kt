package takagi.ru.monica.security

import takagi.ru.monica.data.model.DocumentType

private const val ID_CARD_HIDDEN_PART = "••••••••••••"

/**
 * Privacy-safe representation for passive list and card previews.
 * Detail screens remain responsible for explicit reveal/copy actions.
 */
fun maskDocumentNumberForPreview(number: String, type: DocumentType): String {
    val trimmed = number.trim()
    if (trimmed.isBlank()) return "••••"

    return when (type) {
        DocumentType.ID_CARD -> trimmed.take(3) + ID_CARD_HIDDEN_PART
        DocumentType.PASSPORT -> if (trimmed.length >= 5) {
            "${trimmed.take(2)}•••••${trimmed.takeLast(3)}"
        } else {
            "••••"
        }
        DocumentType.DRIVER_LICENSE -> if (trimmed.length >= 8) {
            "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
        } else {
            "••••"
        }
        DocumentType.SOCIAL_SECURITY -> if (trimmed.length >= 4) {
            "${trimmed.take(2)}••••••${trimmed.takeLast(2)}"
        } else {
            "••••"
        }
        DocumentType.OTHER -> if (trimmed.length >= 4) {
            "••••${trimmed.takeLast(4)}"
        } else {
            "••••"
        }
    }
}
