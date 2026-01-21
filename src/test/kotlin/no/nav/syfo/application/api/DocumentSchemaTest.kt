package no.nav.syfo.application.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import no.nav.syfo.document.api.v1.dto.Document
import org.yaml.snakeyaml.Yaml
import kotlin.reflect.full.memberProperties

class DocumentSchemaTest :
    StringSpec({

        "openapi Linemanager schema matches Linemanager and nested Manager properties" {
            val yamlText = this::class.java.classLoader
                .getResource("openapi/documentation.yaml")!!
                .readText()

            val yaml = Yaml()
            val root = yaml.load<Map<String, Any>>(yamlText)

            val schemas = root["components"] as Map<*, *>
            val schemaMap = (schemas["schemas"] as Map<*, *>)

            val documentSchema = schemaMap["Document"] as Map<*, *>
            val documentProps =
                (documentSchema["properties"] as Map<*, *>).keys.map { it as String }.toSet()

            // Reflect Kotlin Linemanager
            val lmKotlinProps = Document::class.memberProperties.map { it.name }.toSet()
            // Root properties must include at least those from the Kotlin data class
            documentProps.shouldContainAll(lmKotlinProps)
        }
    })
