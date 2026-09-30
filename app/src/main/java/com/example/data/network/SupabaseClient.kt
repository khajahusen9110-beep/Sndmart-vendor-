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

    companion object {
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
        private val lock = Any()

        override fun authenticate(route: Route?, response: Response): Request? {
            // Guard against infinite loop
            if (responseCount(response) >= 3) {
                return null
            }

            // Never try to re-authenticate the auth endpoints themselves
            val path = response.request.url.encodedPath
            if (path.contains("auth/v1/token") || path.contains("auth/v1/signup") || path.contains("auth/v1/verify")) {
                return null
            }

            val storedRefreshToken = sessionManager.refreshToken
            if (storedRefreshToken.isNullOrBlank()) {
                sessionManager.notifySessionExpired("Your session expired, please log in again")
                return null
            }

            synchronized(lock) {
                val currentToken = sessionManager.accessToken
                val priorTokenSent = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

                // If another concurrent request already refreshed the token, retry immediately with new token
                if (!currentToken.isNullOrBlank() && currentToken != priorTokenSent) {
                    val anonKey = resolveAnonKey()
                    return response.request.newBuilder()
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                // Call Supabase GoTrue to refresh token
                val refreshed = refreshTokensSynchronously(storedRefreshToken)
                if (refreshed) {
                    val newToken = sessionManager.accessToken ?: return null
                    val anonKey = resolveAnonKey()
                    return response.request.newBuilder()
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $newToken")
                        .build()
                } else {
                    return null
                }
            }
        }
    }

    private fun refreshTokensSynchronously(refreshToken: String): Boolean {
        return try {
            val anonKey = resolveAnonKey()
            val baseUrl = resolveBaseUrl()
            val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val url = "${cleanBaseUrl}auth/v1/token?grant_type=refresh_token"

            val jsonBody = """{"refresh_token":"$refreshToken"}"""
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val refreshRequest = Request.Builder()
                .url(url)
                .post(body)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val cleanClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val response = cleanClient.newCall(refreshRequest).execute()
            if (response.isSuccessful) {
                val responseStr = response.body?.string() ?: return false
                val json = org.json.JSONObject(responseStr)
                val newAccessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id")

                if (newAccessToken.isNotBlank()) {
                    sessionManager.updateTokens(
                        newAccessToken = newAccessToken,
                        newRefreshToken = if (newRefreshToken.isNotBlank()) newRefreshToken else null,
                        expiresInSeconds = expiresIn
                    )
                    if (!userId.isNullOrBlank()) {
                        sessionManager.userId = userId
                    }
                    android.util.Log.i("SupabaseClient", "Session token auto-refreshed successfully.")
                    return true
                }
            } else {
                val code = response.code
                val errBody = response.body?.string() ?: ""
                android.util.Log.w("SupabaseClient", "Session refresh failed: HTTP $code - $errBody")
                if (code == 400 || code == 401 || code == 403) {
                    sessionManager.notifySessionExpired("Your session expired, please log in again")
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("SupabaseClient", "Exception during token refresh", e)
            false
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
