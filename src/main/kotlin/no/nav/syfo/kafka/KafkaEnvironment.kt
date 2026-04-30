package no.nav.syfo.kafka

data class KafkaEnvironment(
    val brokers: String,
    val truststorePath: String,
    val keystorePath: String,
    val credstorePassword: String,
    val varselbusTopic: String,
) {
    companion object {
        fun createFromEnvVars() = KafkaEnvironment(
            brokers = System.getenv("KAFKA_BROKERS") ?: throw RuntimeException("Missing KAFKA_BROKERS"),
            truststorePath = System.getenv("KAFKA_TRUSTSTORE_PATH") ?: "",
            keystorePath = System.getenv("KAFKA_KEYSTORE_PATH") ?: "",
            credstorePassword = System.getenv("KAFKA_CREDSTORE_PASSWORD") ?: "",
            varselbusTopic = System.getenv("ESYFOVARSEL_TOPIC") ?: "team-esyfo.varselbus",
        )

        fun createForLocal() = KafkaEnvironment(
            brokers = "localhost:9092",
            truststorePath = "",
            keystorePath = "",
            credstorePassword = "",
            varselbusTopic = "team-esyfo.varselbus",
        )
    }
}
