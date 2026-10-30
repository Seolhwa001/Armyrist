package com.seolhwa.armyrist

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class ChecklistViewPreferencesTest {
    private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
    @Test fun reopenedScreensRetainEachListsMode(){
        assertTrue(ChecklistViewPreferences(app).setMode("A","COMPACT"))
        assertEquals("COMPACT",ChecklistViewPreferences(app).mode("A"))
        assertTrue(ChecklistViewPreferences(app).setMode("B","DETAIL"))
        assertEquals("COMPACT",ChecklistViewPreferences(app).mode("A"))
        assertEquals("DETAIL",ChecklistViewPreferences(app).mode("B"))
    }
    @Test fun newListsUseLastSelectedModeAndInvalidLegacyValuesAreSafe(){
        val p=ChecklistViewPreferences(app);assertTrue(p.setMode("A","COMPACT"));assertEquals("COMPACT",p.mode("new"))
        app.getSharedPreferences("checklist_view_preferences",0).edit().putString("mode:bad","bad").commit()
        assertEquals("DETAIL",ChecklistViewPreferences(app).mode("bad"))
    }
}
