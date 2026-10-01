package com.alsaril.scheme.tokenizer

sealed interface Token

data class ConstantToken(val value: Int) : Token

data class SymbolToken(val name: String) : Token

enum class BracketToken: Token {
    OpenBracketToken, CloseBracketToken
}

data object DotToken: Token

data object QuoteToken: Token