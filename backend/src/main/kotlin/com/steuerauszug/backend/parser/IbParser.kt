package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.IbActivityData

interface IbParser {
    fun parse(content: String): IbActivityData

    companion object {
        fun extractSymbol(description: String): String {
            val idx = description.indexOf('(')
            return if (idx > 0) description.substring(0, idx).trim()
            else description.split(" ").firstOrNull() ?: description
        }
    }
}
