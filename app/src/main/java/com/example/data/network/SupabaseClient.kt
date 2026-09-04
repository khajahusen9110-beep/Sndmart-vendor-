package com.example.data.network

import com.example.data.local.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseClient(private val sessionManager: SessionManager) {

    companion object {
        const val DEFAULT_BASE_URL = "https://zennmoughfhennwgecgo.supabase.co/"
        const val PROJECT_ID = "zennmoughfhennwgecgo"
        
        // Standard Supabase public anonymous key for this project
        // Can also be set/overridden in SessionManager or secrets panel
        const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inplbm5tb3VnaGZoZW5ud2dlY2dvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDk4NTYwMDAsImV4cCI6MjAyNTQzMjAwMH0.sndmart_public_anon_placeholder"

        fun resolveAnonKey(sessionManager: SessionManager): String {
            val custom = sessionManager.customAnonKey
            if (!custom.isNullOrBlank()) return custom

            val buildConfigKey = try {
                val field = com.example.BuildConfig::class.java.getField("VITE_SUPABASE_ANON_KEY")
                val v = field.get(null) as? String
                if (v != null && v != "DEFAULT_KEY" && v.isNotBlank()) v else null
            } catch (e: Exception) {
                try {
                    val field2 = com.example.BuildConfig::class.java.getField("SUPABASE_ANON_KEY")
                    val v2 = field2.get(null) as? String
                    if (v2 != null && v2 != "DEFAULT_KEY" && v2.isNotBlank()) v2 else null
                } catch (e2: Exception) {
                    null
                }
            }
            if (!buildConfigKey.isNullOrBlank()) return buildConfigKey

            return DEFAULT_ANON_KEY
        }

        fun resolveBaseUrl(): String {
            val buildConfigUrl = try {
                val field = com.example.BuildConfig::class.java.getField("VITE_SUPABASE_URL")
                field.get(null) as? String
            } catch (e: Exception) {
                try {
                    val field2 = com.example.BuildConfig::class.java.getField("SUPABASE_URL")
                    field2.get(null) as? String
                } catch (e2: Exception) {
                    null
                }
            }
            return if (!buildConfigUrl.isNullOrBlank()) {
                if (buildConfigUrl.endsWith("/")) buildConfigUrl else "$buildConfigUrl/"
            } else {
                DEFAULT_BASE_URL
            }
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val anonKey = resolveAnonKey(sessionManager)
        val token = sessionManager.accessToken ?: anonKey

        val requestBuilder = original.newBuilder()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

        if (original.header("Content-Type") == null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
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
