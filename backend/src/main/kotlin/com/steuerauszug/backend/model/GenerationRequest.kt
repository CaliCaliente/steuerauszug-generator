package com.steuerauszug.backend.model

data class GenerationRequest(
    val clearingNumber: String,
    val institutionName: String,
    val institutionAddress: String,
    val customerNumber: String,
    val customerName: String,
    val customerAddress: String,
    val canton: String,
    val taxYear: Int
)
