package takagi.ru.monica.steam.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.*
import org.junit.Assert.*
import androidx.test.platform.app.InstrumentationRegistry
import takagi.ru.monica.steam.SteamComposeFixtureActivity

class SteamTabPageHostDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<SteamComposeFixtureActivity>()
    private val selected=mutableStateOf("library")
    private val enabled=mutableStateOf(true)
    private val showDialog=mutableStateOf(false)
    private val mounts=mutableMapOf<String,Int>()
    private val owners=mutableMapOf<String,LifecycleOwner>()
    private val disposed=mutableListOf<String>()
    private var backPage=""
    private var clicked=""
    private var customParent:LifecycleOwner?=null
    private fun mount() {
        compose.setContent {
            MaterialTheme {
                val parent=customParent ?: LocalLifecycleOwner.current
                CompositionLocalProvider(LocalLifecycleOwner provides parent) {
                SteamTabPageHost(selected.value,setOf("library","chat"),enabled.value,
                    modifier=Modifier.fillMaxSize(),transitionSpec={ _,_ -> steamPageTransition(true) }) { page ->
                    val owner=LocalLifecycleOwner.current
                    DisposableEffect(Unit) {
                        mounts[page]=(mounts[page] ?: 0)+1
                        owners[page]=owner
                        onDispose { disposed+=page }
                    }
                    var draft by rememberSaveable { mutableStateOf("") }
                    BackHandler { backPage=page }
                    Column {
                        Text(page,Modifier.testTag("page-$page"))
                        TextField(draft,{draft=it},Modifier.testTag("draft-$page"))
                        AndroidView(factory={context -> android.widget.Button(context).apply {
                            text="Native $page"
                            setAllCaps(false)
                            setOnClickListener { clicked=page }
                        }},modifier=Modifier.testTag("native-$page"))
                    }
                    if(page=="library" && showDialog.value) AlertDialog(
                        onDismissRequest={showDialog.value=false},confirmButton={},title={Text("Synthetic dialog")})
                }
                }
            }
        }
    }
    private fun select(page:String) { compose.runOnIdle { selected.value=page };compose.waitForIdle() }
    @Test fun visitedPagesKeepDraftAndOnlyCurrentPageReceivesBack() {
        mount()
        compose.onNodeWithTag("draft-library").performTextReplacement("Synthetic draft")
        select("chat")
        compose.onNodeWithTag("page-chat").assertIsDisplayed()
        compose.onNodeWithTag("page-library").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.CREATED,owners.getValue("library").lifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED,owners.getValue("chat").lifecycle.currentState)
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertEquals("chat",backPage)
        }
        select("library")
        compose.onNodeWithTag("draft-library").assertTextContains("Synthetic draft")
        compose.onNodeWithTag("draft-library").assertIsNotFocused()
        compose.runOnIdle { assertEquals(1,mounts["library"]);assertEquals(1,mounts["chat"]) }
        select("other")
        compose.runOnIdle {
            assertTrue(disposed.containsAll(listOf("library","chat")))
            assertEquals(Lifecycle.State.DESTROYED,owners.getValue("chat").lifecycle.currentState)
        }
        select("library")
        compose.runOnIdle { assertEquals(2,mounts["library"]);assertEquals(1,mounts["chat"]) }
    }
    @Test fun platformViewModeEvictsInactivePageWithoutRemountingCurrentPage() {
        mount();select("chat");select("library")
        compose.runOnIdle { enabled.value=false }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("chat" in disposed);assertEquals(1,mounts["library"]) }
    }
    @Test fun dialogFocusKeepsCurrentPageAndProgrammaticNavigationClosesItsWindow() {
        mount();select("chat");select("library")
        compose.runOnIdle { showDialog.value=true }
        compose.onNodeWithText("Synthetic dialog").assertIsDisplayed()
        compose.waitUntil(3000) { "chat" in disposed }
        compose.runOnIdle { assertEquals(1,mounts["library"]) }
        select("chat")
        compose.onNodeWithText("Synthetic dialog").assertDoesNotExist()
        compose.onNodeWithTag("page-chat").assertIsDisplayed()
    }
    @Test fun backgroundDropsInactivePageBeforeResume() {
        mount();select("chat");select("library")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("chat" in disposed);assertEquals(1,mounts["library"]) }
        select("chat")
        compose.runOnIdle { assertEquals(2,mounts["chat"]) }
    }
    @Test fun cachedNativeViewsDoNotReceiveTouchesOrRemainInAccessibilityTree() {
        mount();select("chat");select("library")
        compose.onNodeWithTag("native-library").performTouchInput { click() }
        compose.runOnIdle { assertEquals("library",clicked) }
        fun containsText(text:String):Boolean {
            fun search(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
                if(node==null) return false
                try {
                    if(node.text?.toString()==text) return true
                    for(index in 0 until node.childCount) if(search(node.getChild(index))) return true
                    return false
                } finally { node.recycle() }
            }
            return search(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        }
        try { compose.waitUntil(3000) { containsText("Native library") } } catch(error:Throwable) {
            val lines=mutableListOf<String>()
            fun dump(node:android.view.accessibility.AccessibilityNodeInfo?,depth:Int=0) {
                if(node==null) { lines+="null root/child";return }
                try {
                    lines+="${" ".repeat(depth)}${node.packageName}: ${node.text} / ${node.contentDescription} children=${node.childCount}"
                    for(index in 0 until node.childCount) dump(node.getChild(index),depth+1)
                } finally { node.recycle() }
            }
            dump(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"tab-host-accessibility.txt").writeText(lines.joinToString("\n"))
            throw error
        }
        assertFalse("Hidden native page must not be exposed to accessibility",containsText("Native chat"))
        select("chat")
        compose.onNodeWithTag("native-chat").performTouchInput { click() }
        compose.runOnIdle { assertEquals("chat",clicked) }
        compose.waitUntil(3000) { containsText("Native chat") }
        assertFalse("Previous native page must leave accessibility after switching",containsText("Native library"))
    }
    @Test fun sameFrameNavigationAndBackgroundCannotRestoreInactivePage() {
        lateinit var registry:LifecycleRegistry
        compose.runOnUiThread {
            customParent=object:LifecycleOwner {
                override val lifecycle:Lifecycle=LifecycleRegistry(this).also { registry=it;it.currentState=Lifecycle.State.RESUMED }
            }
        }
        mount();select("chat");select("library")
        compose.runOnIdle {
            selected.value="chat"
            registry.currentState=Lifecycle.State.CREATED
            registry.currentState=Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("A stopped visit must not reappear as an inactive cached page","library" in disposed) }
    }

    @Test fun switchingTabsReleasesNativeEditorFocusAndRoutesKeysToCurrentPage() {
        val editors = mutableMapOf<String, android.widget.EditText>()
        compose.setContent {
            MaterialTheme {
                SteamTabPageHost(selected.value, setOf("library", "chat"), true,
                    Modifier.fillMaxSize(), transitionSpec = { _, _ -> steamPageTransition(true) }) { page ->
                    Column(Modifier.fillMaxSize()) {
                        Text("Visible $page")
                        AndroidView(factory = { context -> android.widget.EditText(context).apply {
                            hint = "Native editor $page"
                            editors[page] = this
                        } })
                    }
                }
            }
        }
        select("chat")
        compose.runOnIdle {
            assertTrue(editors.getValue("chat").requestFocus())
            assertTrue(editors.getValue("chat").hasFocus())
        }
        select("library")
        compose.runOnIdle {
            val hidden = editors.getValue("chat")
            assertFalse("Hidden native field must release focus", hidden.hasFocus())
            assertTrue(editors.getValue("library").requestFocus())
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_A)
        compose.runOnIdle {
            assertEquals("Keys must go to the visible native field", "a", editors.getValue("library").text.toString())
            assertEquals("Hidden native field must not receive keys", "", editors.getValue("chat").text.toString())
        }
        select("chat")
        compose.runOnIdle { assertTrue("Returning editor must be usable", editors.getValue("chat").requestFocus()) }
    }
}
