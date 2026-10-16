package com.sabit.duelloquiz.backend

import com.sabit.duelloquiz.backend.content.runImportCli
import io.ktor.server.netty.EngineMain
import kotlin.system.exitProcess

/**
 * Entry point of the distribution (`bin/duello-backend`):
 * - no arguments: start the HTTP server (application.conf);
 * - `import <dir>`: run the content importer once and exit.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "import" -> exitProcess(runImportCli(args.drop(1)))
        else -> EngineMain.main(args)
    }
}
