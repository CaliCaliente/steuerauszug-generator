package com.steuerauszug.backend.parser

import com.steuerauszug.backend.model.IbActivityData

interface IbParser {
    fun parse(content: String): IbActivityData
}
