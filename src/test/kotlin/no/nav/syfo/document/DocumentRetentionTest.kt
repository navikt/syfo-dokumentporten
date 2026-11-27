package no.nav.syfo.document

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DocumentRetentionTest :
    DescribeSpec({
        it("keeps content through the last available UTC calendar day") {
            val examples = listOf(
                "2026-06-01T10:00:00Z" to "2026-10-02T00:00:00Z",
                "2026-02-28T23:59:59Z" to "2026-06-29T00:00:00Z",
                "2024-02-29T00:00:00Z" to "2024-06-30T00:00:00Z",
                "2025-10-31T12:00:00Z" to "2026-03-01T00:00:00Z",
            )
            for ((createdString, expiryString) in examples) {
                val created = Instant.parse(createdString)
                val expiry = Instant.parse(expiryString)
                DocumentRetention.expiresAt(created) shouldBe expiry
                created.isBefore(DocumentRetention.cleanupCutoff(expiry.minusNanos(1))) shouldBe false
                created.isBefore(DocumentRetention.cleanupCutoff(expiry)) shouldBe true
            }
        }

        it("uses the UTC storage date even when the supplied timestamp crosses a local date boundary") {
            val created = Instant.parse("2026-06-02T00:30:00+02:00")
            DocumentRetention.expiresAt(created) shouldBe Instant.parse("2026-10-02T00:00:00Z")
            DocumentRetention.cleanupCutoff(Instant.parse("2026-10-02T00:30:00+02:00")) shouldBe
                Instant.parse("2026-06-01T00:00:00Z")
        }

        it("inverts expiry around midnight and short months over a complete Gregorian 400-year cycle") {
            var today = LocalDate.of(2000, 1, 1)
            val end = today.plusYears(400)
            while (today.isBefore(end)) {
                val midnight = today.atStartOfDay().toInstant(ZoneOffset.UTC)
                for (now in listOf(midnight, midnight.plusSeconds(86_400).minusNanos(1))) {
                    val cutoff = DocumentRetention.cleanupCutoff(now)
                    val candidate = today.minusMonths(4)
                    // Probe both sides of all potentially clamped dates, including intraday storage.
                    for (dayOffset in -3L..3L) {
                        val storageDay = candidate.plusDays(dayOffset)
                        val start = storageDay.atStartOfDay().toInstant(ZoneOffset.UTC)
                        val expectedExpiry = storageDay.plusMonths(4).plusDays(1)
                            .atStartOfDay().toInstant(ZoneOffset.UTC)
                        for (created in listOf(start, start.plusSeconds(86_400).minusNanos(1))) {
                            DocumentRetention.expiresAt(created) shouldBe expectedExpiry
                            created.isBefore(cutoff) shouldBe !expectedExpiry.isAfter(now)
                        }
                    }
                    DocumentRetention.expiresAt(cutoff).isAfter(now) shouldBe true
                    DocumentRetention.expiresAt(cutoff.minusNanos(1)).isAfter(now) shouldBe false
                }
                today = today.plusDays(1)
            }
        }
    })
