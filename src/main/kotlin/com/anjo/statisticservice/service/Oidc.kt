package com.anjo.statisticservice.service

/** One OpenID Connect provider; [name] is what the sign-in button shows. */
class Oidc(val issuer: String, private val clientId: String, private val clientSecret: String, val name: String)
