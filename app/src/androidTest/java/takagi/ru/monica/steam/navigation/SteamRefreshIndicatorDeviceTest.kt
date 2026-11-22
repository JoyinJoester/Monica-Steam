package takagi.ru.monica.steam.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh

class SteamRefreshIndicatorDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val refreshing=mutableStateOf(false)
    private var refreshes=0
    private fun mount(enabled:Boolean=true) {
        compose.setContent {
            MaterialTheme {
                SteamExpressivePullToRefresh(refreshing.value,{refreshes++;refreshing.value=true},
                    modifier=Modifier.testTag("refresh-body"),enabled=enabled) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(100) { Text("Synthetic row $it",Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
    }
    @Test fun hiddenIndicatorIsAbsentAndReturnsForPullAndRefresh() {
        mount()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).assertCountEquals(0)
        compose.onNodeWithTag("refresh-body").performTouchInput {
            down(center)
            moveBy(Offset(0f,80f))
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).assertCountEquals(1)
        assertEquals(0,refreshes)
        compose.onNodeWithTag("refresh-body").performTouchInput { up() }
        compose.waitUntil(3000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).fetchSemanticsNodes().isEmpty() }
        assertEquals(0,refreshes)
        compose.onNodeWithTag("refresh-body").performTouchInput { swipeDown(startY=height*.2f,endY=height*.95f,durationMillis=500) }
        compose.waitUntil(3000) { refreshes==1 }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).assertCountEquals(1)
        compose.runOnIdle { refreshing.value=false }
        compose.waitUntil(3000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).fetchSemanticsNodes().isEmpty() }
        assertEquals(1,refreshes)
    }
    @Test fun disabledRefreshDoesNotMountIndicatorOrAcceptPull() {
        mount(enabled=false)
        compose.onNodeWithTag("refresh-body").performTouchInput { swipeDown() }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),useUnmergedTree=true).assertCountEquals(0)
        assertEquals(0,refreshes)
    }
}
