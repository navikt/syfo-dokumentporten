package no.nav.syfo.application

import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.texas.TexasEnvironment
import no.nav.syfo.application.valkey.ValkeyEnvironment
import no.nav.syfo.kafka.KafkaEnvironment

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val clientProperties: ClientProperties
    val publicIngressUrl: String
    val dialogportenIsApiOnly: Boolean
    val valkeyEnvironment: ValkeyEnvironment
    val kafka: KafkaEnvironment
}

const val NAIS_DATABASE_ENV_PREFIX = "SYFO_DOKUMENTPORTEN_DB"

data class NaisEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment.createFromEnvVars(),
    override val texas: TexasEnvironment = TexasEnvironment.createFromEnvVars(),
    override val clientProperties: ClientProperties = ClientProperties.createFromEnvVars(),
    override val publicIngressUrl: String = getEnvVar("PUBLIC_INGRESS_URL"),
    override val dialogportenIsApiOnly: Boolean = getEnvVar("DIALOGPORTEN_API_ONLY").toBoolean(),
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createFromEnvVars(),
    override val kafka: KafkaEnvironment = KafkaEnvironment.createFromEnvVars(),
) : Environment

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocalEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "local"

fun isProdEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-gcp"

data class LocalEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment.createForLocal(),
    override val texas: TexasEnvironment = TexasEnvironment.createForLocal(),
    override val clientProperties: ClientProperties = ClientProperties.createForLocal(),
    override val publicIngressUrl: String = "http://localhost:8080",
    override val dialogportenIsApiOnly: Boolean = true,
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createForLocal(),
    override val kafka: KafkaEnvironment = KafkaEnvironment.createForLocal(),
) : Environment
