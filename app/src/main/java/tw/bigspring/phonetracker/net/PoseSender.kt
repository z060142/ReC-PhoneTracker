package tw.bigspring.phonetracker.net

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.bigspring.phonetracker.ar.PoseSample

class LatestSlot<T> { private val lock = Object(); private var value: T? = null
    fun offer(v: T) = synchronized(lock) { value = v; lock.notifyAll() }
    fun take(): T = synchronized(lock) { while (value == null) lock.wait(); value.also { value = null }!! }
}
data class SenderStatus(val connected: Boolean = false, val seq: Int = 0, val error: String? = null)
class PoseSender(private val transport: Transport, private val host: String, private val port: Int) : AutoCloseable {
    private val slot = LatestSlot<PoseSample>(); private val running = AtomicBoolean(true); private val seq = AtomicInteger(); val originEpoch = AtomicInteger(); private val originMark = AtomicBoolean(false); private val paused = AtomicBoolean(false); var maxRateHz = AtomicInteger(0)
    private val _status = MutableStateFlow(SenderStatus()); val status = _status.asStateFlow()
    private val thread = Thread({ run() }, "PoseSender").apply { isDaemon = true }
    init { thread.start() }
    fun offer(s: PoseSample) { if (!paused.get()) slot.offer(s) }
    fun recenter() { originEpoch.incrementAndGet(); originMark.set(true) }
    fun setPaused(value: Boolean) { paused.set(value) }
    private fun run() { var lastSend = 0L; while (running.get()) try { val s = slot.take(); val interval = maxRateHz.get().let { if (it > 0) 1_000_000_000L / it else 0 }; if (interval > 0 && s.frameNs - lastSend < interval) continue; lastSend = s.frameNs; var flags = if (s.trackingState == 2) PacketCodec.POSE_VALID else PacketCodec.HEARTBEAT; if (s.sessionResumed) flags = flags or PacketCodec.SESSION_RESUMED; if (originMark.getAndSet(false)) flags = flags or PacketCodec.ORIGIN_MARK; val packetSeq = seq.getAndIncrement(); transport.send(PacketCodec.pose(s, packetSeq, originEpoch.get(), flags)); _status.value = SenderStatus(true, packetSeq) } catch (_: InterruptedException) { break } catch (e: Exception) { _status.value = SenderStatus(false, seq.get(), e.message ?: "network error"); Thread.sleep(2000); try { transport.connect(host, port) } catch (_: Exception) {} } }
    override fun close() { running.set(false); thread.interrupt(); transport.close(); _status.value = SenderStatus(false, seq.get()) }
}
