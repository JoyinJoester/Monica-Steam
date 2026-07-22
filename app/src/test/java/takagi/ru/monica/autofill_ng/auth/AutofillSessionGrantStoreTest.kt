package takagi.ru.monica.autofill_ng.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillSessionGrantStoreTest {

    @Test
    fun grantIsLimitedToAppAndInteractionButSurvivesDynamicFieldChanges() {
        var now = 1_000L
        val store = AutofillSessionGrantStore(
            ttlMillis = 30_000L,
            elapsedRealtime = { now },
        )
        val context = AutofillGrantContext(
            packageName = "com.example.app",
            webDomain = "login.example.com",
            interactionIdentifier = "web:login.example.com",
            fieldSignatureKey = "username|password",
        )

        store.grant(context)

        assertTrue(store.isGranted(context))
        assertFalse(store.isGranted(context.copy(packageName = "com.example.other")))
        assertFalse(store.isGranted(context.copy(webDomain = "evil.example")))
        assertFalse(store.isGranted(context.copy(interactionIdentifier = "app:other")))
        assertTrue(store.isGranted(context.copy(fieldSignatureKey = "password")))
        assertTrue(store.isGranted(context.copy(fieldSignatureKey = null)))
    }

    @Test
    fun grantExpiresUsingMonotonicTimeAndCanBeCleared() {
        var now = 5_000L
        val store = AutofillSessionGrantStore(
            ttlMillis = 30_000L,
            elapsedRealtime = { now },
        )
        val context = AutofillGrantContext(
            packageName = "com.example.app",
            webDomain = null,
            interactionIdentifier = "app:com.example.app",
            fieldSignatureKey = "password",
        )

        store.grant(context)
        now += 29_999L
        assertTrue(store.isGranted(context))

        now += 1L
        assertFalse(store.isGranted(context))

        store.grant(context)
        store.clear()
        assertFalse(store.isGranted(context))
    }

    @Test
    fun disabledAuthenticationAlwaysUsesExistingDirectFillMode() {
        assertFalse(
            AutofillAuthenticationPolicy.requiresResponseUnlock(
                authenticationRequired = false,
                vaultLocked = true,
                grantActive = false,
            )
        )
        assertFalse(
            AutofillAuthenticationPolicy.requiresResponseUnlock(
                authenticationRequired = false,
                vaultLocked = false,
                grantActive = false,
            )
        )
    }

    @Test
    fun lockedVaultRequiresOneResponseUnlockUnlessGrantIsActive() {
        assertTrue(
            AutofillAuthenticationPolicy.requiresResponseUnlock(
                authenticationRequired = true,
                vaultLocked = true,
                grantActive = false,
            )
        )
        assertFalse(
            AutofillAuthenticationPolicy.requiresResponseUnlock(
                authenticationRequired = true,
                vaultLocked = true,
                grantActive = true,
            )
        )
        assertFalse(
            AutofillAuthenticationPolicy.requiresResponseUnlock(
                authenticationRequired = true,
                vaultLocked = false,
                grantActive = false,
            )
        )
    }

    @Test
    fun requestUriFactoryDoesNotTreatAndroidAppPackageAsWebDomain() {
        val appContext = AutofillGrantContext.fromRequestUri(
            packageName = "com.example.app",
            requestUri = "androidapp://com.example.app",
            interactionIdentifier = "app:com.example.app",
            fieldSignatureKey = "password",
        )
        val webContext = AutofillGrantContext.fromRequestUri(
            packageName = "com.example.browser",
            requestUri = "https://www.example.com/login",
            interactionIdentifier = "web:example.com",
            fieldSignatureKey = "username|password",
        )

        assertTrue(appContext.webDomain == null)
        assertTrue(webContext.webDomain == "example.com")
    }
}
