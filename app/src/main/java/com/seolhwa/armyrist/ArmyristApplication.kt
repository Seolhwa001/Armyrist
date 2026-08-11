package com.seolhwa.armyrist

import android.app.Application
import com.seolhwa.armyrist.data.CountingRepository
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository

class ArmyristApplication : Application() {
    lateinit var repository: CountingRepository
        private set

    lateinit var coreSuiteRepository: CoreSuiteRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Stage 1 repository is intentionally preserved exactly as the existing source of truth.
        repository = CountingRepository(this)

        // Stage 2 domains use a separate local store so Stage 1 data is never destructively migrated.
        coreSuiteRepository = CoreSuiteRepository(this)
    }
}
