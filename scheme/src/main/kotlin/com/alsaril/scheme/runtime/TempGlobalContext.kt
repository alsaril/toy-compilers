package com.alsaril.scheme.runtime

import com.alsaril.scheme.Context
import com.alsaril.scheme.Function

class TempGlobalContext : Context {
    private val map = mutableMapOf<String, Any>()

    init {
        map["boolean?"] = object : Function {
            override fun call(args: Any): Any {
                require(args is Pair && args.second is Null)
                val arg = args.first
                return Boolean.from(arg is Boolean)
            }
        }
        map["not"] = object : Function {
            override fun call(args: Any): Any {
                if (args !is Pair || args.second !is Null || args.first !is Boolean) return Boolean.FALSE
                return args.first.not()
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