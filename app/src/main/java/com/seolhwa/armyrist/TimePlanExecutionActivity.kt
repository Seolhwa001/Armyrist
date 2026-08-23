package com.seolhwa.armyrist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.seolhwa.armyrist.timeplan.v3.ui.TimePlanExecutionApp
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager

class TimePlanExecutionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAN_ID = "planId"
        const val EXTRA_MODE = "mode"
        const val EXTRA_POINT_IDS = "pointIds"
        const val MODE_PREPARE = "PREPARE"
        const val MODE_EXECUTE = "EXECUTE"
    }

    override fun onResume() {
        super.onResume()
        val app = application as ArmyristApplication
        TimePlanActionNotificationManager.reconcile(this, app.dateAwareTimePlanRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planId = intent.getStringExtra(EXTRA_PLAN_ID)
        if (planId.isNullOrBlank()) {
            finish()
            return
        }
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PREPARE
        val pointIds = intent.getStringArrayListExtra(EXTRA_POINT_IDS)?.toSet().orEmpty()
        val app = application as ArmyristApplication

        setContent {
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    TimePlanExecutionApp(
                        planId = planId,
                        initialMode = initialMode,
                        initialPointIds = pointIds,
                        repository = app.dateAwareTimePlanRepository,
                        coreRepository = app.coreSuiteRepository,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
