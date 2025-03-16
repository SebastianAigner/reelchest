package io.sebi.utils

import kotlin.time.Duration

fun List<Duration>.average(): Duration {
    if (this.isEmpty()) return Duration.ZERO
    return this.fold(Duration.ZERO) { acc, d -> acc + d } / this.size
}