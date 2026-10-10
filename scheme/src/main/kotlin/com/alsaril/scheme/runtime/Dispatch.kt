package com.alsaril.scheme.runtime

class Dispatch(val function: Function, val args: Any) {
    companion object {
        @JvmStatic
        fun of(function: Function, args: Any) = Dispatch(function, args)

//        @JvmStatic
//        fun dispatchFully(result: Any): Any {
//            var i = result
//            while (i is Dispatch) {
//                i = i.function.call(i.args)
//            }
//            return i
//        }
    }
}
