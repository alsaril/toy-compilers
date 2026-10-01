package com.alsaril.scheme.runtime

import com.alsaril.scheme.SchemeNameException
import com.alsaril.scheme.SchemeRuntimeException
import com.alsaril.scheme.runtime.Printer.print
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GlobalEnvironment : Environment {
    private val map = mutableMapOf<String, Any>()
    private val symbols = mutableMapOf<String, Symbol>()

    private fun fvar(name: String, f: (List<Any>) -> Any) {
        map[name] = object : Function {
            override fun call(args: Any) = f(argumentList(args))
        }
    }

    private fun fixed(name: String, count: Int, f: (List<Any>) -> Any) = fvar(name) {
        if (it.size != count) throw SchemeRuntimeException("$name: expected ${arguments(count)}, got ${it.size}")
        f(it)
    }

    private fun f1(name: String, f: (Any) -> Any) = fixed(name, 1) { f(it[0]) }

    private fun f2(name: String, f: (Any, Any) -> Any) = fixed(name, 2) { f(it[0], it[1]) }

    private fun fnumvar(name: String, min: Int, f: (List<Int>) -> Any) = fvar(name) { args ->
        if (args.size < min) throw SchemeRuntimeException("$name: expected at least ${arguments(min)}, got ${args.size}")
        f(args.map { it.number(name) })
    }

    private fun comparison(name: String, f: (Int, Int) -> Boolean) = fnumvar(name, 0) {
        (it.asSequence() zip it.asSequence().drop(1)).fold(true) { acc, (a, b) -> acc && f(a, b) }
    }

    private fun arithmetic(name: String, identity: Int? = null, f: (Int, Int) -> Int) =
        fnumvar(name, if (identity == null) 1 else 0) {
            if (it.isEmpty()) identity!! else it.asSequence().drop(1).fold(it.first(), f)
        }

    private fun Any.number(name: String) =
        this as? Int ?: throw SchemeRuntimeException("$name: expected a number, got ${print(this)}")

    private fun Any.pair(name: String) =
        this as? Cons ?: throw SchemeRuntimeException("$name: expected a pair, got ${print(this)}")

    private fun drop(name: String, list: Any, index: Any): Any {
        val count = index.number(name)
        if (count < 0) throw SchemeRuntimeException("$name: expected a non-negative index, got $count")
        var tail = list
        repeat(count) {
            tail = (tail as? Cons)?.second ?: throw outOfRange(name, list, count)
        }
        return tail
    }

    private fun outOfRange(name: String, list: Any, index: Int) =
        SchemeRuntimeException("$name: index $index is out of range for ${print(list)}")

    init {
        f1("boolean?") { it is Boolean }
        f1("number?") { it is Int }
        f1("symbol?") { it is Symbol }
        f1("pair?") { it is Cons }
        f1("null?") { it is Nil }
        f1("list?") {
            var i = it
            while (i is Cons) {
                i = i.second
            }
            i is Nil
        }
        f2("cons", ::Cons)
        f1("car") { it.pair("car").first }
        f1("cdr") { it.pair("cdr").second }
        map["list"] = object : Function {
            override fun call(args: Any) = args
        }
        f2("list-ref") { list, index ->
            (drop("list-ref", list, index) as? Cons)?.first ?: throw outOfRange("list-ref", list, index as Int)
        }
        f2("list-tail") { list, index -> drop("list-tail", list, index) }
        f1("not") { it == false }
        comparison("=") { a, b -> a == b }
        comparison("<") { a, b -> a < b }
        comparison(">") { a, b -> a > b }
        comparison("<=") { a, b -> a <= b }
        comparison(">=") { a, b -> a >= b }
        arithmetic("+", 0) { a, b -> a + b }
        arithmetic("-") { a, b -> a - b }
        arithmetic("*", 1) { a, b -> a * b }
        arithmetic("/") { a, b ->
            if (b == 0) throw SchemeRuntimeException("/: division by zero")
            a / b
        }
        arithmetic("max") { a, b -> max(a, b) }
        arithmetic("min") { a, b -> min(a, b) }
        f1("abs") { abs(it.number("abs")) }
    }

    override fun define(name: String, value: Any) {
        map[name] = value
    }

    override fun set(name: String, value: Any) {
        if (name !in map) throw SchemeNameException(name, "set!: $name is not defined")
        map[name] = value
    }

    override fun resolve(name: String): Any {
        return map[name] ?: throw SchemeNameException(name)
    }

    override fun intern(name: String) = symbols.computeIfAbsent(name, ::Symbol)
}
