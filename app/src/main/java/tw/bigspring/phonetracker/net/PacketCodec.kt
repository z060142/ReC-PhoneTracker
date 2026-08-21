package tw.bigspring.phonetracker.net

import java.nio.ByteBuffer
import java.nio.ByteOrder
import tw.bigspring.phonetracker.ar.PoseSample

object PacketCodec {
    const val POSE_MAGIC = 0x54443350; const val CONTROL_MAGIC = 0x54433350; const val VERSION: Short = 1
    const val POSE_VALID = 1; const val SESSION_RESUMED = 2; const val ORIGIN_MARK = 4; const val HEARTBEAT = 8
    const val PING: Short = 1; const val PONG: Short = 2; const val RECENTER: Short = 3; const val SET_RATE: Short = 4; const val PAUSE: Short = 5; const val RESUME: Short = 6
    data class Control(val cmd: Short, val seq: Int, val pcNs: Long, val param: Int)
    fun pose(sample: PoseSample, seq: Int, originEpoch: Int, flags: Int): ByteArray = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(POSE_MAGIC); putShort(VERSION); putShort(flags.toShort()); putInt(seq); putInt(originEpoch); putLong(System.nanoTime()); putLong(sample.frameNs)
        putFloat(sample.px); putFloat(sample.py); putFloat(sample.pz); putFloat(sample.qx); putFloat(sample.qy); putFloat(sample.qz); putFloat(sample.qw)
        put(sample.trackingState.toByte()); put(sample.failureReason.toByte()); put(sample.batteryPct.toByte()); put(sample.appFps.toByte())
    }.array()
    fun control(cmd: Short, seq: Int, pcNs: Long, param: Int = 0): ByteArray = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(CONTROL_MAGIC); putShort(VERSION); putShort(cmd); putInt(seq); putLong(pcNs); putInt(param); putInt(0) }.array()
    fun decodeControl(bytes: ByteArray): Control? = if (bytes.size != 28) null else ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).let { b -> if (b.int != CONTROL_MAGIC || b.short != VERSION) null else Control(b.short, b.int, b.long, b.int) }
}
