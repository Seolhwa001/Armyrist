package com.seolhwa.armyrist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.seolhwa.armyrist.timeplan.ui.TimePlanV2App

class TimePlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository =
            (application as ArmyristApplication).timePlanV2Repository

        setContent {
            ArmyristTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = ArmyristColors.AppBackground
                ) {
                    TimePlanV2App(
                        repository = repository,
                        onHome = { finish() }
                    )
                }
            }
        }
    }
}
