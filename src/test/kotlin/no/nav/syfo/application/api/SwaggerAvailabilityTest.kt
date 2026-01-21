package no.nav.syfo.application.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class SwaggerAvailabilityTest :
    StringSpec({
        "openapi yaml should be served" {
            testApplication {
                application { minimalSwaggerRouting() }
                val response = client.get("/openapi/documentation.yaml")
                response.status.value shouldBe 200
                response.bodyAsText().contains("openapi: 3.0.3") shouldBe true
            }
        }
        "swagger UI should be served" {
            testApplication {
                application { minimalSwaggerRouting() }
                val response = client.get("/swagger")
                response.status.value shouldBe 200
                // Swagger UI returns HTML
                response.contentType()?.match(ContentType.Text.Html) shouldBe true
                response.bodyAsText().contains("Swagger UI") shouldBe true
            }
        }
    })

internal fun Application.minimalSwaggerRouting() {
    routing {
        staticResources("/openapi", "openapi")
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
    }
}
