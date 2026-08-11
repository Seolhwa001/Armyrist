package com.seolhwa.armyrist

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Legacy placeholder.
 *
 * Checklist alarm sound configuration was moved from the Home-level settings
 * screen into each ChecklistItem editor. This Activity is intentionally kept
 * temporarily so older source trees that still contain the class continue to
 * compile safely.
 */
class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
