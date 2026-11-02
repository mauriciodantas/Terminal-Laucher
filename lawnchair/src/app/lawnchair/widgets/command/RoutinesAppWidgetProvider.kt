package app.lawnchair.widgets.command

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import com.android.launcher3.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Widget that lists the scheduled commands and the rules. RemoteViews cannot follow the jobs as they
 * change, so the launcher draws it itself with [RoutinesWidgetView] (see LauncherAppWidgetHostView);
 * the provider only gives it a component name.
 */
class RoutinesAppWidgetProvider : AppWidgetProvider() {

    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, RoutinesAppWidgetProvider::class.java.name)
    }
}

/** Tells the routines widgets on screen that a job or a result changed. Everything runs in the launcher process. */
object RoutinesFeed {
    private val state = MutableStateFlow(0)

    val changes: StateFlow<Int> = state.asStateFlow()

    fun changed() {
        state.update { it + 1 }
    }
}
