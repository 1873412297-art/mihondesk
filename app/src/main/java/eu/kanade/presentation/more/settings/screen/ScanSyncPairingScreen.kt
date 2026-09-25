package eu.kanade.presentation.more.settings.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.data.sync.SyncStrings
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Edit
import java.util.concurrent.Executors

/**
 * Full-screen camera QR code scanner that, on a successful decode, parses the URI as a
 * [mihon.sync.transport.http.SyncPairingCode] and writes it to [SyncPreferences].
 *
 * UI: edge-to-edge camera preview with a dimmed scan frame, animated scan line,
 * a bottom hint card and a manual-code entry as fallback.
 *
 * Handles CAMERA runtime permission; guides to Settings when permanently denied.
 *
 * For external QR scan deep-links: the `mihonsync://` intent-filter in AndroidManifest.xml
 * routes to MainActivity which navigates to [eu.kanade.presentation.more.settings.screen.SyncPairingConfirmScreen].
 */
class ScanSyncPairingScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val syncPreferences = remember { context.appGraph.syncPreferences }

        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        var permissionPermanentlyDenied by remember { mutableStateOf(false) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                permissionPermanentlyDenied = true
            }
        }

        LaunchedEffect(Unit) {
            if (!hasCameraPermission) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Returns true when the raw text was a valid pairing code and has been applied.
        val submitCode: (String) -> Boolean = remember(syncPreferences) {
            { raw ->
                val parsed = mihon.sync.transport.http.SyncPairingCode.parseOrNull(raw)
                if (parsed != null) {
                    syncPreferences.updateFromPairingCode(parsed)
                    context.toast(SyncStrings.scanPairingSuccess)
                    navigator.pop()
                    true
                } else {
                    false
                }
            }
        }

        ScanSyncPairingContent(
            hasCameraPermission = hasCameraPermission,
            permissionPermanentlyDenied = permissionPermanentlyDenied,
            onBack = { navigator.pop() },
            onSubmitCode = submitCode,
            onInvalidCode = { context.toast(SyncStrings.scanPairingInvalidCode) },
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
private fun ScanSyncPairingContent(
    hasCameraPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    onBack: () -> Unit,
    onSubmitCode: (String) -> Boolean,
    onInvalidCode: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = SyncStrings.scanPairing,
                navigateUp = onBack,
            )
        },
    ) { paddingValues ->
        when {
            permissionPermanentlyDenied -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            SyncStrings.scanPairingCameraPermissionDenied,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            hasCameraPermission -> {
                CameraQrScanner(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding()),
                    hint = SyncStrings.scanPairingHint,
                    invalidCodeMessage = SyncStrings.scanPairingInvalidCode,
                    onQrResult = { text -> onSubmitCode(text) },
                    onSubmitCode = onSubmitCode,
                    onInvalidCode = onInvalidCode,
                )
            }
            else -> {
                // Waiting for permission response
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(SyncStrings.scanPairingHint)
                }
            }
        }
    }
}

/**
 * Composable that hosts a CameraX preview + ImageAnalysis QR decode pipeline with a
 * scanner-style overlay (dimmed frame, corner brackets, animated scan line) and a
 * bottom hint card + manual-code entry.
 *
 * @param onQrResult Called on the main thread with the decoded text. Return true to stop scanning.
 */
@Composable
private fun CameraQrScanner(
    modifier: Modifier = Modifier,
    hint: String,
    invalidCodeMessage: String,
    onQrResult: (String) -> Boolean,
    onSubmitCode: (String) -> Boolean,
    onInvalidCode: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var bottomMessage by remember { mutableStateOf<String?>(null) }
    var lastInvalidTimestamp by remember { mutableLongStateOf(0L) }
    var scanning by remember { mutableStateOf(true) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    val scanTransition = rememberInfiniteTransition(label = "scanline")
    val scanProgress by scanTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanlineY",
    )

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener(
                    {
                        val provider = providerFuture.get()
                        cameraProvider = provider

                        val preview = Preview.Builder().build().apply {
                            // Java setter-only: Kotlin cannot synthesize a property without a getter
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val reader = MultiFormatReader().apply {
                            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                        }

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (!scanning) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val result = decodeImageProxy(imageProxy, reader)
                            imageProxy.close()

                            if (result != null) {
                                // Hop to the main thread: state writes, toasts and navigation
                                // are not safe from the analyzer executor thread.
                                mainExecutor.execute {
                                    val shouldStop = onQrResult(result)
                                    if (shouldStop) {
                                        scanning = false
                                    } else {
                                        // Invalid mihon code: throttle the inline message
                                        val now = System.currentTimeMillis()
                                        if (now - lastInvalidTimestamp >= INVALID_CODE_THROTTLE_MS) {
                                            lastInvalidTimestamp = now
                                            bottomMessage = invalidCodeMessage
                                        }
                                    }
                                }
                            }
                        }

                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis,
                            )
                        } catch (_: Throwable) {}
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        ScannerOverlay(scanProgress = scanProgress)

        // Bottom hint card + manual entry
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val message = bottomMessage
            Text(
                text = message ?: hint,
                color = if (message != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.White
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
            TextButton(onClick = { showManualDialog = true }) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(SyncStrings.scanPairingManualInput, color = Color.White)
            }
        }
    }

    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text(SyncStrings.scanPairingManualInput) },
            text = {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    placeholder = { Text(SyncStrings.scanPairingManualHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onSubmitCode(manualCode)) {
                            showManualDialog = false
                        } else {
                            onInvalidCode()
                        }
                    },
                ) {
                    Text(SyncStrings.confirmPairingButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text(SyncStrings.cancel)
                }
            },
        )
    }
}

/**
 * Draws the scanner overlay: a dimmed scrim with a transparent rounded-rect cutout for the
 * scan frame, white corner brackets, a subtle frame border and an animated scan line.
 */
@Composable
private fun ScannerOverlay(scanProgress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val frameSizePx = FRAME_SIZE.toPx()
        val half = frameSizePx / 2f
        val centerX = size.width / 2f
        val centerY = size.height * FRAME_CENTER_FRACTION
        val frame = RoundRect(
            left = centerX - half,
            top = centerY - half,
            right = centerX + half,
            bottom = centerY + half,
            cornerRadius = CornerRadius(FRAME_CORNER.toPx()),
        )

        // Dim scrim with the scan frame cut out
        val dimPath = Path().apply {
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            addRoundRect(frame)
            fillType = PathFillType.EvenOdd
        }
        drawPath(dimPath, Color.Black.copy(alpha = 0.45f))

        // Subtle full frame border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(frame.left, frame.top),
            size = Size(frame.right - frame.left, frame.bottom - frame.top),
            cornerRadius = CornerRadius(FRAME_CORNER.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )

        // Corner brackets
        val bracket = 28.dp.toPx()
        val stroke = 4.dp.toPx()
        val strokeStyle = Stroke(width = stroke)
        fun bracketPath(start: Offset, horizontal: Offset, vertical: Offset): Path = Path().apply {
            moveTo(start.x + horizontal.x * bracket, start.y + horizontal.y * bracket)
            lineTo(start.x, start.y)
            lineTo(start.x + vertical.x * bracket, start.y + vertical.y * bracket)
        }
        drawPath(
            bracketPath(Offset(frame.left, frame.top), Offset(1f, 0f), Offset(0f, 1f)),
            Color.White,
            style = strokeStyle,
        )
        drawPath(
            bracketPath(Offset(frame.right, frame.top), Offset(-1f, 0f), Offset(0f, 1f)),
            Color.White,
            style = strokeStyle,
        )
        drawPath(
            bracketPath(Offset(frame.left, frame.bottom), Offset(1f, 0f), Offset(0f, -1f)),
            Color.White,
            style = strokeStyle,
        )
        drawPath(
            bracketPath(Offset(frame.right, frame.bottom), Offset(-1f, 0f), Offset(0f, -1f)),
            Color.White,
            style = strokeStyle,
        )

        // Animated scan line sweeping the frame vertically
        val lineY = frame.top + (frame.bottom - frame.top) * scanProgress
        val lineInset = 14.dp.toPx()
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.9f), Color.Transparent),
            ),
            start = Offset(frame.left + lineInset, lineY),
            end = Offset(frame.right - lineInset, lineY),
            strokeWidth = 2.5.dp.toPx(),
        )
    }
}

private val FRAME_SIZE: Dp = 260.dp
private val FRAME_CORNER: Dp = 28.dp

/** Vertical position of the scan frame center as a fraction of the viewport height. */
private const val FRAME_CENTER_FRACTION = 0.42f

/**
 * Decodes a QR code from an [ImageProxy] using zxing.  Returns the decoded text or null.
 * Must be called off the main thread (inside the ImageAnalysis analyzer executor).
 *
 * Handles YUV_420_888 layouts where the Y plane has a rowStride/pixelStride larger than
 * the image width (common on some devices) by copying Y samples row by row.
 */
private fun decodeImageProxy(imageProxy: ImageProxy, reader: MultiFormatReader): String? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val yuv = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(yuv, 0, minOf(yuv.size, buffer.remaining()))
        } else {
            val row = ByteArray(rowStride)
            var outIndex = 0
            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.capacity()) break
                buffer.position(rowStart)
                val toRead = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, toRead)
                var col = 0
                while (col < width && col * pixelStride < toRead) {
                    yuv[outIndex++] = row[col * pixelStride]
                    col++
                }
            }
            if (outIndex < width * height) return null
        }

        val source = PlanarYUVLuminanceSource(
            yuv,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        val binary = com.google.zxing.BinaryBitmap(HybridBinarizer(source))
        try {
            reader.decodeWithState(binary).text
        } finally {
            reader.reset()
        }
    } catch (_: Throwable) {
        null
    }
}

private const val INVALID_CODE_THROTTLE_MS = 1500L
