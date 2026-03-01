package com.steuerauszug.backend.model

data class ValidationResponse(val valid: Boolean, val errors: List<String> = emptyList())
