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

        describe("DialogDb -> getDialogCandidatesWithApiOnlyTrue") {
            it("should return all matching dialog ids ordered by created ascending") {
                val oldestDialog = dialogDAO.insertDialog(dialogEntity())
                val nextDialog = dialogDAO.insertDialog(dialogEntity())
                val tooRecentDialog = dialogDAO.insertDialog(dialogEntity())
                val apiOnlyFalseDialog = dialogDAO.insertDialog(dialogEntity())

                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = oldestDialog.dialogportenUUID!!,
                    apiOnly = true,
                    created = Instant.parse("2026-05-02T00:00:00Z")
                )
                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = nextDialog.dialogportenUUID!!,
                    apiOnly = true,
                    created = Instant.parse("2026-05-03T00:00:00Z")
                )
                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = tooRecentDialog.dialogportenUUID!!,
                    apiOnly = true,
                    created = Instant.parse("2026-05-05T00:00:00Z")
                )
                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = apiOnlyFalseDialog.dialogportenUUID!!,
                    apiOnly = false,
                    created = Instant.parse("2026-05-01T00:00:00Z")
                )

                val candidates = dialogDAO.getDialogCandidatesWithApiOnlyTrue()

                candidates shouldContainExactly listOf(
                    oldestDialog.dialogportenUUID,
                    nextDialog.dialogportenUUID,
                )
            }
        }

        describe("DialogDb -> setDialogApiOnlyFalse") {
            it("should update only the selected dialog") {
                val dialogToUpdate = dialogDAO.insertDialog(dialogEntity())
                val otherDialog = dialogDAO.insertDialog(dialogEntity())

                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = dialogToUpdate.dialogportenUUID!!,
                    apiOnly = true,
                    created = Instant.parse("2026-05-01T00:00:00Z")
                )
                updateDialogApiOnlyAndCreated(
                    dialogportenUUID = otherDialog.dialogportenUUID!!,
                    apiOnly = true,
                    created = Instant.parse("2026-05-02T00:00:00Z")
                )

                dialogDAO.setDialogApiOnlyFalse(dialogToUpdate.dialogportenUUID)

                dialogDAO.getDialogCandidatesWithApiOnlyTrue() shouldContainExactly listOf(otherDialog.dialogportenUUID)
            }
        }
    })
