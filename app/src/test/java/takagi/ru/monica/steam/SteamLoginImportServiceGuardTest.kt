package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.service.SteamLoginImportService

class SteamLoginImportServiceGuardTest {
    @Test
    fun authApiChallengeTypesSeparateCodesFromMobileApproval() {
        assertTrue(SteamLoginImportService.isCodeChallengeType(2))
        assertTrue(SteamLoginImportService.isCodeChallengeType(3))
        assertFalse(SteamLoginImportService.isCodeChallengeType(4))
        assertFalse(SteamLoginImportService.isCodeChallengeType(5))

        assertFalse(SteamLoginImportService.isPollingChallengeType(2))
        assertFalse(SteamLoginImportService.isPollingChallengeType(3))
        assertTrue(SteamLoginImportService.isPollingChallengeType(4))
        assertTrue(SteamLoginImportService.isPollingChallengeType(5))

        assertEquals(3, SteamLoginImportService.manualCodeTypeForPollingChallenge(4))
        assertEquals(2, SteamLoginImportService.manualCodeTypeForPollingChallenge(5))
        assertNull(SteamLoginImportService.manualCodeTypeForPollingChallenge(2))
        assertNull(SteamLoginImportService.manualCodeTypeForPollingChallenge(3))
    }

    @Test
    fun steamLoginImportKeepsMobileApprovalPollingPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()

        assertTrue(source.contains("\"platform_type\" to \"3\""))
        assertTrue(source.contains("STEAM_WEBSITE_ID = \"Mobile\""))
        assertTrue(source.contains("pollPendingSession"))
        assertTrue(source.contains("codeAlreadyAccepted = updateEResult == 29"))
        assertTrue(source.contains("method = \"AddAuthenticator\""))
        assertTrue(source.contains("method = \"FinalizeAddAuthenticator\""))
        assertTrue(source.contains("writeFixed64(1, steamIdLong)"))
        assertFalse(source.contains("URL_ADD_AUTHENTICATOR"))
        assertTrue(viewModelSource.contains("startPendingLoginPolling"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isPollingChallengeType"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isAddAuthenticatorActivationType"))
    }

    @Test
    fun steamLoginImportAllowsCodeOrApprovalAndOptionalRemark() {
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val loginDialogSource = screenSource.substringAfter("private fun SteamLoginImportDialog")
            .substringBefore("private fun badgeCountText")

        assertTrue(viewModelSource.contains("SteamLoginImportService.manualCodeTypeForPollingChallenge"))
        assertTrue(viewModelSource.contains("val confirmationType = pollingManualCodeType"))
        assertTrue(viewModelSource.contains("?: codeChallenge?.confirmationType"))
        assertTrue(viewModelSource.contains("pendingLoginDisplayName"))
        assertTrue(viewModelSource.contains("pendingLoginCompletionAccountId"))
        assertTrue(viewModelSource.contains("fun beginSteamIdCompletionLogin("))
        assertTrue(viewModelSource.contains("accountId: Long,"))
        assertTrue(viewModelSource.contains("userName: String,"))
        assertTrue(viewModelSource.contains("password: String"))
        assertTrue(viewModelSource.contains("saveCompletedSteamIdAccount("))
        assertTrue(viewModelSource.contains("account = account,"))
        assertTrue(viewModelSource.contains("loginPayload = payload,"))
        assertTrue(viewModelSource.contains("SteamMaFileBackupCodec.encode(completedBase)"))
        assertTrue(viewModelSource.contains("repository.replaceAccount(account)"))
        assertTrue(viewModelSource.contains("displayNameOverride = displayNameOverride"))
        assertTrue(viewModelSource.contains("requiresCode && canPoll"))
        assertTrue(viewModelSource.contains("fun beginSteamLogin("))
        assertTrue(viewModelSource.contains("displayName: String = \"\""))
        assertFalse(viewModelSource.contains("fun submitSteamLoginCode(code: String, displayName"))

        assertTrue(loginDialogSource.contains("onBeginLogin: (String, String, String, Long?) -> Unit"))
        assertTrue(loginDialogSource.contains("onSubmitLoginCode: (String) -> Unit"))
        assertTrue(loginDialogSource.contains("pendingChallenge.canPoll"))
        assertTrue(loginDialogSource.contains("PasswordDatabase.getDatabase(context)"))
        assertTrue(loginDialogSource.contains("SecurityManager(context)"))
        assertTrue(loginDialogSource.contains("PasswordEntryPickerBottomSheet("))
        assertTrue(loginDialogSource.contains("R.string.autofill_select_password"))
        assertTrue(loginDialogSource.contains("R.string.steam_login_fill_from_password_applied"))
        assertTrue(loginDialogSource.contains("passwordEntriesForPicker.filter { !it.isDeleted && !it.isArchived }"))
        assertTrue(loginDialogSource.contains("pickerSecurityManager.decryptData(entry.username)"))
        assertTrue(loginDialogSource.contains("pickerSecurityManager.decryptData(entry.password)"))
        assertTrue(loginDialogSource.contains("if (showSteamPasswordPicker && pendingChallenge == null)"))
        assertTrue(loginDialogSource.contains("LaunchedEffect(pendingChallenge?.pendingSessionId, pendingChallenge?.confirmationType)"))
        assertTrue(loginDialogSource.contains("challengeCode = \"\""))
        assertTrue(loginDialogSource.contains("loginDisplayName"))
        assertTrue(loginDialogSource.contains("showRemarkField"))
        assertTrue(loginDialogSource.contains("descriptionRes?.let"))
        assertTrue(loginDialogSource.contains("R.string.steam_remark_optional_label"))
        assertTrue(
            loginDialogSource.contains(
                "onBeginLogin(loginName, loginPassword, loginDisplayName, selectedPasswordEntryId)"
            )
        )
        assertFalse(loginDialogSource.contains("steam_display_name_label"))

        val repositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        assertTrue(repositorySource.contains("suspend fun replaceAccount(account: SteamAccount): Long"))
        assertTrue(repositorySource.contains("findExistingBySteamId(account.steamId)"))
        assertTrue(repositorySource.contains("require(duplicate == null)"))
    }

    @Test
    fun steamIdCompletionLoginDoesNotStartAuthenticatorTransfer() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val completionLoginSource = viewModelSource.substringAfter(
            "fun beginSteamIdCompletionLogin("
        ).substringBefore("fun beginSteamQrLogin")

        assertTrue(serviceSource.contains("private enum class LoginPurpose"))
        assertTrue(serviceSource.contains("SESSION_ONLY"))
        assertTrue(serviceSource.contains("fun beginSessionLogin("))
        assertTrue(serviceSource.contains("purpose = LoginPurpose.SESSION_ONLY"))
        assertTrue(serviceSource.contains("if (purpose == LoginPurpose.SESSION_ONLY)"))
        assertTrue(serviceSource.contains("return buildSessionOnlyLoginResult("))
        assertTrue(serviceSource.contains("sessionOnly = true"))
        assertTrue(viewModelSource.contains("loginImportService.beginSessionLogin(userName, password)"))
        assertFalse(completionLoginSource.contains("loginImportService.beginLogin(userName, password)"))
        assertTrue(viewModelSource.contains("result.toSteamIdCompletionPayload(account)"))
        assertTrue(viewModelSource.contains("sharedSecret = account.sharedSecret"))
        assertTrue(viewModelSource.contains("identitySecret = account.identitySecret"))
    }

    @Test
    fun steamProtoSupportsSteamFixed64Fields() {
        val steamId = 76561198000000000L
        val writer = SteamProtoWriter().apply {
            writeFixed64(1, steamId)
        }

        val fields = SteamProtoReader(writer.toByteArray()).parse()

        assertEquals(steamId, fields[1]?.asFixed64)
        assertEquals(steamId.toString(), fields[1]?.asFixed64UnsignedString)
    }

    @Test
    fun steamLoginImportDecodesSteamIdFromQrLoginJwt() {
        val payload = """{"sub":"76561198000000000"}"""
        val encodedPayload = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        val token = "header.$encodedPayload.signature"

        assertEquals("76561198000000000", SteamLoginImportService.steamIdFromJwt(token))
        assertNull(SteamLoginImportService.steamIdFromJwt("not-a-jwt"))
        assertNull(SteamLoginImportService.steamIdFromJwt("header.e30.signature"))
    }

    @Test
    fun steamLoginGuardCodeAndPollingUseAuthApiProtobufShape() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()

        assertTrue(source.contains("beginAuthSessionViaCredentialsWithProtobuf"))
        assertTrue(source.contains("method = \"BeginAuthSessionViaCredentials\""))
        assertTrue(source.contains("writeString(1, DEVICE_FRIENDLY_NAME)"))
        assertTrue(source.contains("writeString(2, userName)"))
        assertTrue(source.contains("writeString(3, encryptedPassword)"))
        assertTrue(source.contains("writeUint64(4, timestamp)"))
        assertTrue(source.contains("writeBool(5, false)"))
        assertTrue(source.contains("writeVarint(6, 3L)"))
        assertTrue(source.contains("writeVarint(7, 1L)"))
        assertTrue(source.contains("writeString(8, STEAM_WEBSITE_ID)"))
        assertTrue(source.contains("writeMessage(9, buildAuthApiDeviceDetails())"))
        assertTrue(source.contains("beginAuthSessionViaCredentialsWithProtobuf("))
        assertTrue(source.contains("val beginAuthResponse = postForm("))

        assertTrue(source.contains("submitSteamGuardCodeWithProtobuf"))
        assertTrue(source.contains("method = \"UpdateAuthSessionWithSteamGuardCode\""))
        assertTrue(source.contains("writeUint64(1, clientIdLong)"))
        assertTrue(source.contains("writeFixed64(2, steamIdLong)"))
        assertTrue(source.contains("writeString(3, code.trim())"))
        assertTrue(source.contains("writeVarint(4, confirmationType.toLong())"))
        assertTrue(source.contains("9 -> {"))
        assertTrue(source.contains("SteamGuardSubmitResult.UnsupportedSession"))
        assertTrue(source.contains("88 -> \"Steam 登录失败：令牌验证码无效或已过期\""))

        assertTrue(source.contains("pollForTokenWithProtobuf"))
        assertTrue(source.contains("method = \"PollAuthSessionStatus\""))
        assertTrue(source.contains("writeUint64(1, clientId)"))
        assertTrue(source.contains("writeBytes(2, authIds.requestId)"))
        assertTrue(source.contains("generateAccessTokenForApp("))
        assertTrue(source.contains("method = \"GenerateAccessTokenForApp\""))
        assertTrue(source.contains("writeString(1, refreshToken)"))
        assertTrue(source.contains("writeFixed64(2, steamIdLong)"))
        assertTrue(source.contains("decodeAuthApiRequestIdBytes"))
        assertTrue(source.contains("parseUnsigned64AsSignedLong"))
        assertTrue(source.contains("pollForTokenWithForm"))
    }

    @Test
    fun steamLoginImportSupportsMonicaGeneratedQrLogin() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val defaultStrings = projectFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(serviceSource.contains("fun beginQrLogin(): QrLoginResult"))
        assertTrue(serviceSource.contains("method = \"BeginAuthSessionViaQR\""))
        assertTrue(serviceSource.contains("writeString(1, DEVICE_FRIENDLY_NAME)"))
        assertTrue(serviceSource.contains("writeVarint(2, 3L)"))
        assertTrue(serviceSource.contains("writeMessage(3, buildAuthApiDeviceDetails())"))
        assertTrue(serviceSource.contains("writeString(4, STEAM_WEBSITE_ID)"))
        assertTrue(serviceSource.contains("pollQrLoginSession"))
        assertTrue(serviceSource.contains("steamIdFromJwt(refreshToken)"))
        assertTrue(serviceSource.contains("steamIdFromJwt(accessToken)"))
        assertTrue(serviceSource.contains("QrLoginResult.LoginChallengeRequired"))

        assertTrue(viewModelSource.contains("pendingQrLoginChallenge"))
        assertTrue(viewModelSource.contains("fun beginSteamQrLogin(displayName: String = \"\")"))
        assertTrue(viewModelSource.contains("startPendingQrLoginPolling"))
        assertTrue(viewModelSource.contains("loginImportService.pollQrLoginSession"))
        assertTrue(viewModelSource.contains("handleLoginChallenge(result.challenge)"))

        assertTrue(screenSource.contains("SteamAddAccountMethod.QR_LOGIN"))
        assertTrue(screenSource.contains("viewModel.beginSteamQrLogin()"))
        assertTrue(screenSource.contains("SteamQrLoginImportDialog("))
        assertTrue(screenSource.contains("QRCodeWriter().encode(content, BarcodeFormat.QR_CODE"))
        assertTrue(screenSource.contains("R.string.steam_add_method_qr_login"))
        assertTrue(screenSource.contains("R.string.steam_qr_login_import_message"))
        assertTrue(defaultStrings.contains("<string name=\"steam_add_method_qr_login\">"))
        assertTrue(zhStrings.contains("<string name=\"steam_add_method_qr_login\">二维码登录 Steam</string>"))
    }

    @Test
    fun steamLoginImportUsesModernAuthenticatorTransferProtobufShape() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()

        assertTrue(source.contains("method = \"RemoveAuthenticatorViaChallengeStart\""))
        assertTrue(source.contains("method = \"RemoveAuthenticatorViaChallengeContinue\""))
        assertTrue(source.contains("writeString(1, code.trim())"))
        assertTrue(source.contains("writeBool(2, true)"))
        assertTrue(source.contains("writeVarint(3, 2L)"))
        assertTrue(source.contains("val replacementFields = fields[2]?.bytes?.let"))
        assertTrue(source.contains("replacementFields?.get(1)?.bytes"))
        assertTrue(source.contains("replacementFields?.get(2)?.asFixed64UnsignedString"))
        assertFalse(source.contains("accessToken = session.replaceRefreshToken"))
        assertFalse(source.contains("accessToken = refreshToken"))
    }

    @Test
    fun steamLoginImportDoesNotReportTransferStartFailureAsLoginFailure() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()

        assertTrue(source.contains("mapReplaceStartEresultToMessage(error.eResult)"))
        assertTrue(source.contains("Steam 登录已成功，但该账号已经绑定 Steam 验证器"))
        assertTrue(source.contains("Steam 拒绝转移验证器（EResult=2）"))
        assertFalse(
            source.contains(
                "ReplaceAuthenticatorStartResult.Failure(\n                mapEresultToMessage(error.eResult)"
            )
        )
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
