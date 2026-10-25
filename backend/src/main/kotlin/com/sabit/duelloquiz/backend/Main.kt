package com.sabit.duelloquiz.backend

import com.sabit.duelloquiz.backend.admin.runDeleteUserCli
import com.sabit.duelloquiz.backend.admin.runPruneOrphansCli
import com.sabit.duelloquiz.backend.content.runImportCli
import io.ktor.server.netty.EngineMain
import kotlin.system.exitProcess

/**
 * Entry point of the distribution (`bin/duello-backend`):
 * - no arguments: start the HTTP server (application.conf);
 * - `import <dir>`: run the content importer once and exit;
 * - `delete-user (--email <a> | --uid <u>) (--dry-run | --yes)`: delete one account (OQ-102);
 * - `prune-orphans [--dry-run | --yes]`: delete profiles without a Firebase account.
 * See backend/README.md "Admin CLI".
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "import" -> exitProcess(runImportCli(args.drop(1)))
        "delete-user" -> exitProcess(runDeleteUserCli(args.drop(1)))
        "prune-orphans" -> exitProcess(runPruneOrphansCli(args.drop(1)))
        else -> EngineMain.main(args)
    }
}
