package com.runner.core

/** "v0.4.2"가 "0.4.1"보다 새 버전인가. 숫자로 못 읽는 조각은 0으로 본다. */
fun isNewerVersion(tag: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
    val a = parts(tag)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
