package app.lawnchair.widgets.lottie

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Told by the system when the home screen added the widget an import asked to pin, with the new
 * widget id, which then takes the imported animation, command and options.
 */
class LottieWidgetPinReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) LottieWidgetStore.applyPending(context, id)
    }
}
