package takagi.ru.monica.ui.scanner

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class QrCameraScanSession(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    mlKitFormats: List<Int>,
    private val generation: Int,
    private val diagnostics: QrScannerDiagnostics?,
    private val onCandidates: (List<String>, barcodeCount: Int, durationMs: Long) -> Boolean,
    private val onRestartRequested: (QrScanRestartReason) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val controller = LifecycleCameraController(appContext)
    private val scanner: BarcodeScanner = createMlKitBarcodeScanner(mlKitFormats)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val healthPolicy = QrScanHealthPolicy()
    private val active = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val analysisResourcesClosed = AtomicBoolean(false)
    private val frameLifecycle = QrFrameLifecycleGate()
    private val previewStreaming = AtomicBoolean(false)
    private val previewObserver = Observer<PreviewView.StreamState> { state ->
        val streaming = state == PreviewView.StreamState.STREAMING
        val changed = previewStreaming.getAndSet(streaming) != streaming
        if (changed) diagnostics?.logPreviewState(streaming)
        if (streaming) {
            requestCenterFocus(reason = "preview_streaming")
        }
    }

    fun start() {
        check(active.compareAndSet(false, true)) { "QR camera session already started" }
        val startedAt = SystemClock.elapsedRealtime()
        healthPolicy.onSessionStarted(startedAt)
        diagnostics?.logSessionStarted(generation)
        diagnostics?.logCameraProviderRequested(1)

        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        controller.setImageAnalysisResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        )
        controller.setTapToFocusEnabled(true)
        controller.setImageAnalysisAnalyzer(analysisExecutor, ::analyzeFrame)

        previewView.controller = controller
        previewView.previewStreamState.observeForever(previewObserver)

        runCatching {
            controller.bindToLifecycle(lifecycleOwner)
        }.onFailure { error ->
            diagnostics?.logCameraBindFailed(error)
            requestRestart(QrScanRestartReason.FrameStreamStopped)
            return
        }

        controller.initializationFuture.addListener(
            {
                if (!active.get()) return@addListener
                runCatching { controller.initializationFuture.get() }
                    .onSuccess {
                        diagnostics?.logCameraBindSuccess(SystemClock.elapsedRealtime() - startedAt)
                        previewView.post { requestCenterFocus(reason = "session_start") }
                    }
                    .onFailure { error ->
                        diagnostics?.logCameraProviderFailed(error)
                        requestRestart(QrScanRestartReason.FrameStreamStopped)
                    }
            },
            mainExecutor
        )
    }

    fun tick(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (!active.get()) return
        when (val action = healthPolicy.nextAction(nowMs, previewStreaming.get())) {
            QrScanHealthAction.None -> Unit
            QrScanHealthAction.Refocus -> {
                if (requestCenterFocus(reason = "periodic")) {
                    healthPolicy.onRefocusRequested(nowMs)
                }
            }
            is QrScanHealthAction.Restart -> requestRestart(action.reason)
        }
    }

    fun isProcessingFrame(): Boolean = frameLifecycle.isFrameInFlight()

    private fun analyzeFrame(imageProxy: ImageProxy) {
        when (frameLifecycle.tryAcquireFrame()) {
            QrFrameAdmission.Stopped -> {
                imageProxy.close()
                return
            }
            QrFrameAdmission.Busy -> {
                diagnostics?.logFrameSkipped()
                imageProxy.close()
                return
            }
            QrFrameAdmission.Acquired -> Unit
        }

        val frameStartedAt = SystemClock.elapsedRealtime()
        healthPolicy.onFrameStarted(frameStartedAt)
        diagnostics?.logFrameStarted(imageProxy.imageInfo.rotationDegrees)
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            diagnostics?.logFrameMissingImage()
            healthPolicy.onFrameCompleted(SystemClock.elapsedRealtime(), succeeded = false)
            imageProxy.close()
            releaseFrame()
            return
        }

        val frameFinished = AtomicBoolean(false)
        fun finishFrame(succeeded: Boolean) {
            if (!frameFinished.compareAndSet(false, true)) return
            healthPolicy.onFrameCompleted(SystemClock.elapsedRealtime(), succeeded)
            runCatching { imageProxy.close() }
            releaseFrame()
        }

        val inputImage = runCatching {
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        }.onFailure { error ->
            diagnostics?.logFrameFailure(error)
            finishFrame(succeeded = false)
        }.getOrNull() ?: return
        val task = runCatching { scanner.process(inputImage) }
            .onFailure { error ->
                diagnostics?.logFrameFailure(error)
                finishFrame(succeeded = false)
            }
            .getOrNull()
            ?: return

        val succeeded = AtomicBoolean(false)
        task.addOnSuccessListener { barcodes ->
            succeeded.set(true)
            val candidates = barcodes
                .flatMap { it.candidateValues() }
                .distinct()
            val durationMs = SystemClock.elapsedRealtime() - frameStartedAt
            val matched = if (active.get()) {
                runCatching {
                    onCandidates(candidates, barcodes.size, durationMs)
                }.getOrDefault(false)
            } else {
                false
            }
            diagnostics?.logFrameSuccess(
                durationMs = durationMs,
                barcodeCount = barcodes.size,
                candidateCount = candidates.size,
                matched = matched
            )
        }.addOnFailureListener { error ->
            diagnostics?.logFrameFailure(error)
        }.addOnCompleteListener {
            finishFrame(succeeded.get())
        }
    }

    private fun releaseFrame() {
        if (frameLifecycle.completeFrame()) {
            closeAnalysisResources()
        }
    }

    private fun requestCenterFocus(reason: String): Boolean {
        if (!active.get() || !previewStreaming.get()) return false
        if (previewView.width <= 0 || previewView.height <= 0) return false

        return runCatching {
            val point = previewView.meteringPointFactory.createPoint(
                previewView.width / 2f,
                previewView.height / 2f,
                FOCUS_POINT_SIZE
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or
                    FocusMeteringAction.FLAG_AE or
                    FocusMeteringAction.FLAG_AWB
            )
                .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                .build()
            val cameraControl = controller.cameraControl ?: return@runCatching false
            cameraControl.startFocusAndMetering(action)
            diagnostics?.logRefocusRequested(reason)
            true
        }.onFailure { error ->
            diagnostics?.logRefocusFailed(error)
        }.getOrDefault(false)
    }

    private fun requestRestart(reason: QrScanRestartReason) {
        if (!active.compareAndSet(true, false)) return
        diagnostics?.logSessionRestartRequested(reason)
        closeResources()
        mainExecutor.execute { onRestartRequested(reason) }
    }

    override fun close() {
        active.set(false)
        closeResources()
    }

    private fun closeResources() {
        if (!closed.compareAndSet(false, true)) return
        val processingFrame = frameLifecycle.isFrameInFlight()
        val releaseAnalysisResources = frameLifecycle.requestStop()
        diagnostics?.logDispose(processingFrame)
        previewStreaming.set(false)
        runCatching { previewView.previewStreamState.removeObserver(previewObserver) }
        runCatching { controller.clearImageAnalysisAnalyzer() }
        runCatching { controller.unbind() }
        runCatching { previewView.controller = null }
        if (releaseAnalysisResources) {
            closeAnalysisResources()
        }
    }

    private fun closeAnalysisResources() {
        if (!analysisResourcesClosed.compareAndSet(false, true)) return
        runCatching { scanner.close() }
        analysisExecutor.shutdown()
    }

    private companion object {
        private const val ANALYSIS_WIDTH = 1280
        private const val ANALYSIS_HEIGHT = 960
        private const val FOCUS_POINT_SIZE = 0.24f
        private const val FOCUS_AUTO_CANCEL_SECONDS = 3L
    }
}

internal fun Collection<BarcodeFormat>.toMlKitFormatList(): List<Int> {
    val mapped = mapNotNull { format ->
        when (format) {
            BarcodeFormat.QR_CODE -> Barcode.FORMAT_QR_CODE
            BarcodeFormat.CODE_128 -> Barcode.FORMAT_CODE_128
            BarcodeFormat.CODE_39 -> Barcode.FORMAT_CODE_39
            BarcodeFormat.CODE_93 -> Barcode.FORMAT_CODE_93
            BarcodeFormat.EAN_13 -> Barcode.FORMAT_EAN_13
            BarcodeFormat.EAN_8 -> Barcode.FORMAT_EAN_8
            BarcodeFormat.UPC_A -> Barcode.FORMAT_UPC_A
            BarcodeFormat.UPC_E -> Barcode.FORMAT_UPC_E
            BarcodeFormat.ITF -> Barcode.FORMAT_ITF
            BarcodeFormat.CODABAR -> Barcode.FORMAT_CODABAR
            BarcodeFormat.DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
            BarcodeFormat.AZTEC -> Barcode.FORMAT_AZTEC
            BarcodeFormat.PDF_417 -> Barcode.FORMAT_PDF417
            else -> null
        }
    }.distinct()
    return mapped.ifEmpty { listOf(Barcode.FORMAT_ALL_FORMATS) }
}

internal fun createMlKitBarcodeScanner(formats: List<Int>): BarcodeScanner {
    val builder = BarcodeScannerOptions.Builder()
    if (formats.size == 1) {
        builder.setBarcodeFormats(formats.first())
    } else {
        builder.setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
    }
    return BarcodeScanning.getClient(builder.build())
}

internal fun Barcode.candidateValues(): List<String> {
    return listOfNotNull(
        rawValue,
        displayValue,
        url?.url
    ).mapNotNull { value ->
        value.trim().takeIf(String::isNotBlank)
    }
}
