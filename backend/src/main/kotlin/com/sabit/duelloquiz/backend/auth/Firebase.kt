package com.sabit.duelloquiz.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.sabit.duelloquiz.backend.common.FirebaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.FileInputStream

private val log = LoggerFactory.getLogger("com.sabit.duelloquiz.backend.auth.Firebase")

/**
 * Creates the Firebase Admin app from the service-account file in
 * `FIREBASE_SERVICE_ACCOUNT_FILE`. Returns `null` (and logs a warning) when it is not
 * configured, so the server can still start locally; all tokens are then rejected and
 * no push is sent.
 */
fun createFirebaseApp(config: FirebaseConfig): FirebaseApp? {
    val file = config.serviceAccountFile ?: run {
        log.warn("FIREBASE_SERVICE_ACCOUNT_FILE is not set: every authenticated request will get 401")
        return null
    }
    val credentials = FileInputStream(file).use { GoogleCredentials.fromStream(it) }
    val options = FirebaseOptions.builder()
        .setCredentials(credentials)
        .apply { config.projectId?.let { setProjectId(it) } }
        .build()
    return FirebaseApp.getApps().firstOrNull { it.name == APP_NAME }
        ?: FirebaseApp.initializeApp(options, APP_NAME)
}

fun createFirebaseAuth(config: FirebaseConfig): FirebaseAuth? = createFirebaseApp(config)?.let(FirebaseAuth::getInstance)

private const val APP_NAME = "duello-backend"

class FirebaseTokenVerifier(private val auth: FirebaseAuth) : TokenVerifier {
    override suspend fun verify(idToken: String): AuthUser = withContext(Dispatchers.IO) {
        val token = try {
            auth.verifyIdToken(idToken)
        } catch (e: FirebaseAuthException) {
            throw InvalidTokenException("Firebase rejected the token: ${e.authErrorCode}", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidTokenException("Malformed token", e)
        }
        AuthUser(
            firebaseUid = token.uid,
            name = token.name,
            picture = token.picture,
            isAdmin = token.claims["admin"] == true,
        )
    }
}

class FirebaseAccountDeleter(private val auth: FirebaseAuth) : AuthAccountDeleter {
    override suspend fun deleteAccount(firebaseUid: String) = withContext(Dispatchers.IO) {
        try {
            auth.deleteUser(firebaseUid)
        } catch (e: FirebaseAuthException) {
            if (e.authErrorCode != AuthErrorCode.USER_NOT_FOUND) {
                throw AuthProviderException("Firebase user deletion failed: ${e.authErrorCode}", e)
            }
        }
    }
}
