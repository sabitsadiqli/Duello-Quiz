package com.sabit.duelloquiz.backend.common

import java.util.Properties

/**
 * What `GET /health` reports about the running build: the Gradle project version
 * (`build-info.properties`, generated at build time) and the git commit from
 * `APP_GIT_SHA` (set by the Docker image build). Nothing else from the environment
 * is exposed; a commit that is not a plain hex SHA is ignored.
 */
data class BuildInfo(val version: String, val commit: String?) {
    companion object {
        const val COMMIT_ENV = "APP_GIT_SHA"
        private val SHA = Regex("^[0-9a-f]{7,40}$")
        private val VERSION = Regex("^[0-9A-Za-z.+-]{1,64}$")

        val current: BuildInfo by lazy { load(System.getenv(COMMIT_ENV)) }

        fun load(rawCommit: String?, resource: String = "/build-info.properties"): BuildInfo {
            val props = Properties()
            BuildInfo::class.java.getResourceAsStream(resource)?.use(props::load)
            return of(props.getProperty("version"), rawCommit)
        }

        fun of(rawVersion: String?, rawCommit: String?): BuildInfo = BuildInfo(
            version = rawVersion?.trim()?.takeIf(VERSION::matches) ?: "unknown",
            commit = rawCommit?.trim()?.lowercase()?.takeIf(SHA::matches),
        )
    }
}
