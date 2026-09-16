package com.alsaril.scheme.tokenizer

sealed interface Token

data class Number(val value: Int) : Token

data class Symbol(val name: String) : Token
