package com.anjo.statisticservice.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String) {
    companion object {
        val UP = Health("UP")
        val DOWN = Health("DOWN")
    }
}
