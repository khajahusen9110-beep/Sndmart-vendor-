package com.example.data.network

import com.example.BuildConfig
import com.example.data.local.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseClient(private val sessionManager: SessionManager) {

    enum class RefreshOutcome { OK, EXPIRED, TRANSIENT }

    companion object {
        /**
         * One lock for the whole app process. The app has several SupabaseClient instances (screen, incoming-order
         * screen, push service); each used to refresh with its own lock, so two refreshes could run together and
         * the second one used an already-rotated refresh token, which the server answers by ending the session.
         */
        private val refreshLock = Any()

        /**
         * The only place that rotates the refresh token.
         * OK = a valid token is stored now. EXPIRED = the server really refused the session (log in again).
         * TRANSIENT = network trouble / server busy: the session is NOT lost, try again later.
         */
        fun refreshSession(sessionManager: SessionManager, staleAccessToken: String? = null): RefreshOutcome =
            synchronized(refreshLock) {
                // another caller already refreshed while this one waited for the lock
                val current = sessionManager.accessToken
                if (!current.isNullOrBlank() && staleAccessToken != null && current != staleAccessToken &&
                    !sessionManager.isTokenExpiredOrExpiringSoon(30)) return RefreshOutcome.OK
                val rt = sessionManager.refreshToken
                if (rt.isNullOrBlank()) return RefreshOutcome.EXPIRED
                try {
                    val base = resolveBaseUrl().let { if (it.endsWith("/")) it else "$it/" }
                    val anon = resolveAnonKey()
                    val req = Request.Builder().url("${base}auth/v1/token?grant_type=refresh_token")
                        .post(org.json.JSONObject().put("refresh_token", rt).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("apikey", anon).header("Authorization", "Bearer $anon")
                        .header("Content-Type", "application/json").header("Accept", "application/json").build()
                    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
                    client.newCall(req).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val json = org.json.JSONObject(text)
                            val access = json.optString("access_token")
                            if (access.isBlank()) return RefreshOutcome.TRANSIENT
                            sessionManager.updateTokens(
                                newAccessToken = access,
                                newRefreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                                expiresInSeconds = json.optLong("expires_in", 3600L)
                            )
                            json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }?.let { sessionManager.userId = it }
                            return RefreshOutcome.OK
                        }
                        android.util.Log.w("SupabaseClient", "Session refresh failed: HTTP ${response.code} - $text")
                        if (response.code == 400 || response.code == 401 || response.code == 403) {
                            // If a newer token was stored meanwhile (login or another refresh), this failure is about the old one.
                            val nowRt = sessionManager.refreshToken
                            return if (!nowRt.isNullOrBlank() && nowRt != rt) RefreshOutcome.OK else RefreshOutcome.EXPIRED
                        }
                        return RefreshOutcome.TRANSIENT // 429 / 5xx: the server is busy, the session is fine
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SupabaseClient", "Token refresh network error: ${e.message}")
                    return RefreshOutcome.TRANSIENT // no network: never log the vendor out for this
                }
            }

        private const val DEFAULT_BASE_URL = "https://zennmoughfhennwgecgo.supabase.co/"

        // Public (anon) key of the Sndmart Supabase project. It is safe to ship in the app;
        // data access is protected by Row Level Security. Used only when the build does not
        // provide SUPABASE_ANON_KEY through .env.
        private const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inplbm5tb3VnaGZoZW5ud2dlY2dvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcwNjI3MjksImV4cCI6MjEwMjYzODcyOX0.xSuD8sYQ4PWgN-YhOaHhwg6hdZYjw2_Jj-XrK3lMERc"

        private fun isConfigured(value: String?): Boolean =
            !value.isNullOrBlank() && value != "DEFAULT_KEY"

        fun resolveAnonKey(): String {
            val key = BuildConfig.SUPABASE_ANON_KEY
            return if (isConfigured(key)) key else DEFAULT_ANON_KEY
        }

        fun resolveBaseUrl(): String {
            val url = BuildConfig.SUPABASE_URL
            if (!isConfigured(url)) return DEFAULT_BASE_URL
            return if (url.endsWith("/")) url else "$url/"
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val anonKey = resolveAnonKey()
        val token = sessionManager.accessToken ?: anonKey

        val requestBuilder = original.newBuilder()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

        val bodyContentType = original.body?.contentType()?.toString()
        if (original.header("Content-Type") == null) {
            if (bodyContentType != null) {
                requestBuilder.header("Content-Type", bodyContentType)
            } else {
                requestBuilder.header("Content-Type", "application/json")
            }
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // Guard against infinite loop
            if (responseCount(response) >= 3) return null

            // Never try to re-authenticate the auth endpoints themselves
            val path = response.request.url.encodedPath
            if (path.contains("auth/v1/token") || path.contains("auth/v1/signup") || path.contains("auth/v1/verify")) return null

            if (sessionManager.refreshToken.isNullOrBlank()) {
                sessionManager.notifySessionExpired("Your session expired, please log in again")
                return null
            }
            val priorTokenSent = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            return when (refreshSession(sessionManager, priorTokenSent)) {
                RefreshOutcome.OK -> {
                    val newToken = sessionManager.accessToken ?: return null
                    response.request.newBuilder()
                        .header("apikey", resolveAnonKey())
                        .header("Authorization", "Bearer $newToken")
                        .build()
                }
                RefreshOutcome.EXPIRED -> {
                    sessionManager.notifySessionExpired("Your session expired, please log in again")
                    null
                }
                RefreshOutcome.TRANSIENT -> null // request fails for now; the session stays
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        while (!response.isSuccessful && (response.code in listOf(502, 503, 504)) && request.method == "GET" && tryCount < 2) {
            tryCount++
            response.close()
            try {
                Thread.sleep(800L * tryCount)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            response = chain.proceed(request)
        }
        response
    }

    private val okHttpClient = OkHttpClient.Builder()
        // The default allows only 5 requests at a time per host; the screens ask for several things at once.
        .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 24 })
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .addInterceptor(retryInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: SupabaseApi = Retrofit.Builder()
        .baseUrl(resolveBaseUrl())
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SupabaseApi::class.java)
}
