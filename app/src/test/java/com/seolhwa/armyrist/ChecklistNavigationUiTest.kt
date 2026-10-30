package com.seolhwa.armyrist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w411dp-h900dp",application=ArmyristApplication::class)
class ChecklistNavigationUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun bothViewModesSurviveReturningFromChecklistMenu(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=app.coreSuiteRepository
        assertTrue(repo.saveCalendarItem(null,"보기 유지 검증","장비 확인","보기 유지 비고",false,540,"2030-01-02"))
        val list=repo.getChecklists().single()
        compose.setContent{ArmyristTheme{ChecklistApp(repo,app.commonTrashRepository,list.id,{}, {_,_->},{},{},{},{})}}
        compose.onNodeWithText("간략히").performClick()
        compose.onNodeWithText("비고: 보기 유지 비고").assertDoesNotExist()
        compose.onNodeWithText("메뉴").performClick()
        compose.onNodeWithText("보기 유지 검증").performClick()
        compose.onNodeWithText("간략히").assertIsDisplayed()
        compose.onNodeWithText("비고: 보기 유지 비고").assertDoesNotExist()
        compose.runOnIdle{assertEquals("COMPACT",ChecklistViewPreferences(app).mode(list.id))}
        compose.onNodeWithText("자세히").performClick()
        compose.onNodeWithText("비고: 보기 유지 비고").assertIsDisplayed()
        compose.onNodeWithText("메뉴").performClick()
        compose.onNodeWithText("보기 유지 검증").performClick()
        compose.onNodeWithText("비고: 보기 유지 비고").assertIsDisplayed()
        compose.runOnIdle{assertEquals("DETAIL",ChecklistViewPreferences(app).mode(list.id))}
    }
}
