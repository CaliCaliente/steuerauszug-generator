package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.IbActivityData

interface IbParser {
    fun parse(content: String): IbActivityData

    companion object {
        const val TYPE_DIVIDENDS = "Dividends"
        const val TYPE_WITHHOLDING_TAX = "Withholding Tax"
        const val TYPE_INTEREST = "Interest"

        fun extractSymbol(description: String): String {
            val idx = description.indexOf('(')
            return if (idx > 0) description.substring(0, idx).trim()
            else description.split(" ").firstOrNull() ?: description
        }
    }
}
