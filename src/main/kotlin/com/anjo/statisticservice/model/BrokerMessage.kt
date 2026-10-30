package com.anjo.statisticservice.model

data class BrokerMessage(
    val sourceId: String,
    val authorization: String?,
    val payload: String,
)

enum class Delivery { DONE, RETRY }
