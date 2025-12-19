package no.nav.syfo.document.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.syfo.document.api.v1.dto.DocumentType
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class SqlFilterBuilderTest : DescribeSpec({

    describe("buildFilterString") {
        it("should return empty string when no filters are set") {
            val builder = SqlFilterBuilder()
            builder.buildFilterString() shouldBe ""
        }

        it("should build WHERE clause with single filter") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", "PENDING")

            builder.buildFilterString() shouldBe "WHERE status = ?"
        }

        it("should build WHERE clause with multiple filters joined by AND") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", "PENDING")
            builder.filterParam("is_read", false)

            builder.buildFilterString() shouldBe "WHERE status = ? AND is_read = ?"
        }

        it("should support custom comparison operators") {
            val builder = SqlFilterBuilder()
            builder.filterParam("created", Instant.now(), SqlFilterBuilder.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
            builder.filterParam("updated", Instant.now(), SqlFilterBuilder.ComparisonOperator.LESS_THAN_OR_EQUAL_TO)

            val filterString = builder.buildFilterString()
            filterString shouldContain "created >= ?"
            filterString shouldContain "updated <= ?"
        }

        it("should ignore null values") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", null)
            builder.filterParam("type", "DIALOGMOTE")

            builder.buildFilterString() shouldBe "WHERE type = ?"
        }

        it("should include ORDER BY clause when orderBy is set") {
            val builder = SqlFilterBuilder()
            builder.orderBy = Page.OrderBy.CREATED

            builder.buildFilterString() shouldBe "ORDER BY created DESC"
        }

        it("should use ASC when orderDirection is set to ASC") {
            val builder = SqlFilterBuilder()
            builder.orderBy = Page.OrderBy.CREATED
            builder.orderDirection = Page.OrderDirection.ASC

            builder.buildFilterString() shouldBe "ORDER BY created ASC"
        }

        it("should include LIMIT clause when limit is set") {
            val builder = SqlFilterBuilder()
            builder.limit = 50

            builder.buildFilterString() shouldBe "LIMIT 50"
        }

        it("should include OFFSET clause when offset is set") {
            val builder = SqlFilterBuilder()
            builder.offset = 100

            builder.buildFilterString() shouldBe "OFFSET 100"
        }

        it("should build complete filter string with all options") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", "PENDING")
            builder.filterParam("is_read", false)
            builder.orderBy = Page.OrderBy.CREATED
            builder.orderDirection = Page.OrderDirection.DESC
            builder.limit = 50
            builder.offset = 100

            val expected = "WHERE status = ? AND is_read = ? ORDER BY created DESC LIMIT 50 OFFSET 100"
            builder.buildFilterString() shouldBe expected
        }

        it("should allow same column with different operators for date ranges") {
            val builder = SqlFilterBuilder()
            val startDate = Instant.parse("2024-01-01T00:00:00Z")
            val endDate = Instant.parse("2024-12-31T23:59:59Z")

            builder.filterParam("created", startDate, SqlFilterBuilder.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
            builder.filterParam("created", endDate, SqlFilterBuilder.ComparisonOperator.LESS_THAN_OR_EQUAL_TO)

            val filterString = builder.buildFilterString()
            filterString shouldContain "created >= ?"
            filterString shouldContain "created <= ?"
            filterString shouldContain "AND"
        }
    }

    describe("buildStatement - parameter binding") {
        it("should bind String parameter") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", "PENDING")

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setString(1, "PENDING") }
        }

        it("should bind Boolean parameter") {
            val builder = SqlFilterBuilder()
            builder.filterParam("is_read", true)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setBoolean(1, true) }
        }

        it("should bind UUID parameter") {
            val builder = SqlFilterBuilder()
            val uuid = UUID.randomUUID()
            builder.filterParam("document_id", uuid)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setObject(1, uuid) }
        }

        it("should bind DocumentType parameter") {
            val builder = SqlFilterBuilder()
            builder.filterParam("type", DocumentType.DIALOGMOTE)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setObject(1, DocumentType.DIALOGMOTE, java.sql.Types.OTHER) }
        }

        it("should bind DocumentStatus parameter") {
            val builder = SqlFilterBuilder()
            builder.filterParam("status", DocumentStatus.PENDING)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setObject(1, DocumentStatus.PENDING, java.sql.Types.OTHER) }
        }

        it("should bind Instant parameter as Timestamp") {
            val builder = SqlFilterBuilder()
            val instant = Instant.parse("2024-06-15T10:30:00Z")
            builder.filterParam("created", instant)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setTimestamp(1, Timestamp.from(instant)) }
        }

        it("should bind Timestamp parameter") {
            val builder = SqlFilterBuilder()
            val timestamp = Timestamp.from(Instant.now())
            builder.filterParam("created", timestamp)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verify { mockStatement.setObject(1, timestamp) }
        }

        it("should bind multiple parameters in correct order") {
            val builder = SqlFilterBuilder()
            val uuid = UUID.randomUUID()
            builder.filterParam("status", "PENDING")
            builder.filterParam("is_read", false)
            builder.filterParam("document_id", uuid)

            val mockStatement = mockk<PreparedStatement>(relaxed = true)
            builder.buildStatement(mockStatement)

            verifySequence {
                mockStatement.setString(1, "PENDING")
                mockStatement.setBoolean(2, false)
                mockStatement.setObject(3, uuid)
            }
        }

        it("should throw IllegalArgumentException for unsupported type") {
            val builder = SqlFilterBuilder()
            builder.filterParam("some_field", listOf("unsupported"))

            val mockStatement = mockk<PreparedStatement>(relaxed = true)

            shouldThrow<IllegalArgumentException> {
                builder.buildStatement(mockStatement)
            }.message shouldContain "Unsupported parameter type"
        }

        it("should return the same PreparedStatement") {
            val builder = SqlFilterBuilder()
            val mockStatement = mockk<PreparedStatement>(relaxed = true)

            val result = builder.buildStatement(mockStatement)

            result shouldBe mockStatement
        }
    }

    describe("fluent API") {
        it("should support method chaining") {
            val builder = SqlFilterBuilder()
                .filterParam("status", "PENDING")
                .filterParam("is_read", false)
                .filterParam("type", DocumentType.DIALOGMOTE)

            builder.buildFilterString() shouldContain "status = ?"
            builder.buildFilterString() shouldContain "is_read = ?"
            builder.buildFilterString() shouldContain "type = ?"
        }
    }
})
