package app.lawnchair.command

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.lawnchair.command.R as CommandR
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads one QR code with the camera and hands its text back to the bar. It stops after the first
 * code with something in it, and asks for the camera only here, when the user taps [QR]. Cancelling
 * or going back returns nothing, so the bar keeps what was typed.
 */
class QrScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                ScanScreen(
                    onFind = { text ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    /** Starts the reader and gives back the text read, or null when the user left without a code. */
    class Contract : ActivityResultContract<Unit, String?>() {
        override fun createIntent(context: Context, input: Unit): Intent = Intent(context, QrScanActivity::class.java)
        override fun parseResult(resultCode: Int, intent: Intent?): String? = if (resultCode == RESULT_OK) intent?.getStringExtra(EXTRA_TEXT) else null
    }

    companion object {
        private const val EXTRA_TEXT = "qr_text"
    }
}

private fun hasCamera(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun ScanScreen(onFind: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val present = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(hasCamera(context)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (present && !granted) request.launch(Manifest.permission.CAMERA) }
    // Back from the app settings with the camera allowed there.
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = hasCamera(context) }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !present -> Notice(stringResource(CommandR.string.cmd_qr_no_camera), null, null, onBack)

            granted -> {
                CameraView(onFind = onFind)
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(CommandR.string.cmd_qr_scan_hint), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.background(Color(0xCC000000)).padding(8.dp))
                    TextButton(onClick = onBack, modifier = Modifier.background(Color(0xCC000000))) { Text(stringResource(CommandR.string.cmd_qr_back), color = Color.White) }
                }
            }

            else -> Notice(
                stringResource(CommandR.string.cmd_qr_perm_text),
                { request.launch(Manifest.permission.CAMERA) },
                {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                },
                onBack,
            )
        }
    }
}

/** A message over the black screen, with the ways on: allow, settings, back. */
@Composable
private fun Notice(text: String, onAllow: (() -> Unit)?, onSettings: (() -> Unit)?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onAllow != null) Text(stringResource(CommandR.string.cmd_qr_perm_title), color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
        Text(text, color = Color.White, textAlign = TextAlign.Center)
        if (onAllow != null) Button(onClick = onAllow, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(CommandR.string.cmd_qr_perm_allow)) }
        if (onSettings != null) Button(onClick = onSettings, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(CommandR.string.cmd_qr_perm_settings)) }
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(CommandR.string.cmd_qr_back), color = Color.White) }
    }
}

@Composable
private fun CameraView(onFind: (String) -> Unit) {
    val context = LocalContext.current
    val owner = context as ComponentActivity
    val find by androidx.compose.runtime.rememberUpdatedState(onFind)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var empty by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(empty) {
        if (empty > 0) {
            kotlinx.coroutines.delay(2500)
            empty = 0
        }
    }
    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val found = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener(
            {
                val cameras = runCatching { future.get() }.getOrNull() ?: return@addListener
                provider = cameras
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val main = ContextCompat.getMainExecutor(context)
                analysis.setAnalyzer(
                    executor,
                    QrAnalyzer { text ->
                        // Only the first code with text counts; frames that still show it are dropped.
                        if (text.isBlank()) {
                            main.execute { if (!found.get()) empty += 1 }
                        } else if (found.compareAndSet(false, true)) {
                            main.execute {
                                analysis.clearAnalyzer()
                                find(text)
                            }
                        }
                    },
                )
                cameras.unbindAll()
                runCatching { cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (empty > 0) {
            Text(
                stringResource(CommandR.string.cmd_qr_scan_empty),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(12.dp),
            )
        }
    }
}

/** Looks for a QR code in the brightness plane of each frame; a code has no up, so no rotation is needed. */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
    private var lastEmpty = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val data = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val text = decode(source) ?: decode(source.invert()) ?: return
            if (text.isBlank()) {
                // A blank code is reported now and then, not on every frame.
                val now = System.currentTimeMillis()
                if (now - lastEmpty < 2500) return
                lastEmpty = now
            }
            onText(text)
        } catch (_: Exception) {
            // A frame that cannot be read is skipped.
        } finally {
            image.close()
        }
    }

    private fun decode(source: com.google.zxing.LuminanceSource): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.orEmpty()
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
