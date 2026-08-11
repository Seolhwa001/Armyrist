package com.seolhwa.armyrist

import android.app.Application
import com.seolhwa.armyrist.data.CountingRepository
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository

class ArmyristApplication : Application() {
    lateinit var repository: CountingRepository
        private set

    lateinit var coreSuiteRepository: CoreSuiteRepository
        private set

    override fun onCreate() {
        super.onCreate()

        repository = CountingRepository(this)
        coreSuiteRepository = CoreSuiteRepository(this)

        ChecklistNotificationManager.createChannel(this)
        ChecklistNotificationManager.reconcile(this, coreSuiteRepository)
    }
}
