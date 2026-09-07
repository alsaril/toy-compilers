package com.alsaril.bf

import java.io.InputStream
import java.io.OutputStream

interface Program {
    fun run(`in`: InputStream, out: OutputStream, memsize: Int, cycles: Int)
}
