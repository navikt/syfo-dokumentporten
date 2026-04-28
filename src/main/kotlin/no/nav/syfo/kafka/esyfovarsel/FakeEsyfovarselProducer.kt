package no.nav.syfo.kafka.esyfovarsel

import no.nav.syfo.util.logger

class FakeEsyfovarselProducer : IEsyfovarselProducer {
    private val logger = logger()

    override fun publish(key: String, hendelse: EsyfovarselHendelse) {
        logger.info("Would publish esyfovarsel message with key $key and type ${hendelse.type}")
    }

    override fun close() {
    }
}
