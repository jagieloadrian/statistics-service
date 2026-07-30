package com.anjo.statisticservice.model

enum class Resolution(val param:String,val long: Long) {
    RAW("raw", 0), ONE_MIN("1m",60_000), FIVE_MINUTES("5m", 300_000),
    TEN_MINUTES("10m",600_000), TWENTY_MINUTES("20m", 1_200_000),
    THIRTY_MINUTES("30m", 1_800_000);

    companion object {
        fun fromQuery(param: String?): Resolution {
            return entries.firstOrNull { it.param == param } ?: RAW
        }
    }
}