package com.example.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Retrofit rejects a body type with a wildcard (List<? extends Map>) only when the method is first called: this catches that without a phone. */
class SupabaseApiShapeTest {
    private val api: SupabaseApi = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:1/")
        .client(OkHttpClient.Builder().connectTimeout(1, java.util.concurrent.TimeUnit.SECONDS).build())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(SupabaseApi::class.java)

    private fun noWildcardError(call: suspend () -> Unit) {
        val message = try { runBlocking { call() }; "" } catch (e: Throwable) { e.message.orEmpty() }
        assertFalse(message, message.contains("must not include a type variable or wildcard"))
    }

    @Test fun variantsBodyHasNoWildcard() = noWildcardError { api.createProductVariants(listOf(mapOf("label" to "1kg", "price" to 240))) }

    @Test fun bulkProductsBodyHasNoWildcard() = noWildcardError { api.createProductsBulk(listOf(mapOf("name" to "cake"))) }
}
