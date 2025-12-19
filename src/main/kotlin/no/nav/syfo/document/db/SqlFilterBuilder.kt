package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.DocumentType
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.*
import kotlin.math.absoluteValue

/**
 * Example usage:
 *
 * ```
 * val preparedStatement = SqlFilterBuilder().run {
 *      filterParam("id", someId)
 *      filterParam("status", someStatus)
 *      filterParam("created", someCreatedAfter, ComparisonOperator.GREATER_THAN_OR_EQUAL_TO)
 *      limit = 50
 *      orderBy = Page.OrderBy.CREATED
 *      descending = Page.OrderDirection.DESC
 *      buildStatement(
 *          connection.prepareStatement("select * from table ${buildFilterString()}")
 *       )
 *     }
 * preparedStatement.use { it.executeQuery() }
 * ```
 * */
class SqlFilterBuilder {
    private val filters = mutableListOf<Filter>()

    var limit: Int? = null
    var orderBy: Page.OrderBy? = null
    var orderDirection: Page.OrderDirection = Page.OrderDirection.DESC
    var offset: Int? = null

    fun filterParam(
        name: String,
        value: Any?,
        comparisonOperator: ComparisonOperator = ComparisonOperator.EQUALS
    ): SqlFilterBuilder {
        if (value != null) {
            filters.add(Filter(name, value, comparisonOperator.symbol))
        }
        return this
    }

    fun buildFilterString(): String {
        val whereClause = if (filters.isNotEmpty()) {
            "WHERE ${filters.joinToString(" AND ") { "${it.name} ${it.operator} ?" }}"
        } else ""
        val orderClause = orderBy?.let { "ORDER BY ${it.columnName} ${orderDirection.name}" } ?: ""
        val limitClause = limit?.absoluteValue?.let { "LIMIT $it" } ?: ""
        val offsetClause = offset?.absoluteValue?.let { "OFFSET $it" } ?: ""

        return listOf(whereClause, orderClause, limitClause, offsetClause)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    // Decided to let the user do the final composition of the SQL, to keep the static analysis of the SQL intact
    fun buildStatement(preparedStatement: PreparedStatement): PreparedStatement {
        filters.forEachIndexed { idx, filter ->
            val parameterIndex = idx + 1
            when (val value = filter.value) {
                is String -> preparedStatement.setString(parameterIndex, value)
                is Boolean -> preparedStatement.setBoolean(parameterIndex, value)
                is UUID -> preparedStatement.setObject(parameterIndex, value)
                is DocumentType -> preparedStatement.setObject(parameterIndex, value, Types.OTHER)
                is DocumentStatus -> preparedStatement.setObject(parameterIndex, value, Types.OTHER)
                is Timestamp -> preparedStatement.setObject(parameterIndex, value)
                is Instant -> preparedStatement.setTimestamp(parameterIndex, Timestamp.from(value))
                else -> throw IllegalArgumentException("Unsupported parameter type: ${value.javaClass.simpleName}")
            }
        }
        return preparedStatement
    }

    private data class Filter(val name: String, val value: Any, val operator: String)
    enum class ComparisonOperator(val symbol: String) {
        EQUALS("="),
        NOT_EQUALS("!="),
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_THAN_OR_EQUAL_TO(">="),
        LESS_THAN_OR_EQUAL_TO("<=")
    }
}
