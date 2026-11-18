package com.sabit.duelloquiz.core.designsystem.theme

/**
 * Category accent slots (design/tokens.json `color.categoryAccentOrder`, components.md §5).
 * Categories are database rows, so a category is coloured by a slot name, never by its name in
 * code.
 */
object CategoryAccent {
    /** color.categoryAccentOrder: the fallback cycle in server order. */
    val Order: List<String> = listOf("violet", "blue", "teal", "green", "orange", "rose", "pink", "ocean")

    /**
     * color.categoryAccentDefaults: the slot the designer proposes per seed category (keyed by
     * slug / icon key). Only a suggestion for the DB rows; the app does not use it to colour tiles
     * (the API has no `accentKey` yet, so [resolve] falls back to the server order).
     */
    val Defaults: Map<String, String> = mapOf(
        "geography" to "ocean",
        "history" to "orange",
        "biology" to "green",
        "physics" to "violet",
        "sports" to "rose",
    )

    /**
     * Slot of the category at [index] in server order. A known [accentKey] (a future optional API
     * field) wins; a missing or unknown key falls back to `Order[index % 8]`, so a new category
     * always gets a colour without an app release. Negative indices (category not found) map like
     * any other index, never crash.
     */
    fun resolve(index: Int, accentKey: String? = null): String =
        accentKey?.takeIf { it in Order } ?: Order[index.mod(Order.size)]
}
