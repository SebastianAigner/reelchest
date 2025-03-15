package io.sebi.vector

import cloud.unum.usearch.Index

fun main() {
    val index = Index.Config()
        .metric("cos")
        .dimensions(3)
        .capacity(100)
        .quantization("f32")
        .build()

    index.add(42, floatArrayOf(0.1f, 0.2f, 0.2f))
    val foundKeys = index.search(floatArrayOf(0.1f, 0.2f, 0.3f), 10)
    for (key in foundKeys) {
        println(key)
    }
}