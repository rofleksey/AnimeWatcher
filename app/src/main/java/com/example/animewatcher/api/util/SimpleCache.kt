package com.example.animewatcher.api.util

import java.util.concurrent.ConcurrentHashMap

open class SimpleCache<V> {
    private var map = ConcurrentHashMap<Pair<String, Int>, V>()

    fun get(s: String, i: Int): V? {
        return map[Pair(s, i)]
    }

    fun set(s: String, i: Int, v: V) {
        map[Pair(s, i)] = v
    }

    fun clear() {
        map.clear()
    }
}