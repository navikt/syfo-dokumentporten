package no.nav.syfo.application

import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.texas.TexasEnvironment
import no.nav.syfo.application.valkey.ValkeyEnvironment
import java.time.Duration

interface Environment {
    val database: DatabaseEnvironment
    val texas: TexasEnvironment
    val clientProperties: ClientProperties
    val publicIngressUrl: String
    val valkeyEnvironment: ValkeyEnvironment
    val kafka: KafkaEnvironment
    val varselPublishPendingGracePeriod: Duration
    val documentCleanupInterval: Duration?
    val enableApiOnlyJob: Boolean
    val enableDocumentCleanupJob: Boolean
}

const val NAIS_DATABASE_ENV_PREFIX = "SYFO_DOKUMENTPORTEN_DB"
const val VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES_ENV = "VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES"
const val DOCUMENT_CLEANUP_INTERVAL_MINUTES_ENV = "DOCUMENT_CLEANUP_INTERVAL_MINUTES"
private const val DEFAULT_VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES = 1L

data class NaisEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment.createFromEnvVars(),
    override val texas: TexasEnvironment = TexasEnvironment.createFromEnvVars(),
    override val clientProperties: ClientProperties = ClientProperties.createFromEnvVars(),
    override val publicIngressUrl: String = getEnvVar("PUBLIC_INGRESS_URL"),
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createFromEnvVars(),
    override val kafka: KafkaEnvironment = KafkaEnvironment.createFromEnvVars(),
    override val varselPublishPendingGracePeriod: Duration = getVarselPublishPendingGracePeriod(),
    override val documentCleanupInterval: Duration? = getDocumentCleanupInterval(),
    override val enableApiOnlyJob: Boolean = getEnvVar("ENABLE_API_ONLY_JOB", "false").toBoolean(),
    override val enableDocumentCleanupJob: Boolean = getEnvVar("ENABLE_DOCUMENT_CLEANUP_JOB", "false").toBoolean(),

) : Environment

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

private fun getVarselPublishPendingGracePeriod(): Duration {
    val gracePeriodMinutes = getEnvVar(
        VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES_ENV,
        DEFAULT_VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES.toString(),
    ).toLongOrNull() ?: throw RuntimeException(
        "Invalid variable \"$VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES_ENV\": must be a whole number of minutes",
    )

    require(gracePeriodMinutes >= 0) {
        "Variable \"$VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES_ENV\" must be zero or greater"
    }

    return Duration.ofMinutes(gracePeriodMinutes)
}

private fun getDocumentCleanupInterval(): Duration? =
    parseDocumentCleanupInterval(System.getenv(DOCUMENT_CLEANUP_INTERVAL_MINUTES_ENV))

internal fun parseDocumentCleanupInterval(rawValue: String?): Duration? {
    val intervalMinutes = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val intervalMinutesAsLong = intervalMinutes.toLongOrNull() ?: throw RuntimeException(
        "Invalid variable \"$DOCUMENT_CLEANUP_INTERVAL_MINUTES_ENV\": must be a whole number of minutes",
    )

    // Zero would make delay(0) spin in the active task loop.
    require(intervalMinutesAsLong > 0) {
        "Variable \"$DOCUMENT_CLEANUP_INTERVAL_MINUTES_ENV\" must be greater than zero"
    }

    return Duration.ofMinutes(intervalMinutesAsLong)
}

fun isLocalEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "local"

fun isProdEnv(): Boolean = getEnvVar("NAIS_CLUSTER_NAME", "local") == "prod-gcp"

data class LocalEnvironment(
    override val database: DatabaseEnvironment = DatabaseEnvironment.createForLocal(),
    override val texas: TexasEnvironment = TexasEnvironment.createForLocal(),
    override val clientProperties: ClientProperties = ClientProperties.createForLocal(),
    override val publicIngressUrl: String = "http://localhost:8080",
    override val valkeyEnvironment: ValkeyEnvironment = ValkeyEnvironment.createForLocal(),
    override val kafka: KafkaEnvironment = KafkaEnvironment.createForLocal(),
    override val varselPublishPendingGracePeriod: Duration = Duration.ofMinutes(
        DEFAULT_VARSEL_PUBLISH_PENDING_GRACE_PERIOD_MINUTES
    ),
    override val documentCleanupInterval: Duration? = null,
    override val enableApiOnlyJob: Boolean = true,
    override val enableDocumentCleanupJob: Boolean = false,
) : Environment
