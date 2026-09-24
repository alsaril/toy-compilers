package com.alsaril.scheme.runtime

class GlobalEnvironment : Context {
    private val map = mutableMapOf<String, Any>()

    init {
        map["boolean?"] = object : Function {
            override fun call(args: Any): Any {
                require(args is Cons && args.second is Nil)
                val arg = args.first
                return arg is Boolean
            }
        }
        map["not"] = object : Function {
            override fun call(args: Any): Any {
                if (args !is Cons || args.second !is Nil || args.first !is Boolean) return false
                return !args.first
            }
        }
//        map["="] = object : Function {
//            override fun call(args: Any): Any {
//                require(args is Pair && args.second is Pair && args.second.second is Null)
//                return Boolean.from(args.first == args.second.first)
//            }
//        }
//        map[">"] = object : Function {
//            override fun call(args: Any): Any {
//                require(args is Pair && args.second is Pair && args.second.second is Null)
//                return Boolean.from(args.first > args.second.first)
//            }
//        }
    }

    override fun register(name: String, value: Any) {
        map[name] = value
    }

    override fun resolve(name: String): Any {
        return map[name] ?: throw NoSuchElementException("$name not found")
    }
}