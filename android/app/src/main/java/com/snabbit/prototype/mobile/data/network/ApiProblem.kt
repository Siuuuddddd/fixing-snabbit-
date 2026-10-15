package com.snabbit.prototype.mobile.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiProblem(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val errorCode: String? = null,
    val retryAfterSeconds: Long? = null,
    val fields: Map<String, String> = emptyMap(),
    val reason: String? = null,
)
