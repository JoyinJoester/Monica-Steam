package takagi.ru.monica.steam.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.navigation.liquidglass.motion.LiquidGlassDockMotionState

class LiquidGlassDockMotionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()

    private lateinit var motion: LiquidGlassDockMotionState
    private lateinit var scope: CoroutineScope
    private val selections = mutableListOf<Int>()

    private fun mount(reduced: Boolean = false) {
        rule.setContent {
            scope = rememberCoroutineScope()
            motion = remember(scope) { LiquidGlassDockMotionState(0, 5, scope, { selections += it }, reduced) }
            DisposableEffect(motion) { onDispose { motion.dispose() } }
        }
        rule.waitForIdle()
    }

    @Test fun highFrequencyDragPublishesImmediatelyWithoutPerEventJobs() {
        mount()
        rule.runOnIdle {
            motion.onDrag(1f, 100f, eventTimeMillis = 100L)
            val children = scope.coroutineContext[Job]!!.children.count()
            repeat(1_000) { index ->
                motion.onDrag(if (index % 2 == 0) 1f else -1f, 100f,
                    eventTimeMillis = 101L + index)
                assertEquals(if (index % 2 == 0) .0204f else .0102f, motion.value, .0001f)
            }
            assertEquals("Pointer events must not create additional coroutine jobs", children,
                scope.coroutineContext[Job]!!.children.count())
            assertTrue(motion.isDragging)
            assertEquals(1f, motion.dragOffset, .001f)
        }
        rule.runOnIdle { motion.onDragEnd(0f, 100f) }
        rule.waitUntil(5_000) { !motion.isRunning && !motion.isDragging && motion.dragOffset == 0f }
        rule.runOnIdle { assertEquals(listOf(0), selections) }
    }

    @Test fun newSelectionSuppressesPendingDragCallbackAndSettles() {
        mount()
        rule.runOnIdle {
            motion.onDrag(140f, 100f)
            motion.onDragEnd(0f, 100f)
            motion.updateIndex(4)
            motion.updateIndex(2)
        }
        rule.waitUntil(5_000) { !motion.isRunning && motion.value == 2f }
        rule.runOnIdle {
            assertEquals(2, motion.targetIndex)
            assertTrue("Superseded release must not change the page", selections.isEmpty())
        }
    }

    @Test fun disposalCancelsMotionAndCannotPublishLateSelection() {
        mount()
        rule.runOnIdle {
            motion.onDrag(140f, 100f)
            motion.onDragEnd(0f, 100f)
            motion.dispose()
            val retained = motion.value
            motion.onDrag(80f, 100f)
            motion.updateIndex(4)
            assertEquals(retained, motion.value, 0f)
        }
        rule.waitUntil(5_000) { scope.coroutineContext[Job]!!.children.none { it.isActive } }
        rule.runOnIdle { assertTrue(selections.isEmpty()) }
    }

    @Test fun reducedMotionAndOverscrollReturnToValidDestination() {
        mount(reduced = true)
        rule.runOnIdle {
            motion.onDrag(-2_000f, 100f)
            assertEquals(0f, motion.value, 0f)
            motion.onDragEnd(-2_000f, 100f)
        }
        rule.waitUntil(5_000) { !motion.isRunning && motion.dragOffset == 0f }
        rule.runOnIdle {
            assertEquals(listOf(0), selections)
            motion.updateIndex(4)
        }
        rule.waitUntil(5_000) { !motion.isRunning && motion.value == 4f }
        rule.runOnIdle {
            motion.onDrag(2_000f, 100f)
            assertEquals(4f, motion.value, 0f)
            motion.onDragEnd(2_000f, 100f)
        }
        rule.waitUntil(5_000) { !motion.isRunning && motion.dragOffset == 0f }
        rule.runOnIdle { assertEquals(listOf(0, 4), selections) }
    }
}
