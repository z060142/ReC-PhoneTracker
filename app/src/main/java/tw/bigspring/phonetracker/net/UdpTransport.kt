package tw.bigspring.phonetracker.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpTransport : Transport {
    private var socket: DatagramSocket? = null
    private var target: InetAddress? = null
    private var port = 0
    override fun connect(host: String, port: Int) { close(); target = InetAddress.getByName(host); this.port = port; socket = DatagramSocket().apply { soTimeout = 250 } }
    override fun send(bytes: ByteArray) { val s = socket ?: error("not connected"); s.send(DatagramPacket(bytes, bytes.size, target, port)) }
    override fun receive(): ByteArray? {
        return try {
            val data = ByteArray(256)
            val packet = DatagramPacket(data, data.size)
            val currentSocket = socket ?: return null
            currentSocket.receive(packet)
            data.copyOf(packet.length)
        } catch (_: SocketTimeoutException) { null }
    }
    override fun close() { socket?.close(); socket = null }
}
