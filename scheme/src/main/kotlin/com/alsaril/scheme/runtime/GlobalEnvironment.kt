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

    private fun f2num(f: (Int, Int) -> Any) = f2 { a, b ->
        require(a is Int && b is Int)
        f(a, b)
    }

    init {
        map["boolean?"] = f1 { it is Boolean }
        map["not"] = f1 { it == false }
        map["="] = f2num { a, b -> a == b }
        map["<"] = f2num { a, b -> a < b }
        map[">"] = f2num { a, b -> a > b }
    }

    override fun register(name: String, value: Any) {
        map[name] = value
    }

    override fun resolve(name: String): Any {
        return map[name] ?: throw NoSuchElementException("$name not found")
    }

    override fun intern(name: String) = symbols.computeIfAbsent(name, ::Symbol)
}