package com.alsaril.scheme.runtime

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GlobalEnvironment : Environment {
    private val map = mutableMapOf<String, Any>()
    private val symbols = mutableMapOf<String, Symbol>()

    private fun f1(f: (Any) -> Any) = object : Function {
        override fun call(args: Any): Any {
            require(args is Cons && args.second is Nil)
            return f(args.first)
        }
    }

    private fun f2(f: (Any, Any) -> Any) = object : Function {
        override fun call(args: Any): Any {
            require(args is Cons && args.second is Cons && args.second.second is Nil)
            return f(args.first, args.second.first)
        }
    }

    private fun fvar(f: (List<Any>) -> Any) = object : Function {
        override fun call(args: Any): Any {
            val l = mutableListOf<Any>()
            var ptr = args
            while (ptr != Nil) {
                require(ptr is Cons)
                l.add(ptr.first)
                ptr = ptr.second
            }
            return f(l)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fnumvar(f: (List<Int>) -> Any) = fvar {
        require(it.all { it is Int })
        f(it as List<Int>)
    }

    private fun comparison(f: (Int, Int) -> Boolean) = fnumvar {
        (it.asSequence() zip it.asSequence().drop(1)).fold(true) { acc, (a, b) -> acc && f(a, b) }
    }

    private fun arithmetic(identity: Int? = null, f: (Int, Int) -> Int) = fnumvar {
        if (it.isEmpty()) {
            require(identity != null)
            return@fnumvar identity
        }
        it.asSequence().drop(1).fold(it.first(), f)
    }

    private fun drop(list: Any, count: Int): Any {
        require(count >= 0)
        var it = list
        var i: Int = count
        while (it is Cons && i > 0) {
            it = it.second
            i--
        }
        require(i == 0)
        return it
    }

    init {
        map["boolean?"] = f1 { it is Boolean }
        map["number?"] = f1 { it is Int }
        map["symbol?"] = f1 { it is Symbol }
        map["pair?"] = f1 { it is Cons }
        map["null?"] = f1 { it is Nil }
        map["list?"] = f1 {
            var i = it
            while (i is Cons) {
                i = i.second
            }
            i is Nil
        }
        map["cons"] = f2(::Cons)
        map["car"] = f1 { (it as Cons).first }
        map["cdr"] = f1 { (it as Cons).second }
        map["list"] = object : Function {
            override fun call(args: Any) = args
        }
        map["list-ref"] = f2 { list, index ->
            val tail = drop(list, index as Int)
            require(tail is Cons)
            tail.first
        }
        map["list-tail"] = f2 { list, index -> drop(list, index as Int)}
        map["not"] = f1 { it == false }
        map["="] = comparison { a, b -> a == b }
        map["<"] = comparison { a, b -> a < b }
        map[">"] = comparison { a, b -> a > b }
        map["<="] = comparison { a, b -> a <= b }
        map[">="] = comparison { a, b -> a >= b }
        map["+"] = arithmetic(0) { a, b -> a + b }
        map["-"] = arithmetic { a, b -> a - b }
        map["*"] = arithmetic(1) { a, b -> a * b }
        map["/"] = arithmetic { a, b -> a / b }
        map["max"] = arithmetic { a, b -> max(a, b) }
        map["min"] = arithmetic { a, b -> min(a, b) }
        map["abs"] = f1 {
            require(it is Int)
            abs(it)
        }
    }

    override fun define(name: String, value: Any) {
        map[name] = value
    }

    override fun set(name: String, value: Any) {
        require(map.containsKey(name))
        map[name] = value
    }

    override fun resolve(name: String): Any {
        return map[name] ?: throw NoSuchElementException("$name not found")
    }

    override fun intern(name: String) = symbols.computeIfAbsent(name, ::Symbol)
}