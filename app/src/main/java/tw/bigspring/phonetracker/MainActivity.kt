package tw.bigspring.phonetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tw.bigspring.phonetracker.ar.*
import tw.bigspring.phonetracker.net.*

class MainActivity : ComponentActivity() {
    private lateinit var ar: ArSessionController
    private lateinit var settingsRepository: SettingsRepository
    private var sender: PoseSender? = null; private var listener: ControlListener? = null; private var statusJob: Job? = null
    private var settings = TrackerSettings(); private var started by mutableStateOf(false); private var showSettings by mutableStateOf(false)
    private var poseText by mutableStateOf("等待 ARCore…"); private var senderStatus by mutableStateOf(SenderStatus()); private var lastUiNs = 0L
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startTracker() else poseText = "相機權限被拒絕；請到系統設定允許。" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); window.attributes = window.attributes.apply { screenBrightness = .2f }
        settingsRepository = SettingsRepository(this)
        lifecycleScope.launch { settingsRepository.settings.collectLatest { value -> settings = value; if (::ar.isInitialized) restartNetwork(value) } }
        setContent { MaterialTheme { TrackerUi() } }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startTracker() else permission.launch(Manifest.permission.CAMERA)
    }
    private fun startTracker() {
        if (::ar.isInitialized) return
        if (!ArCoreApk.getInstance().checkAvailability(this).isSupported) { poseText = "ARCore 不可用。請更新 Google Play 服務 for AR。"; return }
        ar = ArSessionController(this); ar.create(); restartNetwork(settings); started = true
    }
    private fun restartNetwork(value: TrackerSettings) {
        listener?.close(); sender?.close(); statusJob?.cancel()
        val transport: Transport = if (value.transport == "TCP") TcpTransport() else UdpTransport()
        try {
            transport.connect(value.host, value.port)
            sender = PoseSender(transport, value.host, value.port).also { it.maxRateHz.set(value.rateHz) }
            listener = ControlListener(transport, sender!!)
            statusJob = lifecycleScope.launch { sender!!.status.collectLatest { senderStatus = it } }
        } catch (e: Exception) { senderStatus = SenderStatus(false, error = e.message ?: "connect failed"); transport.close() }
    }
    private fun onPose(sample: PoseSample) {
        sender?.offer(sample); val now = System.nanoTime(); if (now - lastUiNs < 200_000_000L) return; lastUiNs = now
        poseText = "x %+.3f  y %+.3f  z %+.3f\n%d fps · %s · raw ARCore".format(sample.px, sample.py, sample.pz, sample.appFps, when (sample.trackingState) { 2 -> "TRACKING"; 1 -> "PAUSED"; else -> "STOPPED" })
    }
    @Composable private fun TrackerUi() = Box(Modifier.fillMaxSize()) {
        if (started) AndroidView(factory = { context -> android.opengl.GLSurfaceView(context).apply { setEGLContextClientVersion(2); setRenderer(ArRenderer(ar, ::onPose)); renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY } }, modifier = Modifier.fillMaxSize())
        Card(Modifier.padding(top = 38.dp, start = 16.dp).align(Alignment.TopStart), colors = CardDefaults.cardColors(containerColor = Color(0xBB101010))) { Column(Modifier.padding(12.dp)) {
            Text(if (senderStatus.connected) "● 已連線 ${settings.host}:${settings.port} · ${settings.transport}" else "○ 未連線 ${senderStatus.error ?: ""}", color = if (senderStatus.connected) Color(0xFF72E3A6) else Color(0xFFFFCC66))
            Text(poseText, color = Color.White); Text("seq ${senderStatus.seq} · rate cap ${if (settings.rateHz == 0) "native" else "${settings.rateHz} Hz"}", color = Color.LightGray)
        } }
        Row(Modifier.padding(20.dp).align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ showSettings = true }) { Text("設定") }; Button({ sender?.recenter() }) { Text("RECENTER") } }
        if (showSettings) SettingsDialog(settings, { showSettings = false }) { value -> showSettings = false; lifecycleScope.launch { settingsRepository.save(value) } }
    }
    @Composable private fun SettingsDialog(initial: TrackerSettings, dismiss: () -> Unit, save: (TrackerSettings) -> Unit) {
        var host by remember { mutableStateOf(initial.host) }; var port by remember { mutableStateOf(initial.port.toString()) }; var rate by remember { mutableStateOf(initial.rateHz.toString()) }; var tcp by remember { mutableStateOf(initial.transport == "TCP") }
        AlertDialog(onDismissRequest = dismiss, title = { Text("Tracker 設定") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, label = { Text("PC IP / hostname") }, singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Pose port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(rate, { rate = it.filter(Char::isDigit) }, label = { Text("送出頻率上限（0 = 原生）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) { Button({ tcp = false }) { Text(if (!tcp) "✓ UDP" else "UDP") }; TextButton({ tcp = true }) { Text(if (tcp) "✓ TCP" else "TCP") } }
        } }, confirmButton = { Button({ save(TrackerSettings(host.trim(), port.toIntOrNull()?.coerceIn(1,65535) ?: 9050, if (tcp) "TCP" else "UDP", rate.toIntOrNull()?.coerceIn(0,240) ?: 0)) }) { Text("儲存並重連") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
    }
    override fun onResume() { super.onResume(); if (::ar.isInitialized) ar.resume() }
    override fun onPause() { if (::ar.isInitialized) ar.pause(); super.onPause() }
    override fun onDestroy() { statusJob?.cancel(); listener?.close(); sender?.close(); if (::ar.isInitialized) ar.close(); super.onDestroy() }
}
