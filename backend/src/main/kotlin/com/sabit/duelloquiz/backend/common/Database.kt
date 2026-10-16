package com.sabit.duelloquiz.backend.common

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import javax.sql.DataSource

/**
 * Connection pool + Exposed database. All repository work goes through [tx], which
 * runs a blocking JDBC transaction on the IO dispatcher.
 */
class Db(val dataSource: DataSource) {
    val database: Database = Database.connect(dataSource)

    suspend fun <T> tx(block: JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) {
            transaction(database, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) { block() }
        }

    fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    companion object {
        fun dataSource(config: DbConfig): HikariDataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                poolName = "duello-db"
                validate()
            },
        )

        /** Applies Flyway migrations from `classpath:db/migration`. */
        fun migrate(dataSource: DataSource) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }
}
