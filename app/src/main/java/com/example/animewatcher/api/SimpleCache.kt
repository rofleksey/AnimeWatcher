package com.example.animewatcher.api

open class SimpleCache<A, V> {
    @Volatile var last: Pair<Array<out A>, V>? = null

    fun get(vararg args: A): V? {
        val curLast = last
        if (curLast != null) {
            if (args.contentEquals(curLast.first)) {
                return curLast.second
            }
        }
        return null
    }

    fun set(value: V, vararg args: A) {
        last = Pair(args, value)
    }
}