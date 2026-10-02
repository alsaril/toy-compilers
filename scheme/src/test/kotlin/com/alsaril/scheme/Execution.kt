package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.Environment
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThatExceptionOfType

fun execute(source: String, env: Environment = GlobalEnvironment()): String = print(compile(source).run(env))

fun assertSyntaxError(source: String, message: String) {
    assertThatExceptionOfType(SchemeSyntaxException::class.java)
        .isThrownBy { compile(source) }
        .withMessage(message)
}

fun assertNameError(source: String, message: String, env: Environment = GlobalEnvironment()) =
    assertFailsOnRun(SchemeNameException::class.java, source, message, env)

fun assertRuntimeError(source: String, message: String, env: Environment = GlobalEnvironment()) =
    assertFailsOnRun(SchemeRuntimeException::class.java, source, message, env)

private fun assertFailsOnRun(type: Class<out Throwable>, source: String, message: String, env: Environment) {
    val program = compile(source)
    assertThatExceptionOfType(type)
        .isThrownBy { program.run(env) }
        .withMessage(message)
}
