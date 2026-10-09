package app.lawnchair.widgets.lottie

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.android.launcher3.BuildConfig

/**
 * Widget that plays a Lottie animation the user imports. RemoteViews cannot run Lottie, so the
 * launcher draws it itself with [LottieWidgetView] (see LauncherAppWidgetHostView). The provider
 * only keeps the imported files in step with the widgets.
 */
class LottieAppWidgetProvider : AppWidgetProvider() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        LottieWidgetStore.delete(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        LottieWidgetStore.move(context, oldWidgetIds, newWidgetIds)
    }

    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, LottieAppWidgetProvider::class.java.name)
    }
}
