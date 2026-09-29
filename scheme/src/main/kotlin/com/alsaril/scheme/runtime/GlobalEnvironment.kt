package com.alsaril.scheme.runtime

class GlobalEnvironment : Context {
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
        (it.asSequence() zip it.asSequence().drop(1)).fold(true) {acc, (a, b) -> acc && f(a, b)}
    }

    init {
        map["boolean?"] = f1 { it is Boolean }
        map["number?"] = f1 { it is Int }
        map["not"] = f1 { it == false }
        map["="] = comparison { a, b -> a == b }
        map["<"] = comparison { a, b -> a < b }
        map[">"] = comparison { a, b -> a > b }
        map["<="] = comparison { a, b -> a <= b }
        map[">="] = comparison { a, b -> a >= b }
        // map["+"] = f2num { a, b -> a + b }
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