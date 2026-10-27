package com.anjo.statisticservice.exception

class MappingException(val reasons: List<String>) : RuntimeException(reasons.joinToString("; "))
