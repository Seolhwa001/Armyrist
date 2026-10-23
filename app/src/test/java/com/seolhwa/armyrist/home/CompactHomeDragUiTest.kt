package com.seolhwa.armyrist.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w320dp-h480dp",application=ArmyristApplication::class)
class CompactHomeDragUiTest{
    @get:Rule val compose=createComposeRule()
    @Test fun dragAcrossGridPersistsOrderWithoutOpeningATool(){
        var opened="";val app=ApplicationProvider.getApplicationContext<ArmyristApplication>()
        compose.setContent{ArmyristTheme{CompactHome(0,HomeBannerConfig(compactHome=true),{opened=it},{},{})}}
        val from=compose.onNodeWithContentDescription("실셈 도구").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithContentDescription("보고 양식 도구").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithContentDescription("실셈 도구").performTouchInput{
            down(center);advanceEventTime(650);moveBy(to-from);advanceEventTime(180);up()
        }
        compose.runOnIdle{
            val stored=HomeToolOrder.decode(app.getSharedPreferences("killomris_home_tools_v1",0).getString("order",null));assertEquals("counting",stored[3]);assertEquals(HomeToolOrder.defaults.toSet(),stored.toSet());assertEquals("",opened)
        }
        compose.onNodeWithContentDescription("실셈 도구").performClick();assertEquals("counting",opened)
    }
}
