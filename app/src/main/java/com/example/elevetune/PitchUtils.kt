package com.example.elevetune

import kotlin.math.log2

fun freqToCents(refFreq: Float, userFreq: Float): Float {
    if (refFreq <= 0f || userFreq <= 0f) return Float.NaN
    return (1200f * log2(userFreq / refFreq))
}