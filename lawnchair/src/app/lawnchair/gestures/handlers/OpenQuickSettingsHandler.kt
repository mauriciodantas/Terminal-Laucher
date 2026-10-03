package app.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenQuickSettingsHandler(
    context: Context,
) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            Log.v(OpenQuickSettingsHandler::class.java.simpleName, "(Tried reflection)")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandSettingsPanel")
                .apply { isAccessible = true }
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
