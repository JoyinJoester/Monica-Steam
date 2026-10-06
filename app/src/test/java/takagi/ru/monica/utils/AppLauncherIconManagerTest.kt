package takagi.ru.monica.utils

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.data.AppLauncherIcon

class AppLauncherIconManagerTest {
    @Test
    fun enablesTheChosenIconAndDisablesTheOtherOne() {
        val states = AppLauncherIconManager.launcherStatesFor(AppLauncherIcon.CLASSIC)

        assertEquals(
            mapOf(
                CLASSIC_ALIAS to PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                MODERN_ALIAS to PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ),
            states
        )
    }

    @Test
    fun appliesTheReplacementBeforeRemovingTheOutgoingEntry() {
        val states = AppLauncherIconManager.launcherStatesFor(AppLauncherIcon.MODERN)

        assertEquals(
            listOf(MODERN_ALIAS, CLASSIC_ALIAS),
            states.keys.toList()
        )
    }

    @Test
    fun keepsTheNewIconAsTheDefaultChoice() {
        val states = AppLauncherIconManager.launcherStatesFor(AppLauncherIcon.MODERN)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, states[MODERN_ALIAS])
    }

    private companion object {
        private const val MODERN_ALIAS = "takagi.ru.monica.ModernVisibleLauncherAlias"
        private const val CLASSIC_ALIAS = "takagi.ru.monica.ClassicVisibleLauncherAlias"
    }
}
