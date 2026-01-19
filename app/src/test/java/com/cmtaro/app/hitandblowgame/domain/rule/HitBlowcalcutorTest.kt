package com.cmtaro.app.hitandblowgame.domain.rule

import com.cmtaro.app.hitandblowgame.domain.rule.HitBlowCalculator
import org.junit.Test
import org.junit.Assert.assertEquals

class HitBlowCalculatorTest {

    @Test
    fun samePosition_isHit() {
        val calculator = HitBlowCalculator()
        val result = calculator.judge("123", "123")

        // 第1引数が「期待する値」、第2引数が「実際の計算結果」
        assertEquals(3, result.hit)
        assertEquals(0, result.blow)
    }

    @Test
    fun differentPosition_isBlow() {
        val calculator = HitBlowCalculator()
        val result = calculator.judge("123", "312")

        assertEquals(0, result.hit)
        assertEquals(3, result.blow)
    }
}