package com.alsaril.scheme.runtime

import com.alsaril.scheme.SchemeRuntimeException

object Procedures {
    @JvmStatic
    fun procedure(value: Any): Function =
        value as? Function ?: throw SchemeRuntimeException("${Printer.print(value)} is not a procedure")
}
