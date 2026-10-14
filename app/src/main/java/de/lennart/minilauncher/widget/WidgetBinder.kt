package de.lennart.minilauncher.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Runs the steps to add a widget: allocate an ID, get bind permission (system dialog via
 * [AppWidgetManager.ACTION_APPWIDGET_BIND] unless already allowed), then the widget's own
 * configure activity if it has one. [onBound] receives the ready widget ID; cancelled
 * attempts release their ID again.
 *
 * Must be created while the activity is being initialized (it registers an activity result).
 */
class WidgetBinder(
    private val activity: ComponentActivity,
    private val host: () -> AppWidgetHost,
    private val onBound: (Int) -> Unit,
) {
    private val manager by lazy { AppWidgetManager.getInstance(activity) }
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val bindRequest = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) configureOrFinish() else cancel()
    }

    fun bind(provider: AppWidgetProviderInfo) {
        cancel()
        pendingId = host().allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(pendingId, provider.profile, provider.provider, null)) {
            configureOrFinish()
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            try {
                bindRequest.launch(intent)
            } catch (e: ActivityNotFoundException) {
                cancel()
            }
        }
    }

    private fun configureOrFinish() {
        val id = pendingId
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (manager.getAppWidgetInfo(id)?.configure == null) {
            finish()
            return
        }
        try {
            // Goes through the host so configure activities of other profiles work too.
            host().startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
        } catch (e: ActivityNotFoundException) {
            finish()
        } catch (e: SecurityException) {
            finish()
        }
    }

    /** Forward from [Activity.onActivityResult]; returns true if the result was ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQUEST_CONFIGURE) return false
        if (resultCode == Activity.RESULT_OK) finish() else cancel()
        return true
    }

    private fun finish() {
        val id = pendingId
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) onBound(id)
    }

    private fun cancel() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host().deleteAppWidgetId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    /** True while a widget is being added; its ID must not be cleaned up as orphaned. */
    val isBinding: Boolean get() = pendingId != AppWidgetManager.INVALID_APPWIDGET_ID

    fun saveState(outState: Bundle) = outState.putInt(STATE_PENDING_ID, pendingId)

    fun restoreState(state: Bundle?) {
        pendingId = state?.getInt(STATE_PENDING_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private companion object {
        const val REQUEST_CONFIGURE = 0x57
        const val STATE_PENDING_ID = "pending_widget_id"
    }
}
