package tw.bigspring.phonetracker.ar

/** Immutable raw ARCore pose. No coordinate conversion or origin subtraction occurs on phone. */
data class PoseSample(val frameNs: Long, val px: Float, val py: Float, val pz: Float,
    val qx: Float, val qy: Float, val qz: Float, val qw: Float, val trackingState: Int,
    val failureReason: Int, val batteryPct: Int, val appFps: Int, val sessionResumed: Boolean = false)
