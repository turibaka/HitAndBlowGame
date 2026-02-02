package com.cmtaro.app.hitandblowgame.ui.game

import androidx.lifecycle.ViewModel
import com.cmtaro.app.hitandblowgame.domain.model.Guess
import com.cmtaro.app.hitandblowgame.domain.rule.HitBlowCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamePhase { SETTING_P1, SETTING_P2, PLAYING, FINISHED }
enum class Player { P1, P2 }
enum class CardType(val title: String, val description: String) {
    ATTACK_UP("攻撃強化", "次のダメージ2倍"),
    DEFENSE_UP("防御強化", "自傷ダメージ無効"),
    HEAL("回復", "HPを20回復")
}

class GameViewModel : ViewModel() {
    private val calculator = HitBlowCalculator()
    private var digitCount = 3

    // カードモードかどうかを保持
    private var isCardMode = false

    // --- カードモード用ステータス ---
    private val _p1Hp = MutableStateFlow(100)
    val p1Hp = _p1Hp.asStateFlow()

    private val _p2Hp = MutableStateFlow(100)
    val p2Hp = _p2Hp.asStateFlow()

    // --- 既存の状態 ---
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

    private var p1Answer: String = ""
    private var p2Answer: String = ""

    private val _currentRound = MutableStateFlow(1)
    val currentRound = _currentRound.asStateFlow()

    private val _currentTurn = MutableStateFlow(1)
    val currentTurn = _currentTurn.asStateFlow()

    private val _totalTurns = MutableStateFlow(0)
    val totalTurns = _totalTurns.asStateFlow()

    private val _availableCards = MutableStateFlow<List<CardType>>(emptyList())
    val availableCards = _availableCards.asStateFlow()

    private var turnCount = 0
    private var turnCounter = 0

    private var p1NextBuff: CardType? = null
    private var p2NextBuff: CardType? = null

    private var p1AttackMultiplier = 1
    private var p2AttackMultiplier = 1
    private var p1IsInvincible = false
    private var p2IsInvincible = false
    
    private val _lastDamageInfo = MutableStateFlow("")
    val lastDamageInfo = _lastDamageInfo.asStateFlow()

    fun setDigitCount(count: Int) { digitCount = count }

    // MainActivityから渡されるフラグをセット
    fun setCardMode(enabled: Boolean) {
        isCardMode = enabled
    }

    fun onInputSubmitted(input: String) {
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
            GamePhase.PLAYING -> processGuess(input)
            else -> {}
        }
    }

    // --- processGuess 関数を以下に丸ごと差し替え ---
    private fun processGuess(input: String) {
        val current = _currentPlayer.value
        val target = if (current == Player.P1) p2Answer else p1Answer
        val result = calculator.judge(target, input)

        // ログの記録
        val newGuess = Guess(current.name, input, result.hit, result.blow)
        if (current == Player.P1) _p1Logs.value += newGuess else _p2Logs.value += newGuess

        // ターン数をカウント
        _totalTurns.value += 1

        if (isCardMode) {
            // 1. ダメージ計算
            calculateCardModeDamage(input, result.hit, result.blow, current)

            // 2. 3ヒット（正解）した場合の処理
            if (result.hit == digitCount) {
                if (_winner.value == null) {
                    // ラウンド進行
                    _currentRound.value += 1
                    _currentTurn.value = 1 // ターンリセット
                    turnCounter = 0 // 内部カウンターもリセット
                    
                    // ここでバフカードを配る（ボーナスタイム）
                    prepareNextRoundCards()

                    // 【重要】ターゲット（数字）をリセットするため、設定フェーズに戻す
                    // 正解されたプレイヤー（ターゲット側）が数字を決め直す
                    // 例：P1が当てたなら、次はP2が新しい数字を決める
                    _phase.value = if (current == Player.P1) GamePhase.SETTING_P2 else GamePhase.SETTING_P1

                    // ログも一旦クリアして、新しいラウンドをスッキリさせる
                    _p1Logs.value = emptyList()
                    _p2Logs.value = emptyList()

                    return // フェーズが変わるのでここで処理終了
                }
            }

            // 3. 決着チェック
            if (_winner.value != null) {
                _phase.value = GamePhase.FINISHED
            } else {
                // ターン進行チェック（6ターンごとにカード配布）
                checkRoundProgress()
                
                // 交代
                _currentPlayer.value = if (current == Player.P1) Player.P2 else Player.P1
            }
        } else {
            // 通常モード（3ヒットで即終了）
            if (result.hit == digitCount) {
                _winner.value = current
                _phase.value = GamePhase.FINISHED
            } else {
                _currentPlayer.value = if (current == Player.P1) Player.P2 else Player.P1
            }
        }
    }

    // カードバトルの特殊ルール
    private fun calculateCardModeDamage(guess: String, hit: Int, blow: Int, current: Player) {
        val myAnswer = if (current == Player.P1) p1Answer else p2Answer
        var damageLog = ""

        // 1. 【自傷ダメージ】
        if (hit == 0 && blow == 0) {
            val isInvincible = if (current == Player.P1) p1IsInvincible else p2IsInvincible
            if (!isInvincible) {
                val selfDamage = myAnswer.map { it.digitToInt() }.sum()
                if (current == Player.P1) {
                    _p1Hp.value = (p1Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "P1が自傷ダメージ -$selfDamage"
                } else {
                    _p2Hp.value = (p2Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "P2が自傷ダメージ -$selfDamage"
                }
            } else {
                damageLog = "${current.name}は防御バフで自傷を無効化！"
            }
            // 効果を使ったらリセット
            if (current == Player.P1) p1IsInvincible = false else p2IsInvincible = false
        }

        // 2. 【攻撃ダメージ】
        if (hit == digitCount) {
            val multiplier = if (current == Player.P1) p1AttackMultiplier else p2AttackMultiplier
            val attackDamage = guess.map { it.digitToInt() }.sum() * multiplier

            if (current == Player.P1) {
                _p2Hp.value = (p2Hp.value - attackDamage).coerceIn(0, 100)
                damageLog = "P1がP2に攻撃ダメージ -$attackDamage" + if (multiplier > 1) " (×$multiplier)" else ""
            } else {
                _p1Hp.value = (p1Hp.value - attackDamage).coerceIn(0, 100)
                damageLog = "P2がP1に攻撃ダメージ -$attackDamage" + if (multiplier > 1) " (×$multiplier)" else ""
            }

            // 効果を使ったらリセット
            if (current == Player.P1) p1AttackMultiplier = 1 else p2AttackMultiplier = 1
        }

        _lastDamageInfo.value = damageLog

        // 死亡チェック
        if (_p1Hp.value <= 0) _winner.value = Player.P2
        if (_p2Hp.value <= 0) _winner.value = Player.P1
    }
    // processGuess の最後の方、ターン交代の直前に追加
    private fun checkRoundProgress() {
        turnCounter++
        _currentTurn.value = (turnCounter / 2) + 1 // 両者で1ターン
        
        if (turnCounter >= 6) { // 両者3回ずつ（計6回）で1ラウンド終了
            turnCounter = 0
            _currentTurn.value = 1
            prepareNextRoundCards()
        }
    }

    private fun prepareNextRoundCards() {
        // 確実にデータを流すために、一度空にしてからセット
        _availableCards.value = emptyList()
        val newCards = CardType.values().toList().shuffled().take(3)
        _availableCards.value = newCards
    }

    // カードを選んだ時の処理
    fun onCardSelected(player: Player, card: CardType) {
        when (card) {
            CardType.ATTACK_UP -> {
                if (player == Player.P1) p1AttackMultiplier = 2 else p2AttackMultiplier = 2
            }
            CardType.DEFENSE_UP -> {
                if (player == Player.P1) p1IsInvincible = true else p2IsInvincible = true
            }
            CardType.HEAL -> {
                if (player == Player.P1) _p1Hp.value = (p1Hp.value + 20).coerceIn(0, 100)
                else _p2Hp.value = (p2Hp.value + 20).coerceIn(0, 100)
            }
        }
        // カードを配り終えたらリストを空にしてUIを閉じる
        _availableCards.value = emptyList()
    }

}