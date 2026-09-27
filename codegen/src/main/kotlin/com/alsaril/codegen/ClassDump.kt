package com.alsaril.codegen

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.spi.ToolProvider
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * Debug aid for a quick look at generated code, not part of the API. Set `-Dcodegen.dump=<dir>`
 * or `CODEGEN_DUMP=<dir>` — in an IDE's JUnit run configuration template, say — and every class
 * [ByteClassLoader] loads has its `javap -p -v` listing written there as `<n> <class>.javap.txt`,
 * before the JVM gets to reject it. The listings of the previous run are deleted first; nothing
 * else in the directory is touched. Needs a JDK, which is where `javap` lives.
 */
internal object ClassDump {
    private val directory: Path? = (System.getProperty("codegen.dump") ?: System.getenv("CODEGEN_DUMP"))
        ?.takeIf { it.isNotBlank() }
        ?.let(Path::of)
        ?.also { directory ->
            Files.createDirectories(directory)
            directory.listDirectoryEntries("*.javap.txt").forEach { it.deleteExisting() }
        }

    private val count = AtomicInteger()

    fun dump(name: String, bytes: ByteArray) {
        val directory = directory ?: return
        val listing = runCatching { javap(bytes) }.getOrElse { "javap failed: $it" }
        directory.resolve("%04d %s.javap.txt".format(count.incrementAndGet(), name.replace('/', '.'))).writeText(listing)
    }

    private fun javap(bytes: ByteArray): String {
        val file = Files.createTempFile("codegen", ".class")
        try {
            file.writeBytes(bytes)
            val out = StringWriter()
            ToolProvider.findFirst("javap").orElseThrow().run(PrintWriter(out), PrintWriter(out), "-p", "-v", file.toString())
            return out.toString()
        } finally {
            file.deleteIfExists()
        }
    }
}
