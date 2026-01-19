package com.cmtaro.app.hitandblowgame.domain.model

// 履歴1行分のデータ
data class Guess(
    val player: String, // "P1" or "P2"
    val number: String, // 入力した数字
    val hit: Int,
    val blow: Int
)