package no.nav.syfo.document

import java.time.Instant
import java.time.ZoneOffset

object DocumentRetention {
    private const val RETENTION_MONTHS = 4L

    fun expiresAt(created: Instant): Instant = created.atOffset(ZoneOffset.UTC).toLocalDate()
        .plusMonths(RETENTION_MONTHS)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)

    // Invert the calendar-date expiry while keeping the indexed predicate created < cutoff.
    // Month subtraction can clamp to a short month's end; include that date only when expired.
    fun cleanupCutoff(now: Instant): Instant {
        val today = now.atOffset(ZoneOffset.UTC).toLocalDate()
        val candidate = today.minusMonths(RETENTION_MONTHS)
        val cutoffDate = if (candidate.plusMonths(RETENTION_MONTHS).isBefore(today)) {
            candidate.plusDays(1)
        } else {
            candidate
        }
        return cutoffDate.atStartOfDay().toInstant(ZoneOffset.UTC)
    }
}
