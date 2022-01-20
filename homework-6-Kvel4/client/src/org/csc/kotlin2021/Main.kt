package org.csc.kotlin2021

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.net.InternetDomainName
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.csc.kotlin2021.server.HttpChatServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.net.URL
import kotlin.concurrent.thread

val log: Logger = LoggerFactory.getLogger("main")
const val MIN_POSSIBLE_PORT = 0
const val MAX_POSSIBLE_PORT = 9999

fun main(args: Array<String>) {
    val parser = ArgParser("client")
    val name by parser.option(ArgType.String, description = "Name of user").required()
    val registryBaseUrl by parser.option(ArgType.String, "registry", description = "Base URL of User Registry")
        .default("http://localhost:8088")

    val host by parser.option(ArgType.String, description = "Hostname or IP to listen on")
        .default("0.0.0.0") // 0.0.0.0 - listen on all network interfaces

    val port by parser.option(ArgType.Int, description = "Port to listen for on").default(8080)

    val publicUrl by parser.option(ArgType.String, "public-url", description = "Public URL")

    try {
        parser.parse(args)

        validateHost(host)
        validatePort(port)

        checkUserName(name) ?: throw IllegalArgumentException("Illegal user name '$name'")

        // initialize registry interface
        val objectMapper = jacksonObjectMapper()
        val registry = Retrofit.Builder()
            .baseUrl(registryBaseUrl)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build().create(RegistryApi::class.java)

        // create server engine
        val server = HttpChatServer(host, port)
        val chat = Chat(name, registry)
        server.setMessageListener(chat)

        // start server as separate job
        val serverJob = thread {
            server.start()
        }
        try {
            // register our client
            val userAddress = when {
                publicUrl != null -> {
                    val url = URL(publicUrl)
                    UserAddress(url.host, url.port)
                }
                else -> UserAddress(host, port)
            }
            registry.register(UserInfo(name, userAddress)).execute()

            // start
            chat.commandLoop()
        } finally {
            registry.unregister(name).execute()
            server.stop()
            serverJob.join()
        }

    } catch (e: Exception) {
        log.error("Error! ${e.message}", e)
        println("Error! ${e.message}")
    }
}

fun validatePort(port: Int) {
    if (port !in MIN_POSSIBLE_PORT .. MAX_POSSIBLE_PORT) throw IllegalArgumentException("Port must be between 0 and 9999")
}

// На самом деле stable в guava c 2016
@SuppressWarnings("UnstableApiUsage")
fun validateHost(host: String) {
    if (InternetDomainName.isValid(host)) {
        throw IllegalArgumentException("Host must be in ipv4 or ipv6 format")
    }
}
