package tw.bigspring.phonetracker.net

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

class ControlListener(private val transport: Transport, private val sender: PoseSender, private val onSetFocus: (Float) -> Unit = {}) : AutoCloseable {
    private val running = AtomicBoolean(true); private val thread = Thread({ while (running.get()) try { PacketCodec.decodeControl(transport.receive() ?: continue)?.let(::handle) } catch (_: Exception) {} }, "ControlListener").apply { isDaemon = true }
    init { thread.start() }
    private fun handle(c: PacketCodec.Control) { when (c.cmd) { PacketCodec.PING -> transport.send(PacketCodec.control(PacketCodec.PONG, c.seq, c.pcNs, (SystemClock.elapsedRealtimeNanos() shr 10).toInt())); PacketCodec.RECENTER -> sender.recenter(); PacketCodec.SET_RATE -> sender.maxRateHz.set(c.param); PacketCodec.PAUSE -> sender.setPaused(true); PacketCodec.RESUME -> sender.setPaused(false); PacketCodec.SET_FOCUS -> onSetFocus(c.param / 1000f) } }
    override fun close() { running.set(false); thread.interrupt() }
}
