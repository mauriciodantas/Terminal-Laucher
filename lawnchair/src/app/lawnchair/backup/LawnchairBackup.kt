package app.lawnchair.backup

import LawnchairLockedStateController
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.data.AppDatabase
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.provider.RestoreDbTask
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LawnchairBackup(
    private val context: Context,
    private val uri: Uri,
) {
    lateinit var info: BackupInfo
    var screenshot: Bitmap? = null

    suspend fun readInfoAndPreview() {
        var tmpScreenshot: Bitmap? = null
        readZip(
            mapOf(
                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },
                SCREENSHOT_FILE_NAME to { tmpScreenshot = BitmapFactory.decodeStream(it) },
            ),
        )
        val size = max(info.previewWidth, info.previewHeight).coerceAtMost(4000)
        screenshot = tmpScreenshot?.scaleDownTo(size)
    }

    suspend fun restore(selectedContents: Int) {
        val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
        val contents = selectedContents and info.contents
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            handlers.putAll(
                getFiles(context, forRestore = true).mapValues { entry ->
                    {
                        val file = entry.value
                        file.parentFile?.mkdirs()
                        it.copyTo(file.outputStream())
                    }
                },
            )
        }
        context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.deleteRecursively()
        DeviceGridState(info.gridState).writeToPrefs(context, true)
        val fontsDir = fontsDir(context)
        var fonts: (suspend (String, InputStream) -> Unit)? = null
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            fontsDir.deleteRecursively()
            fontsDir.mkdirs()
            fonts = { name, input ->
                if (name.startsWith(FONTS_PREFIX)) {
                    // Keep the font files inside the fonts folder, whatever the entry name says.
                    File(fontsDir, File(name).name).outputStream().use { input.copyTo(it) }
                }
            }
        }
        readZip(handlers, fonts)

        var dbController = ModelDbController(context)
        RestoreDbTask.performRestore(context, dbController)
    }

    private suspend fun readZip(
        handlers: Map<String, suspend (InputStream) -> Unit>,
        fallback: (suspend (String, InputStream) -> Unit)? = null,
    ) {
        withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
            pfd.use {
                FileInputStream(it.fileDescriptor).use { inStream ->
                    ZipInputStream(inStream).use { zipIs ->
                        var entry: ZipEntry?
                        while (true) {
                            entry = zipIs.nextEntry
                            if (entry == null) break
                            val handler = handlers[entry.name]
                            if (handler != null) handler.invoke(zipIs) else fallback?.invoke(entry.name, zipIs)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val BACKUP_VERSION = 1
        private const val PREFS_FILE_NAME = "${LauncherFiles.SHARED_PREFERENCES_KEY}.xml"

        // Command bar actions and history, chat strip and onboarding state.
        private const val NOSTROMO_FILE_NAME = "nostromo.xml"

        // Locked apps in recents, the live information switch and the user's own fonts.
        private const val TASK_LOCK_FILE_NAME = "${LawnchairLockedStateController.TASK_LOCK_STATE}.xml"
        private const val LIVE_INFO_FILE_NAME = "live-information.preferences_pb"
        private const val FONTS_PREFIX = "customFonts/"
        private const val PREFS_DB_FILE_NAME = "preferences"
        private const val PREFS_DATASTORE_FILE_NAME = "preferences.preferences_pb"

        const val INFO_FILE_NAME = "info.pb"
        const val SCREENSHOT_FILE_NAME = "screenshot.png"
        const val LAUNCHER_DB_FILE_NAME = "launcher.db"
        const val RESTORED_DB_FILE_NAME = "restored.db"

        const val INCLUDE_LAYOUT_AND_SETTINGS = 1 shl 0

        const val MIME_TYPE = "application/zip"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        val contentOptions = listOf(
            INCLUDE_LAYOUT_AND_SETTINGS to R.string.backup_content_layout_and_settings,
        )

        fun generateBackupFileName(): String {
            val fileName = "Lawnchair_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"
            return "$fileName.lawnchairbackup"
        }

        fun getFiles(context: Context, forRestore: Boolean): Map<String, File> {
            return mapOf(
                LAUNCHER_DB_FILE_NAME to launcherDbFile(context, forRestore),
                PREFS_FILE_NAME to prefsFile(context),
                NOSTROMO_FILE_NAME to File(context.cacheDir.parent, "shared_prefs/$NOSTROMO_FILE_NAME"),
                TASK_LOCK_FILE_NAME to File(context.cacheDir.parent, "shared_prefs/$TASK_LOCK_FILE_NAME"),
                LIVE_INFO_FILE_NAME to File(context.filesDir, "datastore/$LIVE_INFO_FILE_NAME"),
                PREFS_DB_FILE_NAME to prefsDbFile(context),
                PREFS_DATASTORE_FILE_NAME to prefsDataStoreFile(context),
            )
        }

        @SuppressLint("MissingPermission")
        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap, fileUri: Uri) {
            val idp = LauncherAppState.getIDP(context)
            val createdAt = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
            val colorHints = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.colorHints ?: 0
            val wallpaperSupportsDarkText = (colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0
            val info = BackupInfo.newBuilder()
                .setLawnchairVersion(BuildConfig.VERSION_CODE)
                .setBackupVersion(BACKUP_VERSION)
                .setCreatedAt(createdAt)
                .setContents(contents)
                .setGridState(DeviceGridState(idp).toProtoMessage())
                .setPreviewWidth(screenshotBitmap.width)
                .setPreviewHeight(screenshotBitmap.height)
                .setPreviewDarkText(wallpaperSupportsDarkText)
                .build()

            // Folders and icon overrides sit in the write-ahead log until it is merged into the
            // database file, so merge it first or the newest changes would be left out.
            withContext(Dispatchers.IO) {
                runCatching { AppDatabase.INSTANCE.get(context).checkpointSync() }
            }

            val pfd = context.contentResolver.openFileDescriptor(fileUri, "w")!!
            withContext(Dispatchers.IO) {
                pfd.use {
                    ZipOutputStream(FileOutputStream(pfd.fileDescriptor).buffered()).use { out ->
                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))
                        info.writeTo(out)

                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }

                        getFiles(context, forRestore = false).entries.forEach {
                            if (!it.value.exists()) return@forEach
                            out.putNextEntry(ZipEntry(it.key))
                            it.value.inputStream().copyTo(out)
                        }

                        fontsDir(context).listFiles()?.filter { it.isFile }?.forEach {
                            out.putNextEntry(ZipEntry(FONTS_PREFIX + it.name))
                            it.inputStream().copyTo(out)
                        }
                    }
                }
            }
        }

        private fun fontsDir(context: Context): File = File(context.filesDir, "customFonts")

        private fun launcherDbFile(context: Context, forRestore: Boolean): File {
            val dbName = if (forRestore) RESTORED_DB_FILE_NAME else LauncherAppState.getIDP(context).dbFile
            return context.getDatabasePath(dbName)
        }

        private fun prefsFile(context: Context): File {
            val dir = context.cacheDir.parent
            return File(dir, "shared_prefs/$PREFS_FILE_NAME")
        }

        private fun prefsDbFile(context: Context): File {
            return context.getDatabasePath(PREFS_DB_FILE_NAME)
        }

        private fun prefsDataStoreFile(context: Context): File {
            return File(context.filesDir, "datastore/${PREFS_DATASTORE_FILE_NAME}")
        }
    }
}
