package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionUserDto
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.navigation.AppRole
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response as RetrofitResponse

class SessionTokenRefresherTest {
    @Test fun `parallel refresh is single flight and returns rotated access token`() {
        val store = FakeSessionStore(session("old-access", "old-refresh"))
        val calls = AtomicInteger()
        val refresher = refresher(store) { _, token ->
            assertEquals("old-refresh", token)
            calls.incrementAndGet()
            Thread.sleep(75)
            response("new-access", "new-refresh")
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = executor.invokeAll(List(8) { Callable { refresher.refreshAccessToken() } }).map { it.get() }
            assertEquals(1, calls.get())
            assertEquals(List(8) { "new-access" }, results)
            assertEquals("new-refresh", store.current()?.refreshToken)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `rotated session is persisted before authenticator retries request`() {
        val store = FakeSessionStore(session("old-access", "old-refresh"))
        val refresher = refresher(store) { _, _ -> response("new-access", "new-refresh") }
        val authenticator = RefreshingAuthenticator(refresher)
        val request = Request.Builder().url("https://example.invalid/protected").build()
        val unauthorized = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
            .body("".toResponseBody()).build()

        val retry = authenticator.authenticate(null, unauthorized)

        assertNotNull(retry)
        assertEquals("Bearer new-access", retry?.header("Authorization"))
        assertEquals("new-refresh", store.current()?.refreshToken)
    }

    @Test fun `refresh in progress retries and succeeds`() {
        val store = FakeSessionStore(session("old-access", "old-refresh"))
        val calls = AtomicInteger()
        val refresher = refresher(store) { _, _ ->
            if (calls.incrementAndGet() == 1) throw problem(409, "REFRESH_IN_PROGRESS")
            response("new-access", "new-refresh")
        }

        assertEquals("new-access", refresher.refreshAccessToken())
        assertEquals(2, calls.get())
    }

    @Test fun `session revoked clears local session`() {
        val store = FakeSessionStore(session("old-access", "old-refresh"))
        val manager = FakeSessionManager(store)
        val refresher = refresher(store, manager) { _, _ -> throw problem(401, "SESSION_REVOKED") }

        assertNull(refresher.refreshAccessToken())
        assertNull(store.current())
        assertEquals(1, manager.signOutCalls)
    }

    @Test fun `network failure keeps current session`() {
        val original = session("old-access", "old-refresh")
        val store = FakeSessionStore(original)
        val manager = FakeSessionManager(store)
        val refresher = refresher(store, manager) { _, _ -> throw IOException("offline") }

        assertNull(refresher.refreshAccessToken())
        assertSame(original, store.current())
        assertEquals(0, manager.signOutCalls)
    }

    private fun refresher(
        store: FakeSessionStore,
        manager: FakeSessionManager = FakeSessionManager(store),
        call: suspend (AppRole, String) -> SessionResponseDto,
    ) = SessionTokenRefresher(store, manager, AppRole.CUSTOMER, Json { ignoreUnknownKeys = true }, call)
}

private class FakeSessionStore(initial: AuthSession?) : SessionStore {
    private val value = MutableStateFlow(initial)
    override val session: Flow<AuthSession?> = value
    override fun current(): AuthSession? = value.value
    override fun save(session: AuthSession) { value.value = session }
    override fun clear() { value.value = null }
}

private class FakeSessionManager(private val store: SessionStore) : SessionManager {
    private val events = MutableSharedFlow<Unit>()
    override val signedOut: Flow<Unit> = events
    var signOutCalls = 0
    override fun signOut() { signOutCalls++; store.clear() }
}

private fun session(access: String, refresh: String) = AuthSession(
    access, "Bearer", Long.MAX_VALUE, refresh,
    SessionUser("id", "Name", "person@example.invalid", null, false, "CUSTOMER"),
)

private fun response(access: String, refresh: String) = SessionResponseDto(
    accessToken = access,
    expiresIn = 900,
    refreshToken = refresh,
    user = SessionUserDto("id", "Name", "person@example.invalid", role = "CUSTOMER"),
)

private fun problem(status: Int, code: String): HttpException {
    val body = """{"code":"$code","detail":"test"}""".toResponseBody("application/problem+json".toMediaType())
    return HttpException(RetrofitResponse.error<Any>(status, body))
}
