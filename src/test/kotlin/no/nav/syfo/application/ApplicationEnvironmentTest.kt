package no.nav.syfo.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class ApplicationEnvironmentTest :
    DescribeSpec({
        describe("parseDocumentCleanupInterval") {
            it("returns null when the value is absent") {
                parseDocumentCleanupInterval(null) shouldBe null
            }

            it("returns null when the value is empty") {
                parseDocumentCleanupInterval("") shouldBe null
            }

            it("returns null when the value contains only whitespace") {
                parseDocumentCleanupInterval("   ") shouldBe null
            }

            it("parses a whole number of minutes") {
                parseDocumentCleanupInterval("5") shouldBe Duration.ofMinutes(5)
            }

            it("trims whitespace before parsing") {
                parseDocumentCleanupInterval(" 5 ") shouldBe Duration.ofMinutes(5)
            }

            it("rejects zero to prevent a busy loop") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parseDocumentCleanupInterval("0")
                }

                exception::class shouldBe IllegalArgumentException::class
            }

            it("rejects negative values") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parseDocumentCleanupInterval("-1")
                }

                exception::class shouldBe IllegalArgumentException::class
            }

            it("rejects non-numeric values with a whole-number message") {
                val exception = shouldThrow<RuntimeException> {
                    parseDocumentCleanupInterval("abc")
                }

                exception::class shouldBe RuntimeException::class
                exception.message shouldBe
                    "Invalid variable \"$DOCUMENT_CLEANUP_INTERVAL_MINUTES_ENV\": must be a whole number of minutes"
            }
        }
    })
