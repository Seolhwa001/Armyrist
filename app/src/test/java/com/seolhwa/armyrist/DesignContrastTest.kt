package com.seolhwa.armyrist
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
class DesignContrastTest{
    private fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
    @Test fun mainAndSecondaryCopyStayReadableOnAllLightSurfaces(){
        listOf(ArmyristColors.AppBackground,ArmyristColors.WorkSurface,ArmyristColors.InputSurface,ArmyristColors.SecondaryControl).forEach{surface->
            listOf(ArmyristColors.PrimaryText,ArmyristColors.SecondaryText,ArmyristColors.MutedText).forEach{text->assertTrue("Text contrast",contrast(text,surface)>=4.5f)}
        }
        assertTrue(contrast(ArmyristColors.OnDark,ArmyristColors.PrimaryControl)>=4.5f)
    }
}
