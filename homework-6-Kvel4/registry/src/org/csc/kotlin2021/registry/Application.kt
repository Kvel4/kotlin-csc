package org.csc.kotlin2021.registry

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import org.csc.kotlin2021.UserAddress
import org.csc.kotlin2021.UserInfo
import org.csc.kotlin2021.checkUserName
import org.slf4j.event.Level
import java.util.concurrent.ConcurrentHashMap

fun main(args: Array<String>) {
//    thread {
        // TODO: periodically check users and remove unreachable ones
        // Вообще не понял че от меня хотят. Как запрос к /v1/health может вообще говорить о доступности юзеров???
        // /v1/health всегда возвращает ок, сложно как-то этим валидировать пользователей
//    }
    EngineMain.main(args)
}

object Registry {
    val users = ConcurrentHashMap<String, UserAddress>()
}

@Suppress("UNUSED_PARAMETER")
@JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "invalid argument")
        }
        exception<UserAlreadyRegisteredException> { cause ->
            call.respond(HttpStatusCode.Conflict, cause.message ?: "user already registered")
        }
        exception<IllegalUserNameException> { cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "illegal user name")
        }
    }
    routing {
        get("/v1/health") {
            call.respondText("OK", contentType = ContentType.Text.Plain)
        }

        post("/v1/users") {
            val user = call.receive<UserInfo>()
            val name = user.name

            checkUserName(name) ?: throw IllegalUserNameException()
            if (Registry.users.putIfAbsent(name, user.address) != null) throw UserAlreadyRegisteredException()

            call.okResponse()
        }

        get("/v1/users") {
            call.respond(Registry.users)
        }

        put("/v1/users/{name}") {
            val name = call.parameters["name"] ?: throw IllegalArgumentException()
            checkUserName(name) ?: throw IllegalUserNameException()
            val address = call.receive<UserAddress>()

            Registry.users[name] = address

            call.okResponse()
        }

        delete("/v1/users/{name}") {
            val name = call.parameters["name"] ?: throw IllegalArgumentException()
            checkUserName(name) ?: throw IllegalUserNameException()

            Registry.users.remove(name)

            call.okResponse()
        }
    }
}

private suspend fun ApplicationCall.okResponse() = this.respond(mapOf("status" to "ok"))

class UserAlreadyRegisteredException(message: String = "User already registered") : RuntimeException(message)
class IllegalUserNameException(message: String = "Illegal user name") : RuntimeException(message)
