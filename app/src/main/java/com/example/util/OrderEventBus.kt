package com.example.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object OrderEventBus {
    private val _incomingOrderEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val incomingOrderEvents = _incomingOrderEvents.asSharedFlow()

    fun notifyNewOrder(orderId: String) {
        _incomingOrderEvents.tryEmit(orderId)
    }
}
