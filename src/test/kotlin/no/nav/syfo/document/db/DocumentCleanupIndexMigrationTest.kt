package no.nav.syfo.document.db

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.syfo.TestDB
import org.flywaydb.core.Flyway
import javax.sql.DataSource

private const val MIGRATION_TEST_SCHEMA = "document_cleanup_index_migration_test"
private const val CLEANUP_INDEX_NAME = "idx_document_cleanup_pending"

class DocumentCleanupIndexMigrationTest :
    DescribeSpec({
        val dataSource = TestDB.database.dataSource

        beforeTest {
            recreateSchema(dataSource)
        }

        afterTest {
            dropSchema(dataSource)
        }

        afterSpec {
            dropSchema(dataSource)
        }

        describe("V17 document cleanup pending index") {
            it("recreates an invalid index left by an interrupted concurrent build") {
                val v16Migration = migrateTo(dataSource, "16")
                v16Migration.success shouldBe true
                v16Migration.migrationsExecuted shouldBe 16
                createCleanupIndex(dataSource)

                val invalidIndexOid = indexState(dataSource).oid
                markIndexInvalid(dataSource)

                indexState(dataSource).indisvalid shouldBe false

                val migrationResult = migrateTo(dataSource, "17")
                migrationResult.success shouldBe true
                migrationResult.migrationsExecuted shouldBe 1

                val recreatedIndex = indexState(dataSource)
                recreatedIndex.oid shouldNotBe invalidIndexOid
                recreatedIndex.indisvalid shouldBe true
                recreatedIndex.indisready shouldBe true
                recreatedIndex.indexSchema shouldBe MIGRATION_TEST_SCHEMA
                recreatedIndex.tableSchema shouldBe MIGRATION_TEST_SCHEMA
                recreatedIndex.tableName shouldBe "document"
                normalizePredicate(recreatedIndex.predicate) shouldBe "content_deleted_at IS NULL"
            }
        }
    })

private data class IndexState(
    val oid: Long,
    val indisvalid: Boolean,
    val indisready: Boolean,
    val indexSchema: String,
    val tableSchema: String,
    val tableName: String,
    val predicate: String?,
)

private fun migrateTo(dataSource: DataSource, target: String) = Flyway.configure().run {
    locations("db")
    defaultSchema(MIGRATION_TEST_SCHEMA)
    schemas(MIGRATION_TEST_SCHEMA)
    target(target)
    configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
    dataSource(dataSource)
    load().migrate()
}

private fun recreateSchema(dataSource: DataSource) {
    dropSchema(dataSource)
    execute(dataSource, "CREATE SCHEMA $MIGRATION_TEST_SCHEMA")
}

private fun dropSchema(dataSource: DataSource) {
    execute(dataSource, "DROP SCHEMA IF EXISTS $MIGRATION_TEST_SCHEMA CASCADE")
}

private fun createCleanupIndex(dataSource: DataSource) {
    dataSource.connection.use { connection ->
        try {
            connection.createStatement().use { statement ->
                statement.execute("SET LOCAL search_path TO $MIGRATION_TEST_SCHEMA")
                statement.execute(
                    """
                    CREATE INDEX $CLEANUP_INDEX_NAME
                        ON document (created, id)
                        WHERE content_deleted_at IS NULL
                    """.trimIndent(),
                )
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun markIndexInvalid(dataSource: DataSource) {
    dataSource.connection.use { connection ->
        try {
            connection.prepareStatement(
                """
                UPDATE pg_index
                SET indisvalid = false
                FROM pg_class index_relation
                JOIN pg_namespace index_schema ON index_schema.oid = index_relation.relnamespace
                WHERE pg_index.indexrelid = index_relation.oid
                    AND index_schema.nspname = ?
                    AND index_relation.relname = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, MIGRATION_TEST_SCHEMA)
                statement.setString(2, CLEANUP_INDEX_NAME)
                statement.executeUpdate() shouldBe 1
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun indexState(dataSource: DataSource): IndexState = dataSource.connection.use { connection ->
    try {
        connection.prepareStatement(
            """
            SELECT
                index_relation.oid,
                index_state.indisvalid,
                index_state.indisready,
                index_schema.nspname AS index_schema,
                table_schema.nspname AS table_schema,
                table_relation.relname AS table_name,
                pg_get_expr(index_state.indpred, index_state.indrelid) AS predicate
            FROM pg_index index_state
            JOIN pg_class index_relation ON index_relation.oid = index_state.indexrelid
            JOIN pg_namespace index_schema ON index_schema.oid = index_relation.relnamespace
            JOIN pg_class table_relation ON table_relation.oid = index_state.indrelid
            JOIN pg_namespace table_schema ON table_schema.oid = table_relation.relnamespace
            WHERE index_schema.nspname = ?
                AND index_relation.relname = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, MIGRATION_TEST_SCHEMA)
            statement.setString(2, CLEANUP_INDEX_NAME)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "Expected $MIGRATION_TEST_SCHEMA.$CLEANUP_INDEX_NAME to exist" }
                IndexState(
                    oid = resultSet.getLong("oid"),
                    indisvalid = resultSet.getBoolean("indisvalid"),
                    indisready = resultSet.getBoolean("indisready"),
                    indexSchema = resultSet.getString("index_schema"),
                    tableSchema = resultSet.getString("table_schema"),
                    tableName = resultSet.getString("table_name"),
                    predicate = resultSet.getString("predicate"),
                )
            }
        }
    } finally {
        connection.rollback()
    }
}

private fun execute(dataSource: DataSource, sql: String) {
    dataSource.connection.use { connection ->
        try {
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
            connection.commit()
        } catch (exception: Exception) {
            connection.rollback()
            throw exception
        }
    }
}

private fun normalizePredicate(predicate: String?): String = requireNotNull(predicate)
    .replace(Regex("\\s+"), " ")
    .trim()
    .removePrefix("(")
    .removeSuffix(")")
