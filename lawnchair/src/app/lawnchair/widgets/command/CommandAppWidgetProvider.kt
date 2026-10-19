package app.lawnchair.widgets.command

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.android.launcher3.BuildConfig

/**
 * Widget that runs a command line and shows its result, again on a schedule when set. RemoteViews
 * cannot run a command, so the launcher draws it itself with [CommandWidgetView] (see
 * LauncherAppWidgetHostView). The provider only keeps the saved settings in step with the widgets.
 */
class CommandAppWidgetProvider : AppWidgetProvider() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CommandWidgetStore.delete(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        CommandWidgetStore.move(context, oldWidgetIds, newWidgetIds)
    }

    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, CommandAppWidgetProvider::class.java.name)
    }
}
