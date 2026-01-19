package com.cmtaro.app.hitandblowgame.util

import kotlin.random.Random

object DigitGenerator {

    fun generate(digit: Int): String {
        val numbers = (0..9).shuffled()
        return numbers.take(digit).joinToString("")
    }
}
