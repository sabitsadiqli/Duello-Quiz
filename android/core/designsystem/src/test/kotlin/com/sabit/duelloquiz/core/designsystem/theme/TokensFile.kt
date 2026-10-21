package com.sabit.duelloquiz.core.designsystem.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** design/tokens.json, found by walking up from the test working directory. */
internal fun findTokensFile(): File {
    var dir: File? = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
    while (dir != null) {
        val candidate = File(dir, "design/tokens.json")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    throw AssertionError("design/tokens.json not found above ${System.getProperty("user.dir")}")
}

internal fun readTokens(): JsonObject = Json.parseToJsonElement(findTokensFile().readText()).jsonObject
