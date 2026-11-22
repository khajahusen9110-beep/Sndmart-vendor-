package com.example.data.local

import android.content.Context
import com.example.data.model.DashboardSnapshot
import com.example.data.model.Order
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File

/**
 * The last orders and dashboard numbers, kept on the phone so the app opens with real data at once while the fresh
 * numbers load. Files are per vendor; they are only for showing, never for deciding anything (no alarms, no money).
 */
object LiveCache {
    private var dir: File? = null
    private val moshi = Moshi.Builder().build()
    private val ordersAdapter: JsonAdapter<List<Order>> by lazy { moshi.adapter(Types.newParameterizedType(List::class.java, Order::class.java)) }
    private val dashAdapter: JsonAdapter<DashboardSnapshot> by lazy { moshi.adapter(DashboardSnapshot::class.java) }

    /** Data older than this is not shown (a day-old dashboard would mislead). */
    private const val MAX_AGE_MS = 24 * 3600 * 1000L

    fun init(context: Context) {
        if (dir == null) dir = File(context.applicationContext.filesDir, "live_cache").also { it.mkdirs() }
    }

    private fun file(vendorId: String, key: String): File? = dir?.let { File(it, "${vendorId.filter { c -> c.isLetterOrDigit() || c == '-' }}_$key.json") }

    private fun fresh(f: File): Boolean = f.exists() && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS

    fun saveOrders(vendorId: String, orders: List<Order>) {
        try { file(vendorId, "orders")?.writeText(ordersAdapter.toJson(orders.take(20))) } catch (_: Exception) {}
    }

    fun loadOrders(vendorId: String): List<Order>? = try {
        file(vendorId, "orders")?.takeIf { fresh(it) }?.let { ordersAdapter.fromJson(it.readText()) }
    } catch (_: Exception) { null }

    fun saveDashboard(vendorId: String, snap: DashboardSnapshot) {
        try { file(vendorId, "dashboard")?.writeText(dashAdapter.toJson(snap)) } catch (_: Exception) {}
    }

    fun loadDashboard(vendorId: String): DashboardSnapshot? = try {
        file(vendorId, "dashboard")?.takeIf { fresh(it) }?.let { dashAdapter.fromJson(it.readText()) }
    } catch (_: Exception) { null }

    fun clear() {
        try { dir?.listFiles()?.forEach { it.delete() } } catch (_: Exception) {}
    }
}
