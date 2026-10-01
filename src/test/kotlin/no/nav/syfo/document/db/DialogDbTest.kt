package no.nav.syfo.document.db

import dialogEntity
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.syfo.TestDB
import java.sql.Timestamp
import java.time.Instant

class DialogDbTest :
    DescribeSpec({
        val testDb = TestDB.database
        val dialogDAO = DialogDAO(testDb)

        fun updateDialogApiOnlyAndCreated(dialogportenUUID: java.util.UUID, apiOnly: Boolean, created: Instant,) {
            testDb.connection.use { connection ->
                connection.prepareStatement(
                    """
                        UPDATE dialog
                        SET dialogporten_api_only = ?,
                            created = ?,
                            updated = ?
                        WHERE dialogporten_uuid = ?
                    """.trimIndent()
                ).use { preparedStatement ->
                    preparedStatement.setBoolean(1, apiOnly)
                    preparedStatement.setTimestamp(2, Timestamp.from(created))
                    preparedStatement.setTimestamp(3, Timestamp.from(Instant.now()))
                    preparedStatement.setObject(4, dialogportenUUID)
                    preparedStatement.executeUpdate()
                }
                connection.commit()
            }
        }

        beforeTest {
            TestDB.clearAllData()
        }

        describe("DialogDb -> insert") {
            it("should return a generated id") {
                // Arrange
                val dialogEntity = dialogEntity()
                // Act
                val id = dialogDAO.insertDialog(dialogEntity).id
                // Assert
                id shouldNotBe null
                id shouldBeGreaterThan 0L
            }

            it("should persist the dialog with the correct fields") {
                // Arrange
                val dialogEntity = dialogEntity()
                // Act
                // Assert
                val retrievedDialog = dialogDAO.insertDialog(dialogEntity)
                retrievedDialog.shouldBeEqualUsingFields({
                    // exclude auto-generated fields from comparison
                    excludedProperties = setOf(
                        PersistedDialogEntity::id,
                        PersistedDialogEntity::created,
                        PersistedDialogEntity::updated,
                    )
                    dialogEntity
                })
            }
        }
        describe("DialogDb -> getByFnrAndOrgNumber") {
            it("should retrieve the correct dialog") {
                // Arrange
                val dialogEntity = dialogEntity()
                val persistedDialog = dialogDAO.insertDialog(dialogEntity)
                // Act
                val retrievedDialog = dialogDAO.getByFnrAndOrgNumber(
                    fnr = dialogEntity.fnr,
                    orgNumber = dialogEntity.orgNumber
                )
                // Assert
                retrievedDialog shouldNotBe null
                retrievedDialog?.shouldBeEqualUsingFields({
                    // exclude auto-generated fields from comparison
                    excludedProperties = setOf(
                        PersistedDialogEntity::id,
                        PersistedDialogEntity::created,
                        PersistedDialogEntity::updated,
                    )
                    persistedDialog
                })
            }

            it("should return null for non-existing fnr and orgNumber") {
                // Arrange
                val dialogEntity = dialogEntity()
                dialogDAO.insertDialog(dialogEntity)
                // Act
                val retrievedDialog = dialogDAO.getByFnrAndOrgNumber(
                    fnr = "non-existing-fnr",
                    orgNumber = "non-existing-orgNumber"
                )
                // Assert
                retrievedDialog shouldBe null
            }
        }
    })
