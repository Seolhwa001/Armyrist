package com.seolhwa.armyrist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.seolhwa.armyrist.timeplan.v3.ui.DateAwareTimePlanApp

class TimePlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ArmyristApplication
        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    DateAwareTimePlanApp(
                        repository = app.dateAwareTimePlanRepository,
                        legacyRepository = app.timePlanV2Repository,
                        coreRepository = app.coreSuiteRepository,
                        onHome = { finish() }
                    )
                }
            }
        }
    }
}
