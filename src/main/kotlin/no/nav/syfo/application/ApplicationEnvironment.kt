package no.nav.syfo.application

import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.texas.TexasEnvironment
import no.nav.syfo.application.valkey.ValkeyEnvironment

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val clientProperties: ClientProperties
    val publicIngressUrl: String
    val valkeyEnvironment: ValkeyEnvironment
}

const val NAIS_DATABASE_ENV_PREFIX = "SYFO_DOKUMENTPORTEN_DB"

data class NaisEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment.createFromEnvVars(),
    override val texas: TexasEnvironment = TexasEnvironment.createFromEnvVars(),
    override val clientProperties: ClientProperties = ClientProperties.createFromEnvVars(),
    override val publicIngressUrl: String = getEnvVar("PUBLIC_INGRESS_URL"),
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createFromEnvVars(),
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
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createForLocal(),
) : Environment
