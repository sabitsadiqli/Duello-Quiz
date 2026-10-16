package com.sabit.duelloquiz.core.model

import androidx.compose.runtime.Immutable

/** The backend profile of the signed-in user (`GET /v1/me`). */
@Immutable
data class Profile(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    /** Elo rating, starts at 1000; only duels change it. */
    val rating: Int,
)

/** A playable category (`GET /v1/categories`), in server order. */
@Immutable
data class Category(
    val id: Int,
    val slug: String,
    /** Localized name (Accept-Language, fallback az). */
    val name: String,
    /** OQ-044: geography, history, biology, physics, sports; unknown keys get a generic icon. */
    val iconKey: String,
)
