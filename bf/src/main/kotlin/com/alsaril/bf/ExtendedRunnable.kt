package com.alsaril.bf

import java.io.InputStream
import java.io.OutputStream

interface ExtendedRunnable: Runnable {
    fun run(`in`: InputStream, `out`: OutputStream, memsize: Int, cycles: Int)
}