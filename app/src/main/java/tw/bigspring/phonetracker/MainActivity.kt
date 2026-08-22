package tw.bigspring.phonetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import kotlin.math.log10
import kotlin.math.pow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tw.bigspring.phonetracker.ar.*
import tw.bigspring.phonetracker.net.*

class MainActivity : ComponentActivity() {
    private lateinit var ar: ArSessionController
    private lateinit var settingsRepository: SettingsRepository
    private var sender: PoseSender? = null; private var listener: ControlListener? = null; private var statusJob: Job? = null
    private var settings = TrackerSettings(); private var started by mutableStateOf(false); private var showSettings by mutableStateOf(false)
    private var lens by mutableStateOf(LensState()); private var showLens by mutableStateOf(false); private var lensLoaded = false
    private var poseText by mutableStateOf("Waiting for ARCore…"); private var senderStatus by mutableStateOf(SenderStatus()); private var lastUiNs = 0L
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startTracker() else poseText = "Camera permission denied; enable it in system settings." }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); window.attributes = window.attributes.apply { screenBrightness = .2f }
        settingsRepository = SettingsRepository(this)
        // Settings and lens state share one DataStore file, so every lens write re-emits this
        // flow; without distinctUntilChanged each EV tap tore the network down and raced the
        // sender thread into a crash.
        lifecycleScope.launch { settingsRepository.settings.distinctUntilChanged().collectLatest { value -> settings = value; if (::ar.isInitialized) restartNetwork(value) } }
        lifecycleScope.launch { settingsRepository.lens.collectLatest { value -> if (!lensLoaded) { lensLoaded = true; lens = value; sender?.setLens(value) } } }
        setContent { MaterialTheme { TrackerUi() } }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startTracker() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun startTracker() {
        if (::ar.isInitialized) return
        if (!ArCoreApk.getInstance().checkAvailability(this).isSupported) { poseText = "ARCore unavailable. Please update Google Play Services for AR."; return }
        ar = ArSessionController(this); ar.create(); restartNetwork(settings); started = true
    }
    private fun restartNetwork(value: TrackerSettings) {
        listener?.close(); sender?.close(); statusJob?.cancel()
        val transport: Transport = if (value.transport == "TCP") TcpTransport() else UdpTransport()
        try {
            transport.connect(value.host, value.port)
            sender = PoseSender(transport, value.host, value.port).also { it.maxRateHz.set(value.rateHz) }
            listener = ControlListener(transport, sender!!) { metres ->
                runOnUiThread { onLensChanged(lens.copy(focusDistanceM = metres), true) }
            }
            sender!!.setLens(lens)
            statusJob = lifecycleScope.launch { sender!!.status.collectLatest { senderStatus = it } }
        } catch (e: Exception) { senderStatus = SenderStatus(false, error = e.message ?: "connect failed"); transport.close() }
    }
    private fun onLensChanged(value: LensState, persist: Boolean) {
        lens = value; sender?.setLens(value)
        if (persist) lifecycleScope.launch { settingsRepository.saveLens(value) }
    }
    private fun onPose(sample: PoseSample) {
        sender?.offer(sample); val now = System.nanoTime(); if (now - lastUiNs < 200_000_000L) return; lastUiNs = now
        poseText = "x %+.3f  y %+.3f  z %+.3f\n%d fps · %s · raw ARCore".format(sample.px, sample.py, sample.pz, sample.appFps, when (sample.trackingState) { 2 -> "TRACKING"; 1 -> "PAUSED"; else -> "STOPPED" })
    }
    @Composable private fun TrackerUi() = Box(Modifier.fillMaxSize()) {
        if (started) AndroidView(factory = { context -> android.opengl.GLSurfaceView(context).apply { setEGLContextClientVersion(2); setRenderer(ArRenderer(ar, ::onPose)); renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY } }, modifier = Modifier.fillMaxSize())
        Card(Modifier.padding(top = 38.dp, start = 16.dp).align(Alignment.TopStart), colors = CardDefaults.cardColors(containerColor = Color(0xBB101010))) { Column(Modifier.padding(12.dp)) {
            Text(if (senderStatus.connected) "● Connected ${settings.host}:${settings.port} · ${settings.transport}" else "○ Disconnected ${senderStatus.error ?: ""}", color = if (senderStatus.connected) Color(0xFF72E3A6) else Color(0xFFFFCC66))
            Text(poseText, color = Color.White); Text("seq ${senderStatus.seq} · rate cap ${if (settings.rateHz == 0) "native" else "${settings.rateHz} Hz"}", color = Color.LightGray)
        } }
        Row(Modifier.padding(20.dp).align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ showSettings = true }) { Text("Settings") }; Button({ showLens = !showLens }) { Text(if (showLens) "Hide Lens" else "Lens") }; Button({ sender?.recenter() }) { Text("RECENTER") } }
        if (showLens) LensPanel()
        if (showSettings) SettingsDialog(settings, { showSettings = false }) { value -> showSettings = false; lifecycleScope.launch { settingsRepository.save(value) } }
    }
    @Composable private fun BoxScope.LensPanel() = Card(
        // Compact: the panel must fit a landscape phone above the button row; scroll is the
        // safety net for very short screens.
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 72.dp).align(Alignment.BottomCenter).fillMaxWidth()
            .heightIn(max = 290.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC101010))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).verticalScroll(rememberScrollState())) {
            LensSlider("Focal Length", "%.0f mm".format(lens.focalLengthMm), lens.focalLengthMm, 12f..200f) { v, done -> onLensChanged(lens.copy(focalLengthMm = v), done) }
            LensSlider("Aperture", "f/%.1f".format(lens.aperture), lens.aperture, 1f..22f) { v, done -> onLensChanged(lens.copy(aperture = v), done) }
            // Log scale like a real focus ring (fine up close, coarse far out), with the top
            // of the travel acting as the infinity stop.
            val focusInfinity = lens.focusDistanceM >= 9999f
            val focusPos = if (focusInfinity) 1f else (log10(lens.focusDistanceM.coerceIn(0.1f, 100f) / 0.1f) / 3f)
            LensSlider("Focus", if (focusInfinity) "∞" else "%.2f m".format(lens.focusDistanceM), focusPos, 0f..1f) { v, done ->
                val d = if (v >= 0.995f) 10000f else 0.1f * 10f.pow(v * 3f)
                onLensChanged(lens.copy(focusDistanceM = d), done)
            }
            // ISO fader snaps to standard sensitivity stops; the engine maps it onto a
            // real sensor noise curve to drive film grain.
            val isoStops = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600)
            val isoIndex = isoStops.indexOf(lens.iso).let { if (it < 0) 2 else it }
            LensSlider("ISO", "${lens.iso}", isoIndex.toFloat(), 0f..(isoStops.size - 1).toFloat()) { v, done ->
                onLensChanged(lens.copy(iso = isoStops[v.toInt().coerceIn(0, isoStops.size - 1)]), done)
            }

            Row(Modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EV", color = Color.LightGray, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium)
                for (step in -3..3) {
                    val selected = lens.ev == step
                    Button({ onLensChanged(lens.copy(ev = step), true) }, modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text(if (step > 0) "+$step" else "$step", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DOF", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                Switch(lens.depthOfField, { onLensChanged(lens.copy(depthOfField = it), true) }, modifier = Modifier.scale(0.75f))
                Spacer(Modifier.width(8.dp))
                Text("Exposure", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                Switch(lens.exposure, { onLensChanged(lens.copy(exposure = it), true) }, modifier = Modifier.scale(0.75f))
                Spacer(Modifier.weight(1f))
                Button({ sender?.requestAutofocus(lens) }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("AF", style = MaterialTheme.typography.labelMedium) }
                TextButton({ onLensChanged(LensState(), true) }) { Text("Reset", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
    @Composable private fun LensSlider(label: String, readout: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float, Boolean) -> Unit) =
        Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.LightGray, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall)
            Slider(value, { onChange(it, false) }, modifier = Modifier.weight(1f).height(32.dp), valueRange = range, onValueChangeFinished = { onChange(value, true) })
            Text(readout, color = Color.White, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
        }
    @Composable private fun SettingsDialog(initial: TrackerSettings, dismiss: () -> Unit, save: (TrackerSettings) -> Unit) {
        var host by remember { mutableStateOf(initial.host) }; var port by remember { mutableStateOf(initial.port.toString()) }; var rate by remember { mutableStateOf(initial.rateHz.toString()) }; var tcp by remember { mutableStateOf(initial.transport == "TCP") }
        AlertDialog(onDismissRequest = dismiss, title = { Text("Tracker Settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, label = { Text("PC IP / hostname") }, singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Pose port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(rate, { rate = it.filter(Char::isDigit) }, label = { Text("Send rate cap (0 = native)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) { Button({ tcp = false }) { Text(if (!tcp) "✓ UDP" else "UDP") }; TextButton({ tcp = true }) { Text(if (tcp) "✓ TCP" else "TCP") } }
        } }, confirmButton = { Button({ save(TrackerSettings(host.trim(), port.toIntOrNull()?.coerceIn(1,65535) ?: 9050, if (tcp) "TCP" else "UDP", rate.toIntOrNull()?.coerceIn(0,240) ?: 0)) }) { Text("Save & Reconnect") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
    }
    override fun onResume() { super.onResume(); if (::ar.isInitialized) ar.resume() }
    override fun onPause() { if (::ar.isInitialized) ar.pause(); super.onPause() }
    override fun onDestroy() { statusJob?.cancel(); listener?.close(); sender?.close(); if (::ar.isInitialized) ar.close(); super.onDestroy() }
}
