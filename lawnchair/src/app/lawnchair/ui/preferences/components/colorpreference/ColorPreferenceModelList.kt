package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import javax.inject.Inject

@LauncherAppSingleton
class ColorPreferenceModelList @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val models = mutableMapOf<String, ColorPreferenceModel>()

    init {
        val prefs = PreferenceManager2.getInstance(context)
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.accentColor,
                labelRes = R.string.accent_color,
                dynamicEntries = dynamicColors,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.strokeColorStyle,
                labelRes = R.string.appearance_color_search_outline,
                dynamicEntries = listOf(
                    ColorPreferenceEntry<ColorOption>(
                        ColorOption.Default,
                        { stringResource(R.string.follow_accent) },
                        { 0 },
                    ),
                ) + dynamicColors,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.launcherBackgroundColor,
                labelRes = R.string.launcher_bg_color_label,
                // The system accent and the wallpaper color make no sense as a background: only the default and custom colors.
                dynamicEntries = listOf(
                    ColorPreferenceEntry<ColorOption>(
                        ColorOption.Default,
                        { stringResource(R.string.launcher_bg_default) },
                        { 0 },
                    ),
                ),
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.hotseatBackgroundColor,
                labelRes = R.string.appearance_color_dock,
                dynamicEntries = dynamicColorsWithDefault,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.appDrawerBackgroundColor,
                labelRes = R.string.app_drawer_bg_color_label,
                dynamicEntries = dynamicColorsWithDefault,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.workProfileTabBackgroundColor,
                labelRes = R.string.work_profile_tab_background_label,
                dynamicEntries = dynamicColors,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.notificationDotColor,
                labelRes = R.string.notification_dots_color,
                dynamicEntries = dynamicColorsWithDefault,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.notificationDotTextColor,
                labelRes = R.string.notification_dots_text_color,
                dynamicEntries = dynamicColorsWithDefault,
            ),
        )
        registerModel(
            ColorPreferenceModel(
                prefObject = prefs.folderColor,
                labelRes = R.string.appearance_color_folders,
                dynamicEntries = dynamicColorsWithDefault,
            ),
        )
    }

    operator fun get(key: String): ColorPreferenceModel = models.getValue(key)

    private fun registerModel(model: ColorPreferenceModel) {
        models[model.prefObject.key.name] = model
    }

    companion object {
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getColorPreferenceModelList)
    }
}
