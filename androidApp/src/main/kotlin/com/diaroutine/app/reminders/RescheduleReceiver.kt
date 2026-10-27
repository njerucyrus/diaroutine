package com.diaroutine.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.diaroutine.app.diaRoutine
import kotlinx.coroutines.launch

/**
 * Re-arms reminders after a restart, an app update, a change of time or time zone,
 * or when exact-alarm access is granted or withdrawn.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.diaRoutine.container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.rescheduleSoon().join()
            } finally {
                pending.finish()
            }
        }
    }
}
