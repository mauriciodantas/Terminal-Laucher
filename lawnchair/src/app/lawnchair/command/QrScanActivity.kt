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
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import app.lawnchair.ui.theme.LawnchairTheme

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
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    LaunchedEffect(empty) {
        if (empty > 0) {
            kotlinx.coroutines.delay(2500)
            empty = 0
        }
    }
    // The ring of a tap to focus fades away by itself.
    LaunchedEffect(focusAt) {
        if (focusAt != null) {
            kotlinx.coroutines.delay(1200)
            focusAt = null
        }
    }
    // Phones that lose focus up close get a new focus at the centre every two seconds.
    LaunchedEffect(camera) {
        val cam = camera ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(2000)
            runCatching { cam.cameraControl.startFocusAndMetering(centreFocus(previewView)) }
        }
    }
    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val found = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val analyzer = QrAnalyzer { text ->
            // Only the first code with text counts; frames that still show it are dropped.
            val main = ContextCompat.getMainExecutor(context)
            if (text.isBlank()) {
                main.execute { if (!found.get()) empty += 1 }
            } else if (found.compareAndSet(false, true)) {
                main.execute { find(text) }
            }
        }
        future.addListener(
            {
                val cameras = runCatching { future.get() }.getOrNull() ?: return@addListener
                provider = cameras
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                // A theme or a pack makes a dense code: 640x480, the default, cannot resolve its modules.
                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                cameras.unbindAll()
                camera = runCatching { cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }.getOrNull()
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
            analyzer.close()
        }
    }
    // Pinch to zoom, tap to focus.
    DisposableEffect(previewView, camera) {
        val cam = camera
        val pinch = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoom = cam?.cameraInfo?.zoomState?.value ?: return true
                    cam.cameraControl.setZoomRatio(zoom.zoomRatio * detector.scaleFactor)
                    return true
                }
            },
        )
        previewView.setOnTouchListener { view, event ->
            pinch.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && !pinch.isInProgress && event.pointerCount == 1 && cam != null) {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                runCatching {
                    cam.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build(),
                    )
                }
                focusAt = Offset(event.x, event.y)
                view.performClick()
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Viewfinder(focusAt)
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

private fun centreFocus(view: PreviewView): FocusMeteringAction {
    val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
    return FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build()
}

/** The square where the code goes, with corners, and the ring where the user last tapped to focus. */
@Composable
private fun Viewfinder(focusAt: Offset?) {
    Canvas(Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height) * 0.72f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val arm = side * 0.14f
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val color = Color.White
        // Dim everything outside the square so the eye goes to it.
        val dim = Color(0x66000000)
        drawRect(dim, Offset.Zero, GSize(size.width, top))
        drawRect(dim, Offset(0f, top + side), GSize(size.width, size.height - top - side))
        drawRect(dim, Offset(0f, top), GSize(left, side))
        drawRect(dim, Offset(left + side, top), GSize(size.width - left - side, side))
        for ((cx, cy, dx, dy) in listOf(
            listOf(left, top, 1f, 1f),
            listOf(left + side, top, -1f, 1f),
            listOf(left, top + side, 1f, -1f),
            listOf(left + side, top + side, -1f, -1f),
        )) {
            drawLine(color, Offset(cx, cy), Offset(cx + dx * arm, cy), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(cx, cy), Offset(cx, cy + dy * arm), stroke.width, StrokeCap.Round)
        }
        focusAt?.let { drawCircle(color, radius = 36.dp.toPx(), center = it, style = Stroke(width = 3.dp.toPx())) }
    }
}

/**
 * Looks for a QR code in each frame. ML Kit goes first (it finds small, tilted, curved or dense
 * codes that zxing misses); zxing, on the brightness plane, is the second try, also inverted.
 */
private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
    private var lastEmpty = 0L

    fun close() = scanner.close()

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        try {
            val text = mlKit(image) ?: zxing(image) ?: return
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

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun mlKit(image: ImageProxy): String? {
        val media = image.image ?: return null
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        val codes = Tasks.await(scanner.process(input), 3, TimeUnit.SECONDS)
        // Prefer the bytes read as UTF-8: a theme with accents must come back intact.
        return codes.firstNotNullOfOrNull { code ->
            code.rawBytes?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotEmpty() && '\uFFFD' !in it } ?: code.rawValue?.takeIf { it.isNotEmpty() }
        }
    }

    private fun zxing(image: ImageProxy): String? {
        val plane = image.planes[0]
        val data = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
        val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
        return decode(source) ?: decode(source.invert())
    }

    private fun decode(source: com.google.zxing.LuminanceSource): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text.orEmpty()
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
