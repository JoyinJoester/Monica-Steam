package takagi.ru.monica.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.ui.reconcileSteamAccountsAfterSourceUpdate

class SteamTokenReorderPerformanceGuardTest {

    @Test
    fun steamAccountDecryptionRunsBeforeTheMainCollector() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        val observeAccounts = source.substringAfter("fun observeAccounts()")
            .substringBefore("suspend fun getAccounts")

        assertTrue(observeAccounts.contains(".flowOn(Dispatchers.Default)"))
    }

    @Test
    fun tokenReorderKeepsLocalListStableWhenPersistenceEmits() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val codeContent = source.substringAfter("private fun SteamCodeContent(")
            .substringBefore("private fun SteamAccountDetailContent(")

        assertTrue(codeContent.contains("var localAccounts by remember { mutableStateOf(accounts) }"))
        assertFalse(codeContent.contains("remember(accounts) { mutableStateOf(accounts) }"))
        assertTrue(codeContent.contains("reconcileSteamAccountsAfterSourceUpdate("))
    }

    @Test
    fun persistedSortMetadataDoesNotReplaceTheSettledLocalList() {
        val first = account(id = 1L, sortOrder = 0)
        val second = account(id = 2L, sortOrder = 1)
        val current = listOf(second, first)
        val incoming = listOf(
            second.copy(sortOrder = 0),
            first.copy(sortOrder = 1)
        )

        val result = reconcileSteamAccountsAfterSourceUpdate(current, incoming)

        assertSame(current, result)
    }

    @Test
    fun realAccountChangesStillReplaceTheLocalList() {
        val current = listOf(account(id = 1L, sortOrder = 0))
        val incoming = listOf(current.first().copy(displayName = "Updated"))

        val result = reconcileSteamAccountsAfterSourceUpdate(current, incoming)

        assertSame(incoming, result)
    }

    private fun account(id: Long, sortOrder: Int): SteamAccount = SteamAccount(
        id = id,
        steamId = "7656119800000000$id",
        accountName = "account$id",
        displayName = "Account $id",
        deviceId = "android:$id",
        sharedSecret = "shared$id",
        identitySecret = "identity$id",
        revocationCode = null,
        tokenGid = null,
        accessToken = null,
        refreshToken = null,
        steamLoginSecure = null,
        rawSteamGuardJson = "{}",
        selected = id == 1L,
        sortOrder = sortOrder,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, relativePath)
    }
}
