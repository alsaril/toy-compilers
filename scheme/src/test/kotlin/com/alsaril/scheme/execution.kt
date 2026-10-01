package com.alsaril.scheme

import com.alsaril.scheme.compiler.SchemeCompiler.compile
import com.alsaril.scheme.runtime.Environment
import com.alsaril.scheme.runtime.GlobalEnvironment
import com.alsaril.scheme.runtime.Printer.print
import org.assertj.core.api.Assertions.assertThatThrownBy

fun execute(source: String, env: Environment = GlobalEnvironment()): String = print(compile(source).run(env))

fun assertSyntaxError(source: String, message: String) {
    assertThatThrownBy { compile(source) }
        .isInstanceOf(SchemeSyntaxException::class.java)
        .hasMessage(message)
}

fun assertNameError(source: String, message: String, env: Environment = GlobalEnvironment()) =
    assertFailsOnRun(SchemeNameException::class.java, source, message, env)

fun assertRuntimeError(source: String, message: String, env: Environment = GlobalEnvironment()) =
    assertFailsOnRun(SchemeRuntimeException::class.java, source, message, env)

private fun assertFailsOnRun(type: Class<out Throwable>, source: String, message: String, env: Environment) {
    val program = compile(source)
    assertThatThrownBy { program.run(env) }
        .isInstanceOf(type)
        .hasMessage(message)
}
