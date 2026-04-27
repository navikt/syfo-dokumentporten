package no.nav.syfo.application

import io.ktor.server.config.ApplicationConfig
import no.nav.syfo.document.api.v1.dto.DocumentType

data class DocumentConfig(
    val dialogRessurs: String,
    val dialogSummaryTemplate: String,
    val documents: Map<DocumentType, DocumentTypeDetails>,
) {
    init {
        require(dialogRessurs.isNotBlank()) {
            "Invalid dokumentporten config:\n- dokumentporten.dialogRessurs cannot be blank"
        }
        require(dialogSummaryTemplate.isNotBlank()) {
            "Invalid dokumentporten config:\n- dokumentporten.dialogSummaryTemplate cannot be blank"
        }
        require(dialogSummaryTemplate.contains(NAME_PLACEHOLDER)) {
            "Invalid dokumentporten config:\n- dokumentporten.dialogSummaryTemplate must contain $NAME_PLACEHOLDER placeholder"
        }

        val unsupportedTypes = documents.keys.filter { it == DocumentType.UNDEFINED }.map { it.name }
        require(unsupportedTypes.isEmpty()) {
            "Invalid dokumentporten config:\n- dokumentporten.documents contains unsupported DocumentType(s): ${unsupportedTypes.joinToString()}"
        }

        documents.forEach { (documentType, documentTypeDetails) ->
            require(documentTypeDetails.displayName.isNotBlank()) {
                "Invalid dokumentporten config:\n- dokumentporten.documents.${documentType.name}.displayName cannot be blank"
            }
            require(documentTypeDetails.altinnResource.isNotBlank()) {
                "Invalid dokumentporten config:\n- dokumentporten.documents.${documentType.name}.altinn.resource cannot be blank"
            }
        }

        val requiredDocumentTypes = supportedDocumentTypes()
        val missingDocumentTypes = requiredDocumentTypes
            .filterNot { documents.containsKey(it) }
            .map { it.name }
        require(missingDocumentTypes.isEmpty()) {
            "Invalid dokumentporten config:\n- Missing document config for DocumentType(s): ${missingDocumentTypes.joinToString()}"
        }
    }

    fun get(documentType: DocumentType): DocumentTypeDetails =
        documents[documentType] ?: throw IllegalArgumentException("Missing document config for ${documentType.name}")

    data class DocumentTypeDetails(val displayName: String, val altinnResource: String, val supportVarsel: Boolean)

    companion object {
        private const val ROOT_PATH = "dokumentporten"
        private const val DOCUMENTS_PATH = "documents"
        private const val NAME_PLACEHOLDER = "{name}"

        fun fromApplicationConfig(applicationConfig: ApplicationConfig): DocumentConfig {
            val rootConfig = runCatching { applicationConfig.config(ROOT_PATH) }
                .getOrElse {
                    throw IllegalArgumentException(
                        "Invalid dokumentporten config:\n- $ROOT_PATH section is required",
                        it
                    )
                }
            val errors = mutableListOf<String>()

            val dialogRessurs = rootConfig.requiredString(
                path = "dialogRessurs",
                fullPath = "$ROOT_PATH.dialogRessurs",
                errors = errors,
            )
            val dialogSummaryTemplate = rootConfig.requiredString(
                path = "dialogSummaryTemplate",
                fullPath = "$ROOT_PATH.dialogSummaryTemplate",
                errors = errors,
            )

            if (dialogSummaryTemplate != null && !dialogSummaryTemplate.contains(NAME_PLACEHOLDER)) {
                errors += "$ROOT_PATH.dialogSummaryTemplate must contain $NAME_PLACEHOLDER placeholder"
            }

            val configuredDocuments = rootConfig.configListOrEmpty(
                path = DOCUMENTS_PATH,
                fullPath = "$ROOT_PATH.$DOCUMENTS_PATH",
                errors = errors,
            )

            val documents = mutableMapOf<DocumentType, DocumentTypeDetails>()

            configuredDocuments.forEachIndexed { index, documentConfig ->
                val documentPath = "$ROOT_PATH.$DOCUMENTS_PATH[$index]"
                val name = documentConfig.requiredString(
                    path = "name",
                    fullPath = "$documentPath.name",
                    errors = errors,
                )
                val displayName = documentConfig.requiredString(
                    path = "displayName",
                    fullPath = "$documentPath.displayName",
                    errors = errors,
                )
                val altinnResource = documentConfig.requiredString(
                    path = "altinn.resource",
                    fullPath = "$documentPath.altinn.resource",
                    errors = errors,
                )
                val supportVarsel = documentConfig.requiredBoolean(
                    path = "supportVarsel",
                    fullPath = "$documentPath.supportVarsel",
                    errors = errors,
                )

                if (name == null) {
                    return@forEachIndexed
                }

                val documentType = supportedDocumentTypes().find { it.name == name }
                if (documentType == null) {
                    errors += "$documentPath.name '$name' does not match a supported DocumentType"
                    return@forEachIndexed
                }

                if (documents.containsKey(documentType)) {
                    errors += "$documentPath.name '$name' is duplicated in config"
                    return@forEachIndexed
                }

                if (displayName != null && altinnResource != null && supportVarsel != null) {
                    documents[documentType] = DocumentTypeDetails(
                        displayName = displayName,
                        altinnResource = altinnResource,
                        supportVarsel = supportVarsel,
                    )
                }
            }

            val missingDocumentTypes = supportedDocumentTypes()
                .filterNot { documents.containsKey(it) }
                .map { it.name }
            if (missingDocumentTypes.isNotEmpty()) {
                errors += "Missing document config for DocumentType(s): ${missingDocumentTypes.joinToString()}"
            }

            if (errors.isNotEmpty()) {
                throw IllegalArgumentException(
                    buildString {
                        appendLine("Invalid dokumentporten config:")
                        errors.forEach { appendLine("- $it") }
                    }.trimEnd()
                )
            }

            return DocumentConfig(
                dialogRessurs = dialogRessurs!!,
                dialogSummaryTemplate = dialogSummaryTemplate!!,
                documents = documents,
            )
        }

        private fun supportedDocumentTypes(): List<DocumentType> = DocumentType.entries
            .filterNot { it == DocumentType.UNDEFINED }
    }
}

private fun ApplicationConfig.requiredString(path: String, fullPath: String, errors: MutableList<String>): String? {
    val value = propertyOrNull(path)?.getString()
    return when {
        value == null -> {
            errors += "$fullPath is required"
            null
        }

        value.isBlank() -> {
            errors += "$fullPath cannot be blank"
            null
        }

        else -> value
    }
}

private fun ApplicationConfig.requiredBoolean(path: String, fullPath: String, errors: MutableList<String>): Boolean? {
    val value = propertyOrNull(path)?.getString()
    return when {
        value == null -> {
            errors += "$fullPath is required"
            null
        }

        value.isBlank() -> {
            errors += "$fullPath cannot be blank"
            null
        }

        else -> value.toBooleanStrictOrNull()
            ?: run {
                errors += "$fullPath must be true or false"
                null
            }
    }
}

private fun ApplicationConfig.configListOrEmpty(
    path: String,
    fullPath: String,
    errors: MutableList<String>,
): List<ApplicationConfig> = runCatching { configList(path) }
    .getOrElse {
        errors += "$fullPath is required"
        emptyList()
    }
