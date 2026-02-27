package dji.sampleV5.aircraft.tests.config

object MqttConfig {
    const val HOST = "192.168.1.11"
    const val PORT = 1883

    object Topics {
        const val TELEMETRY = "drone/telemetry"
        const val COMMAND   = "drone/command"
        const val STATUS    = "drone/status"
    }
}
