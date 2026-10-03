package com.alsaril.scheme.runtime

class LocalEnvironment(val parent: Environment) : Environment {
    private val map = mutableMapOf<String, Any>()

    override fun define(name: String, value: Any) {
        map[name] = value
    }

    override fun set(name: String, value: Any) {
        if (map.containsKey(name)) {
            map[name] = value
        } else {
            parent.set(name, value)
        }
    }

    override fun resolve(name: String) = map[name] ?: parent.resolve(name)
}