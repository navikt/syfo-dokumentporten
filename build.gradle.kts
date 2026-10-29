plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "no.nav.syfo"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
}
dependencies {
    // Temporary security floors for transitive dependencies; remove when ktor, swagger-codegen
    // and logstash-logback-encoder request patched versions.
    implementation(platform(libs.jackson.bom))
    implementation(platform(libs.tools.jackson.bom))

    constraints {
        // The BOM platforms above already resolve patched versions, but GitHub's dependency graph
        // reports the requested transitive versions and ignores platform() constraints, so the
        // Dependabot alerts stay open without these explicit floors.
        //
        // jackson-databind 2.22.2 comes in via ktor-serialization-jackson / ktor-bom;
        // no patched ktor release exists yet. Same approach as navikt/syfo-budstikka#345.
        implementation(libs.jackson.databind) {
            because("CVE-2026-91776 (GHSA-wv8q-qhhj-9h54)")
        }
        // tools.jackson.core:jackson-core 3.0.1 comes in via logstash-logback-encoder -> tools.jackson:jackson-bom;
        // no patched encoder release exists yet.
        implementation(libs.tools.jackson.core) {
            because("GHSA-2m67-wjpj-xhg9")
        }
        // handlebars 4.3.1 comes in via ktor-server-openapi -> swagger-codegen;
        // no patched swagger-codegen/ktor release exists yet.
        implementation(libs.handlebars) {
            because("CVE-2026-55760 (GHSA-r4gv-qr8j-p3pg)")
        }
        // Guava 33.6.0-jre comes in via jwks-rsa (ktor-server-auth-jwt) and
        // swagger-parser/json-schema-validator (ktor-server-openapi).
        implementation(libs.guava) {
            because("CVE-2026-102554")
        }
    }

    implementation(libs.datafaker)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-apache5-jvm")
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)
    implementation(libs.kafka.clients)
    implementation(libs.logstash)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.swagger)
    implementation(libs.fasterxml.uuid)
    // Database
    implementation(libs.bundles.database)
    implementation(libs.bundles.exposed)
    // Metrics and Prometheus
    implementation(libs.ktor.server.micrometer)
    implementation(libs.micrometer.prometheus)
    implementation(libs.valkey.java)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.bundles.testcontainers) // Will want this eventually
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.ApplicationKt"
    }

    register("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()
        archiveFileName.set("app.jar")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
    }
    named("check") {
        dependsOn("ktlintCheck")
    }
}
