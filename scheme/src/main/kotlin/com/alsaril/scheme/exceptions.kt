package com.alsaril.scheme

/** The source is not a well-formed program. Thrown while tokenizing, parsing or compiling, before anything runs. */
class SchemeSyntaxException(message: String) : RuntimeException(message)

/** A symbol was looked up, or assigned with `set!`, and no environment defines it. */
class SchemeNameException(val name: String, message: String = "$name is not defined") : RuntimeException(message)

/** Anything else that fails while a program runs: a wrong argument count or type, calling something that is not a procedure, division by zero. */
class SchemeRuntimeException(message: String) : RuntimeException(message)
