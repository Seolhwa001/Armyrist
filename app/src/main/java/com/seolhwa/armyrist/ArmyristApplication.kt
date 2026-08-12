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

        // Restore journal recovery MUST happen before repository snapshots load.
        ArmyristPortableDataManager.recoverInterruptedRestore(this)

        reloadRepositories()

        ChecklistNotificationManager.reconcile(
            this,
            coreSuiteRepository
        )
    }

    fun reloadAfterPortableDataChange() {
        // Keep repository object identity stable so Activities that already
        // hold these instances immediately see imported/restored data.
        repository.reloadFromPersistence()
        coreSuiteRepository.reloadFromPersistence()

        ChecklistNotificationManager.reconcile(
            this,
            coreSuiteRepository
        )
    }

    private fun reloadRepositories() {
        repository = CountingRepository(this)
        coreSuiteRepository = CoreSuiteRepository(this)
    }
}
