package org.csc.kotlin2021.registry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import org.csc.kotlin2021.HttpApi
import org.csc.kotlin2021.Message
import org.csc.kotlin2021.server.ChatMessageListener
import org.csc.kotlin2021.server.HttpChatServer
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

// Грязь какая-то, хз как заставить работать через эту функцию. Запросы просто не видят что сервер
// стартанул, если
// специально не запускать его в отдельном треде

// fun Application.testModule() {
//    (environment.config as MapApplicationConfig).apply {
//         define test environment here
//    }
//    val server = HttpChatServer("0.0.0.0", 8080)
//    server.setMessageListener(TestMessageListener())
//    val module = server.configureModule()
//
//    this.module()
//    thread { server.start() }
// }

fun withServer(block: () -> Unit) {
    val server = HttpChatServer("0.0.0.0", 8080)
    server.setMessageListener(TestMessageListener())

    thread { server.start() }

    try {
        block()
    } finally {
        server.stop()
    }
}

class ClientTest {
    private val clients =
        mapOf(
            "skank-hunter" to TestHttpClient("0.0.0.0", 8080),
            "DeadInside" to TestHttpClient("0.0.0.0", 8080),
            "vladimiry-privet" to TestHttpClient("0.0.0.0", 8080),
            "lexa-ti-lychiy-proveryayschiy" to TestHttpClient("0.0.0.0", 8080),
            "SfAbuser" to TestHttpClient("0.0.0.0", 8080)
        )

    @Test
    fun message() {
        withServer {
            val sfMessage = Message("SfAbuser", "go zxc")
            val deadInsideMessage = Message("DeadInside", "go")

            assertTrue { clients["DeadInside"]!!.sendMessage(sfMessage) }
            assertTrue { clients["SfAbuser"]!!.sendMessage(deadInsideMessage) }
        }
    }
}

class TestMessageListener : ChatMessageListener {
    override fun messageReceived(userName: String, text: String) {}
}

class TestHttpClient(host: String, port: Int) {
    private val objectMapper = jacksonObjectMapper()
    private val httpApi: HttpApi =
        Retrofit.Builder()
            .baseUrl("http://$host:$port")
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
            .create(HttpApi::class.java)

    fun sendMessage(message: Message): Boolean {
        val response = httpApi.sendMessage(message).execute()

        return response.isSuccessful
    }
}
