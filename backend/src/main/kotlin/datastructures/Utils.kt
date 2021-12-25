package io.sebi.datastructures

import java.math.BigInteger
import java.security.MessageDigest

fun String.shaHashed(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bigInt = BigInteger(1, md.digest(toByteArray(Charsets.UTF_8)))
    return String.format("%032x", bigInt)
}
