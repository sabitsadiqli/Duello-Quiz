package com.sabit.duelloquiz.backend.progress

import com.sabit.duelloquiz.backend.auth.authUser
import com.sabit.duelloquiz.backend.category.CategoryService
import com.sabit.duelloquiz.backend.common.language
import com.sabit.duelloquiz.backend.user.UserService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.progressRoutes(service: ProgressService, categories: CategoryService, users: UserService) {
    get("/me/progress") {
        val user = users.getOrCreate(call.authUser)
        call.respond(service.progressOf(user.id, categories.listPlayable(), call.language))
    }
}
