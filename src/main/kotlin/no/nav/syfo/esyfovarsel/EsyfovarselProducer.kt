package no.nav.syfo.esyfovarsel

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.util.jacksonMapper
import no.nav.syfo.util.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

interface IEsyfovarselProducer {
    fun publish(key: String, hendelse: EsyfovarselHendelse)

    fun close()
}

class EsyfovarselProducer(private val kafkaProducer: KafkaProducer<String, String>, private val topic: String,) :
    IEsyfovarselProducer {
    private val logger = logger()
    private val objectMapper = jacksonMapper()

    override fun publish(key: String, hendelse: EsyfovarselHendelse) {
        val json = objectMapper.writeValueAsString(hendelse)
        val metadata = kafkaProducer.send(ProducerRecord(topic, key, json)).get()
        logger.info(
            "Published esyfovarsel message to topic ${metadata.topic()}, partition ${metadata.partition()}, offset ${metadata.offset()}, key=$key"
        )
    }

    override fun close() {
        kafkaProducer.close()
    }
}

fun buildKafkaProducerProperties(kafkaEnv: KafkaEnvironment): Properties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaEnv.brokers)
    put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.RETRIES_CONFIG, Int.MAX_VALUE)
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
    put(ProducerConfig.CLIENT_ID_CONFIG, "syfo-dokumentporten")

    if (kafkaEnv.truststorePath.isNotEmpty()) {
        put("security.protocol", "SSL")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaEnv.truststorePath)
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaEnv.keystorePath)
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaEnv.credstorePassword)
        put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, kafkaEnv.credstorePassword)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaEnv.credstorePassword)
    }
}
