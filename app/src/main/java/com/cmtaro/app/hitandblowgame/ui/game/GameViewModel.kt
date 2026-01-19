package com.cmtaro.app.hitandblowgame.ui.game

import androidx.lifecycle.ViewModel
import com.cmtaro.app.hitandblowgame.domain.model.Guess
import com.cmtaro.app.hitandblowgame.domain.rule.HitBlowCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamePhase { SETTING_P1, SETTING_P2, PLAYING, FINISHED }
enum class Player { P1, P2 }

class GameViewModel : ViewModel() {
    // 1. Calculatorは「判定するだけ」の道具として持つ
    private val calculator = HitBlowCalculator()
    private var digitCount = 3

    fun setDigitCount(count: Int) {
        digitCount = count
    }

    private val _phase = MutableStateFlow(GamePhase.SETTING_P1)
    val phase = _phase.asStateFlow()

    private val _currentPlayer = MutableStateFlow(Player.P1)
    val currentPlayer = _currentPlayer.asStateFlow()

    private val _p1Logs = MutableStateFlow<List<Guess>>(emptyList())
    val p1Logs = _p1Logs.asStateFlow()

    private val _p2Logs = MutableStateFlow<List<Guess>>(emptyList())
    val p2Logs = _p2Logs.asStateFlow()

    private val _winner = MutableStateFlow<Player?>(null)
    val winner = _winner.asStateFlow()

    // 2. 正解データはViewModelが責任を持って保持する
    private var p1Answer: String = "" // P2が当てる数字
    private var p2Answer: String = "" // P1が当てる数字

    fun onInputSubmitted(input: String) {
        // 基本バリデーション：桁数不足や重複をここで弾く
        if (input.length != digitCount || input.toSet().size != digitCount) return

        when (_phase.value) {
            GamePhase.SETTING_P1 -> {
                p1Answer = input
                _phase.value = GamePhase.SETTING_P2
            }
            GamePhase.SETTING_P2 -> {
                p2Answer = input
                _phase.value = GamePhase.PLAYING
                _currentPlayer.value = Player.P1
            }
            GamePhase.PLAYING -> {
                processGuess(input)
            }
            GamePhase.FINISHED -> { /* 何もしない */ }
        }
    }

    private fun processGuess(input: String) {
        val current = _currentPlayer.value
        // 攻撃側がP1なら、標的はP2が設定した数字
        val target = if (current == Player.P1) p2Answer else p1Answer

        // 3. Calculatorには「引数」として渡す。Calculator内の変数は参照しない。
        val result = calculator.judge(target, input)

        val newGuess = Guess(current.name, input, result.hit, result.blow)

        // ログ更新
        if (current == Player.P1) {
            _p1Logs.value = _p1Logs.value + newGuess
        } else {
            _p2Logs.value = _p2Logs.value + newGuess
        }

        if (result.hit == digitCount) {
            _winner.value = current
            _phase.value = GamePhase.FINISHED
        } else {
            _currentPlayer.value = if (current == Player.P1) Player.P2 else Player.P1
        }
    }
}