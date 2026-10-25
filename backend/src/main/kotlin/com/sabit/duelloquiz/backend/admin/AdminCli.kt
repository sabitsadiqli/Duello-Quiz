package com.sabit.duelloquiz.backend.admin

import com.sabit.duelloquiz.backend.auth.AuthProviderException
import com.sabit.duelloquiz.backend.auth.FirebaseAccountDeleter
import com.sabit.duelloquiz.backend.auth.FirebaseAuthDirectory
import com.sabit.duelloquiz.backend.auth.createFirebaseAuth
import com.sabit.duelloquiz.backend.common.AppConfig
import com.sabit.duelloquiz.backend.common.Db
import com.sabit.duelloquiz.backend.user.DisplayNamePolicy
import com.sabit.duelloquiz.backend.user.UserRepository
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.runBlocking
import java.io.PrintStream
import java.time.Clock

/**
 * Exit codes of the admin CLI tasks:
 * 0 done, nothing to do or dry run; 1 runtime error (database, Firebase);
 * 2 usage or configuration error; 3 refused (active duel, `--max` exceeded): nothing deleted.
 */
object AdminExit {
    const val OK = 0
    const val ERROR = 1
    const val USAGE = 2
    const val REFUSED = 3
}

data class DeleteUserArgs(val target: UserTarget, val dryRun: Boolean)

data class PruneOrphansArgs(val dryRun: Boolean, val max: Int, val pauseMs: Long)

sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Invalid(val message: String) : Parsed<Nothing>
}

const val DELETE_USER_USAGE = """Usage: duello-backend delete-user (--email <address> | --uid <firebaseUid>) (--dry-run | --yes)
  --dry-run  print what would be deleted, change nothing
  --yes      delete: the same as DELETE /v1/me plus the Firebase account"""

const val PRUNE_ORPHANS_USAGE = """Usage: duello-backend prune-orphans [--dry-run | --yes] [--max <n>] [--pause-ms <ms>]
  --dry-run      (default) list profiles whose Firebase account no longer exists
  --yes          delete them with the same code as DELETE /v1/me
  --max <n>      refuse to delete more than n profiles (default 20; guards against a wrong Firebase project)
  --pause-ms <ms> pause between Firebase calls (default 250)"""

private const val MAX_EMAIL_LENGTH = 254
private const val MAX_UID_LENGTH = 128
private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val DEFAULT_PRUNE_MAX = 20

/** Reads `--flag value` pairs and bare flags; unknown or repeated options are errors. */
private fun options(args: List<String>, valued: Set<String>, flags: Set<String>): Parsed<Map<String, String>> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val name = args[i]
        when (name) {
            in valued -> {
                val value = args.getOrNull(i + 1) ?: return Parsed.Invalid("$name needs a value")
                if (result.put(name, value) != null) return Parsed.Invalid("$name given twice")
                i += 2
            }
            in flags -> {
                if (result.put(name, "") != null) return Parsed.Invalid("$name given twice")
                i += 1
            }
            else -> return Parsed.Invalid("unknown argument")
        }
    }
    return Parsed.Ok(result)
}

fun parseDeleteUserArgs(args: List<String>): Parsed<DeleteUserArgs> {
    val opts = when (val p = options(args, setOf("--email", "--uid"), setOf("--dry-run", "--yes"))) {
        is Parsed.Invalid -> return p
        is Parsed.Ok -> p.value
    }
    val email = opts["--email"]?.trim()?.lowercase()
    val uid = opts["--uid"]?.trim()
    val target = when {
        email != null && uid != null -> return Parsed.Invalid("give either --email or --uid, not both")
        email != null -> {
            if (email.length > MAX_EMAIL_LENGTH || !EMAIL.matches(email)) return Parsed.Invalid("--email is not a valid address")
            UserTarget.Email(email)
        }
        uid != null -> {
            if (uid.isEmpty() || uid.length > MAX_UID_LENGTH || uid.any { it.isWhitespace() || it.isISOControl() }) {
                return Parsed.Invalid("--uid must be 1-$MAX_UID_LENGTH characters without whitespace")
            }
            UserTarget.Uid(uid)
        }
        else -> return Parsed.Invalid("--email or --uid is required")
    }
    val dryRun = "--dry-run" in opts
    val yes = "--yes" in opts
    return when {
        dryRun && yes -> Parsed.Invalid("give either --dry-run or --yes, not both")
        !dryRun && !yes -> Parsed.Invalid("add --dry-run to preview or --yes to delete")
        else -> Parsed.Ok(DeleteUserArgs(target, dryRun))
    }
}

fun parsePruneOrphansArgs(args: List<String>): Parsed<PruneOrphansArgs> {
    val opts = when (val p = options(args, setOf("--max", "--pause-ms"), setOf("--dry-run", "--yes"))) {
        is Parsed.Invalid -> return p
        is Parsed.Ok -> p.value
    }
    if ("--dry-run" in opts && "--yes" in opts) return Parsed.Invalid("give either --dry-run or --yes, not both")
    val max = opts["--max"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return Parsed.Invalid("--max must be a non-negative integer") }
    val pause = opts["--pause-ms"]?.let {
        it.toLongOrNull()?.takeIf { n -> n in 0..60_000 } ?: return Parsed.Invalid("--pause-ms must be 0..60000")
    }
    return Parsed.Ok(
        PruneOrphansArgs(
            dryRun = "--yes" !in opts,
            max = max ?: DEFAULT_PRUNE_MAX,
            pauseMs = pause ?: AccountAdmin.DEFAULT_PAUSE_MS,
        ),
    )
}

/** `duello-backend delete-user ...` (OQ-102). Never prints or logs the email. */
fun runDeleteUserCli(args: List<String>): Int {
    val parsed = when (val p = parseDeleteUserArgs(args)) {
        is Parsed.Invalid -> return usage(p.message, DELETE_USER_USAGE)
        is Parsed.Ok -> p.value
    }
    return withAdmin { admin -> deleteUser(admin, parsed, System.out) }
}

/** `duello-backend prune-orphans ...`: run after a database restore (infra/README.md). */
fun runPruneOrphansCli(args: List<String>): Int {
    val parsed = when (val p = parsePruneOrphansArgs(args)) {
        is Parsed.Invalid -> return usage(p.message, PRUNE_ORPHANS_USAGE)
        is Parsed.Ok -> p.value
    }
    return withAdmin { admin -> pruneOrphans(admin, parsed, System.out) }
}

suspend fun deleteUser(admin: AccountAdmin, args: DeleteUserArgs, out: PrintStream): Int {
    val plan = admin.plan(args.target)
    if (plan.firebaseUid == null) {
        out.println("No Firebase account has this email. Nothing deleted.")
        out.println("If the account was removed in Firebase only, its profile is an orphan: run prune-orphans.")
        return AdminExit.OK
    }
    out.println("Firebase uid:      ${plan.firebaseUid}")
    out.println("Firebase account:  ${if (plan.firebaseAccountExists) "exists" else "not found"}")
    out.println("Profile (user id): ${plan.userId ?: "none"}")
    plan.rows.forEach { (table, n) -> out.println("  $table: $n") }
    if (plan.nothingToDelete) {
        out.println("Nothing to delete (no profile and no Firebase account).")
        return AdminExit.OK
    }
    if (plan.activeMatch) {
        out.println("REFUSED: the user is in a running duel. Try again in a few minutes (a match lasts about 2 minutes).")
        return AdminExit.REFUSED
    }
    if (args.dryRun) {
        out.println("Dry run: nothing deleted. Run again with --yes to delete.")
        return AdminExit.OK
    }
    val userId = admin.delete(plan)
    out.println("Deleted: profile ${userId ?: "(none)"} and the Firebase account.")
    return AdminExit.OK
}

suspend fun pruneOrphans(admin: AccountAdmin, args: PruneOrphansArgs, out: PrintStream): Int {
    val scan = admin.findOrphans(pauseMs = args.pauseMs)
    out.println("Checked ${scan.checked} profiles, ${scan.orphans.size} without a Firebase account.")
    scan.orphans.forEach { out.println("  user id ${it.userId}") }
    if (scan.orphans.isEmpty()) return AdminExit.OK
    if (args.dryRun) {
        out.println("Dry run: nothing deleted. Run again with --yes to delete them.")
        return AdminExit.OK
    }
    if (scan.orphans.size > args.max) {
        out.println(
            "REFUSED: ${scan.orphans.size} orphans is more than --max ${args.max}. Check FIREBASE_PROJECT_ID / " +
                "the service account first; raise --max only if the number is expected.",
        )
        return AdminExit.REFUSED
    }
    val result = admin.prune(scan.orphans, args.pauseMs)
    out.println("Deleted ${result.deleted.size} orphan profiles.")
    if (result.skippedActiveMatch.isNotEmpty()) {
        out.println("Skipped ${result.skippedActiveMatch.size} with a running duel; run again later.")
    }
    return AdminExit.OK
}

private fun usage(message: String, usage: String): Int {
    System.err.println("Error: $message")
    System.err.println(usage)
    return AdminExit.USAGE
}

/** Wires the same repository and service as the server (no Koin), with the real Firebase Admin SDK. */
private fun withAdmin(block: suspend (AccountAdmin) -> Int): Int {
    val config = AppConfig.from(ApplicationConfig("application.conf"))
    val auth = createFirebaseAuth(config.firebase) ?: run {
        System.err.println("FIREBASE_SERVICE_ACCOUNT_FILE is not set: the Firebase account cannot be checked or deleted.")
        return AdminExit.USAGE
    }
    val dataSource = Db.dataSource(config.db)
    val db = Db(dataSource)
    return try {
        Db.migrate(dataSource)
        val users = UserRepository(db)
        // No duel listener here: running matches live in the server process; the
        // active-match check refuses those users instead.
        val service = UserService(users, DisplayNamePolicy(), FirebaseAccountDeleter(auth), Clock.systemUTC())
        runBlocking { block(AccountAdmin(users, service, FirebaseAuthDirectory(auth))) }
    } catch (e: AuthProviderException) {
        System.err.println("Firebase error: ${e.message}. Nothing more was changed; running the command again is safe.")
        AdminExit.ERROR
    } catch (e: Exception) {
        System.err.println("Failed: ${e.javaClass.simpleName}. Running the command again is safe.")
        e.printStackTrace()
        AdminExit.ERROR
    } finally {
        db.close()
    }
}
