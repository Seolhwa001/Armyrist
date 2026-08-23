package com.seolhwa.armyrist

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.seolhwa.armyrist.timeplan.v3.ui.DateAwareTimePlanApp

class TimePlanActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        val app = application as ArmyristApplication
        app.dateAwareTimePlanRepository.reloadFromPersistence()
        resumeRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ArmyristApplication
        setContent {
            @Suppress("UNUSED_VARIABLE")
            val observedResumeRevision = resumeRevision
            ArmyristTheme {
                Surface(Modifier.fillMaxSize(), color = ArmyristColors.AppBackground) {
                    DateAwareTimePlanApp(
                        repository = app.dateAwareTimePlanRepository,
                        legacyRepository = app.timePlanV2Repository,
                        coreRepository = app.coreSuiteRepository,
                        trashRepository = app.commonTrashRepository,
                        onHome = { finish() },
                        onOpenExecution = { planId, mode, pointIds ->
                            startActivity(
                                Intent(this@TimePlanActivity, TimePlanExecutionActivity::class.java).apply {
                                    putExtra(TimePlanExecutionActivity.EXTRA_PLAN_ID, planId)
                                    putExtra(TimePlanExecutionActivity.EXTRA_MODE, mode)
                                    putStringArrayListExtra(
                                        TimePlanExecutionActivity.EXTRA_POINT_IDS,
                                        ArrayList(pointIds)
                                    )
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
