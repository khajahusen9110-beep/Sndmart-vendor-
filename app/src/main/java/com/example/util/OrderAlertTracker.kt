package com.example.util

/**
 * Remembers which orders have already triggered the new-order alarm in this app process,
 * so an order is announced once even if it arrives both by push (FCM) and by in-app polling.
 */
object OrderAlertTracker {
    private const val MAX_TRACKED_ORDERS = 200
    private val alertedOrderIds = LinkedHashSet<String>()

    /** Returns true the first time [orderId] is seen, false afterwards. */
    @Synchronized
    fun markIfNew(orderId: String): Boolean {
        if (!alertedOrderIds.add(orderId)) return false
        if (alertedOrderIds.size > MAX_TRACKED_ORDERS) {
            alertedOrderIds.remove(alertedOrderIds.first())
        }
        return true
    }

    @Synchronized
    fun clear() {
        alertedOrderIds.clear()
    }
}
