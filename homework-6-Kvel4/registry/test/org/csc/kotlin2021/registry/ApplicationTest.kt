package org.csc.kotlin2021.registry

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*
import org.csc.kotlin2021.UserAddress
import org.csc.kotlin2021.UserInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

fun Application.testModule() {
    (environment.config as MapApplicationConfig).apply {
        // define test environment here
    }
    module(testing = true)
}

class ApplicationTest {
    private val objectMapper = jacksonObjectMapper()
    private val testUserName = "pupkin"
    private val testHttpAddress = UserAddress("127.0.0.1", 9999)
    private val userData = UserInfo(testUserName, testHttpAddress)
    private val users =
        listOf(
            userData,
            UserInfo("skank-hunter", UserAddress("127.0.0.1", 1652)),
            UserInfo("DeadInside", UserAddress("127.0.0.1", 1653)),
            UserInfo("vladimiry-privet", UserAddress("127.0.0.1", 1488)),
            UserInfo("lexa-ti-lychiy-proveryayschiy", UserAddress("127.0.0.1", 1655)),
            UserInfo("SfAbuser", UserAddress("127.0.0.1", 1656)),
        )

    @BeforeEach
    fun clearRegistry() {
        Registry.users.clear()
    }

    @Test
    fun `health endpoint`() {
        withTestApplication({ testModule() }) {
            handleRequest(HttpMethod.Get, "/v1/health").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("OK", response.content)
            }
        }
    }

    @Test
    fun `register user`() = withRegisteredUser(userData) {}

    @Test
    fun `register already existed user`() =
        withRegisteredUser(userData) {
            registerRequest(userData).apply {
                assertEquals(HttpStatusCode.Conflict, response.status())
            }
        }

    @Test
    fun `register users`() = withRegisteredUsers(users.size) {}

    @Test
    fun `empty users`() =
        withTestApplication({ testModule() }) {
            val usersMap = usersRequest()

            assertTrue { usersMap.size == 0 }
        }

    @Test
    fun `list users`() =
        withRegisteredUsers(users.size) { registered ->
            val usersMap = usersRequest()

            assertTrue {
                registered.all { it.name in usersMap.keys } && registered.size == usersMap.size
            }
        }

    @Test
    fun `delete empty`(): Unit =
        withTestApplication({ testModule() }) {
            val name = "DeadInside"

            checkOkStatus {
                method = HttpMethod.Delete
                uri = "/v1/users/$name"
            }
        }

    @Test
    fun `delete user`() =
        withRegisteredUsers(users.size) { _ ->
            val name = "DeadInside"

            checkOkStatus {
                method = HttpMethod.Delete
                uri = "/v1/users/$name"
            }

            val usersMap = usersRequest()
            assertTrue { name !in usersMap }
        }

    @Test
    fun `wrong name`(): Unit =
        withTestApplication({ testModule() }) {
            registerRequest(UserInfo("@#***SDHF", UserAddress("127.0.0.1", 4365))).apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())

                val content = response.content ?: fail("No response content")

                assertEquals("Illegal user name", content)
            }
        }

    @Test
    fun `modify user`() =
        withRegisteredUser(userData) {
            successfulModify("pupkin", UserAddress("127.0.0.1", 4366))
        }

    @Test
    fun `add through modify`(): Unit =
        withTestApplication({ testModule() }) {
            successfulModify("modified-abuser", UserAddress("127.0.0.1", 4516))
        }

    private fun TestApplicationEngine.successfulModify(name: String, address: UserAddress) =
        checkOkStatus {
            method = HttpMethod.Put
            uri = "/v1/users/$name"
            addHeader("Content-type", "application/json")
            setBody(
                objectMapper.writeValueAsString(
                    address
                )
            )
        }.apply {
            val usersMap = usersRequest()

            assertTrue { name in usersMap && address == usersMap[name] }
        }

    private fun withRegisteredUsers(n: Int, block: TestApplicationEngine.(List<UserInfo>) -> Unit) {
        withTestApplication({ testModule() }) {
            if (n > users.size) throw IllegalArgumentException("n must be lesser or equal than users size")

            for (i in 0 until n) {
                successfulRegister(users[i])
            }

            this.block(users.take(n))
        }
    }

    private fun withRegisteredUser(userInfo: UserInfo, block: TestApplicationEngine.() -> Unit) {
        withTestApplication({ testModule() }) {
            successfulRegister(userInfo)

            this@withTestApplication.block()
        }
    }

    private fun TestApplicationEngine.registerRequest(userInfo: UserInfo) =
        handleRequest {
            method = HttpMethod.Post
            uri = "/v1/users"
            addHeader("Content-type", "application/json")
            setBody(objectMapper.writeValueAsString(userInfo))
        }

    private fun TestApplicationEngine.successfulRegister(userInfo: UserInfo) =
        checkOkStatus {
            method = HttpMethod.Post
            uri = "/v1/users"
            addHeader("Content-type", "application/json")
            setBody(objectMapper.writeValueAsString(userInfo))
        }

    private fun TestApplicationEngine.checkOkStatus(request: TestApplicationRequest.() -> Unit) =
        handleRequest { request() }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content ?: fail("No response content")
            val info = objectMapper.readValue<HashMap<String, String>>(content)

            assertNotNull(info["status"])
            assertEquals("ok", info["status"])
        }

    private fun TestApplicationEngine.usersRequest(): HashMap<String, UserAddress> {
        handleRequest {
            method = HttpMethod.Get
            uri = "/v1/users"
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            val content = response.content ?: fail("No response content")

            return objectMapper.readValue(content)
        }
    }
}
