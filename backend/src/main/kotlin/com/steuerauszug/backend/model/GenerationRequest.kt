package com.steuerauszug.backend.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class GenerationRequest(
    @field:NotBlank val clearingNumber: String,
    @field:NotBlank val institutionName: String,
    @field:NotBlank val institutionAddress: String,
    @field:NotBlank val customerNumber: String,
    @field:NotBlank val customerName: String,
    @field:NotBlank val customerAddress: String,
    @field:NotBlank val canton: String,
    @field:Min(1900) val taxYear: Int
)
