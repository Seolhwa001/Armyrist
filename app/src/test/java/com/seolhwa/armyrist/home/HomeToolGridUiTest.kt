package com.seolhwa.armyrist.home

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp", application = Application::class)
class HomeToolGridUiTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    @Before fun clear() { ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("killomris_home_tools_v1", 0).edit().clear().commit() }
    private fun show() {
        compose.setContent { ArmyristTheme {
            val state = rememberLazyListState()
            var viewport by remember { mutableStateOf(Rect.Zero) }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }) {
                item { HomeToolGrid(state, viewport) { id, modifier, dragging ->
                    Surface(onClick = { if (!dragging) clicks++ }, modifier = modifier.height(90.dp).testTag(id)) { Text(id) }
                } }
            }
        } }
    }
    @Test fun ordinaryTapStillOpensTool() {
        show(); compose.onNodeWithTag("counting").performClick(); compose.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun longDragSavesOrderAndDoesNotOpenTool() {
        show()
        val start = compose.onNodeWithTag("handicap").fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("counting").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(start); advanceEventTime(700); moveTo(start); moveTo(target, delayMillis = 300); up()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("killomris_home_tools_v1", 0)
            assertEquals("handicap", HomeToolOrder.decode(prefs.getString("order", null)).first())
            assertEquals(0, clicks)
        }
    }
}
