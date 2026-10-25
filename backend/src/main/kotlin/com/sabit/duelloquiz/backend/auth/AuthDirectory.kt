package com.sabit.duelloquiz.backend.auth

/**
 * Read access to the identity provider's accounts for the admin CLI tasks
 * (`delete-user`, `prune-orphans`). Implementations: [FirebaseAuthDirectory], a fake in tests.
 */
interface AuthDirectory {
    /**
     * @return the uid of the account with this email, or null if there is none.
     * @throws AuthProviderException if the provider could not be asked.
     */
    suspend fun findUidByEmail(email: String): String?

    /**
     * @param uids at most [MAX_BATCH] uids.
     * @return the subset of [uids] that still exists at the provider.
     * @throws AuthProviderException if the provider could not be asked.
     */
    suspend fun existingUids(uids: Collection<String>): Set<String>

    companion object {
        /** Firebase `getUsers` accepts at most 100 identifiers per call. */
        const val MAX_BATCH = 100
    }
}
