package tw.bigspring.phonetracker.net

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket

/** Length-prefixed TCP fallback; UDP remains the latency-oriented default. */
class TcpTransport : Transport {
    private var socket: Socket? = null; private var input: BufferedInputStream? = null; private var output: BufferedOutputStream? = null
    override fun connect(host: String, port: Int) { close(); socket = Socket(host, port).also { it.soTimeout = 250 }; input = BufferedInputStream(socket!!.getInputStream()); output = BufferedOutputStream(socket!!.getOutputStream()) }
    override fun send(bytes: ByteArray) { val o = output ?: error("not connected"); o.write(bytes.size ushr 8); o.write(bytes.size); o.write(bytes); o.flush() }
    override fun receive(): ByteArray? { val i = input ?: return null; val hi = i.read(); if (hi < 0) return null; val lo = i.read(); val n = (hi shl 8) or lo; return i.readNBytes(n) }
    override fun close() { socket?.close(); socket = null; input = null; output = null }
}
