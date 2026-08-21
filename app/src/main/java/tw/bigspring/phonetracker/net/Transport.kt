package tw.bigspring.phonetracker.net

interface Transport : AutoCloseable {
    fun connect(host: String, port: Int)
    fun send(bytes: ByteArray)
    fun receive(): ByteArray?
    override fun close()
}
