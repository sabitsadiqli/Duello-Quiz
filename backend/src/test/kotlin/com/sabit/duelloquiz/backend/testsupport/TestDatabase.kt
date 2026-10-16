package com.sabit.duelloquiz.backend.testsupport

import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.common.DbConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * One PostgreSQL per test JVM: Testcontainers (same image as infra/docker-compose.yml),
 * or an existing server via `TEST_DB_URL` (+ `TEST_DB_USER`, `TEST_DB_PASSWORD`).
 * Without Docker and without `TEST_DB_URL` the database tests are skipped, unless
 * `REQUIRE_DB_TESTS=true` (set in CI) turns that into a failure.
 */
object TestDatabase {
    private const val IMAGE = "postgres:17.11-alpine"

    private val dataSource: HikariDataSource by lazy {
        val url = System.getenv("TEST_DB_URL")
        val config = if (url != null) {
            DbConfig(url, System.getenv("TEST_DB_USER") ?: "duello", System.getenv("TEST_DB_PASSWORD").orEmpty(), 8)
        } else {
            val container = PostgreSQLContainer(IMAGE).also { it.start() }
            DbConfig(container.jdbcUrl, container.username, container.password, 8)
        }
        Db.dataSource(config)
    }

    val available: Boolean by lazy {
        System.getenv("TEST_DB_URL") != null || runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
    }

    fun assumeAvailable() {
        if (!available && System.getenv("REQUIRE_DB_TESTS") == "true") {
            error("REQUIRE_DB_TESTS=true but neither Docker nor TEST_DB_URL is available")
        }
        Assumptions.assumeTrue(available, "No Docker and no TEST_DB_URL: database tests skipped")
    }

    fun flyway(): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()

    /** Drops everything and applies all migrations: every test starts from an empty, seeded schema. */
    fun freshDb(): Db {
        assumeAvailable()
        flyway().apply {
            clean()
            migrate()
        }
        return Db(dataSource)
    }
}

/** Base class for tests that need PostgreSQL. */
abstract class DbTest {
    protected lateinit var db: Db

    @BeforeEach
    fun setUpDatabase() {
        db = TestDatabase.freshDb()
    }
}
