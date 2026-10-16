plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.sabit.duelloquiz"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

application {
    // One entry point: no arguments starts the HTTP server, `import <dir>` runs the
    // content importer (see backend/README.md).
    mainClass.set("com.sabit.duelloquiz.backend.MainKt")
    applicationName = "duello-backend"
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    implementation(libs.firebase.admin)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    useJUnitPlatform()
    // Importer tests read the public samples and the JSON Schema from the monorepo.
    systemProperty("duello.repoRoot", rootDir.parentFile.absolutePath)
    // Optional: run DB tests against an existing PostgreSQL instead of Testcontainers.
    System.getenv("TEST_DB_URL")?.let { environment("TEST_DB_URL", it) }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    inputs.dir(rootDir.resolve("../content/samples")).withPropertyName("contentSamples").optional()
    inputs.file(rootDir.resolve("../content/schema/question.schema.json")).withPropertyName("questionSchema").optional()
}
