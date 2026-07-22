package takagi.ru.monica.data

import java.io.File

data class SteamMaFileExportCandidate(
    val id: Long,
    val title: String,
    val subtitle: String
)

data class PreparedSteamMaFileExport(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val successMessage: String,
    val accountCount: Int
)
