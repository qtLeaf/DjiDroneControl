package dji.sampleV5.aircraft.tests.config

object MqttConfig {
    var HOST = "192.168.1.11"
    const val PORT = 1883

    object Topics {
        const val TELEMETRY = "drone/telemetry"
        const val COMMAND   = "drone/command"
        const val STATUS    = "drone/status"
    }

    /**
     * Updates the HOST if the provided string is a valid IPv4 address.
     * Format: 0-255 . 0-255 . 0-255 . 0-255
     */
    fun setHost(host: String): Boolean {
        val ipv4Pattern = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$".toRegex()

        return if (host.matches(ipv4Pattern)) {
            HOST = host
            true
        } else {
            false
        }
    }
}
