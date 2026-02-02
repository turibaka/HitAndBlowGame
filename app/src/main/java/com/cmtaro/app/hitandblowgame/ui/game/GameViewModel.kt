package com.cmtaro.app.hitandblowgame.ui.game

import androidx.lifecycle.ViewModel
import com.cmtaro.app.hitandblowgame.domain.model.Guess
import com.cmtaro.app.hitandblowgame.domain.rule.HitBlowCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GamePhase { 
    SETTING_P1, SETTING_P2, 
    PLAYING,
    WAITING_P2_INPUT,  // P1入力完了、P2待ち
    REPLAYING,         // リプレイ中
    CARD_SELECT_P1, CARD_SELECT_P2, 
    FINISHED 
}
enum class Player { P1, P2 }

// カードの種類を大幅に拡張
enum class CardType(val title: String, val description: String, val category: CardCategory) {
    // バフ系（ラウンド開始時）
    ATTACK_SMALL("攻撃小", "次の攻撃 +5ダメージ", CardCategory.BUFF),
    ATTACK_MEDIUM("攻撃中", "次の攻撃 +10ダメージ", CardCategory.BUFF),
    ATTACK_LARGE("攻撃大", "次の攻撃 ×2倍", CardCategory.BUFF),
    
    DEFENSE_SMALL("防御小", "次の自傷ダメージ-5", CardCategory.BUFF),
    DEFENSE_MEDIUM("防御中", "次の自傷ダメージ半減", CardCategory.BUFF),
    DEFENSE_LARGE("防御大", "次の自傷ダメージ無効", CardCategory.BUFF),
    
    HEAL_SMALL("回復小", "HP +10回復", CardCategory.BUFF),
    HEAL_MEDIUM("回復中", "HP +20回復", CardCategory.BUFF),
    HEAL_LARGE("回復大", "HP +30回復", CardCategory.BUFF),
    
    // 補助系（即時発動）
    COUNTER("反撃", "相手の次の攻撃を跳ね返す", CardCategory.SUPPORT),
    INVINCIBLE("無敵", "次のダメージを完全無効化", CardCategory.SUPPORT),
    HIT_BONUS("Hitボーナス", "次のHit時、Hit数×5のダメージ追加", CardCategory.SUPPORT),
    BLOW_BONUS("Blowボーナス", "次のBlow時、Blow数×3のダメージ追加", CardCategory.SUPPORT),
    STEAL_HP("HP吸収", "相手のHPを10奪う", CardCategory.SUPPORT)
}

enum class CardCategory {
    BUFF,    // バフ系（ラウンド開始時に選択）
    SUPPORT  // 補助系（ゲーム中に使用可能）
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
    
    // 同時ターン制：各プレイヤーの入力を一時保存
    private var p1CurrentInput: String = ""
    private var p2CurrentInput: String = ""

    private val _currentRound = MutableStateFlow(1)
    val currentRound = _currentRound.asStateFlow()

    private val _currentTurn = MutableStateFlow(1)
    val currentTurn = _currentTurn.asStateFlow()

    private val _totalTurns = MutableStateFlow(0)
    val totalTurns = _totalTurns.asStateFlow()

    private val _availableCards = MutableStateFlow<List<CardType>>(emptyList())
    val availableCards = _availableCards.asStateFlow()

    // プレイヤーごとの手札（補助系カード）
    private val _p1HandCards = MutableStateFlow<List<CardType>>(emptyList())
    val p1HandCards = _p1HandCards.asStateFlow()
    
    private val _p2HandCards = MutableStateFlow<List<CardType>>(emptyList())
    val p2HandCards = _p2HandCards.asStateFlow()

    private var turnCount = 0
    private var turnCounter = 0

    private var p1NextBuff: CardType? = null
    private var p2NextBuff: CardType? = null

    // カード効果の状態管理を拡張
    private var p1AttackBonus = 0
    private var p2AttackBonus = 0
    private var p1AttackMultiplier = 1.0
    private var p2AttackMultiplier = 1.0
    
    private var p1DefenseReduction = 0
    private var p2DefenseReduction = 0
    private var p1DefenseMultiplier = 1.0
    private var p2DefenseMultiplier = 1.0
    
    private var p1IsInvincible = false
    private var p2IsInvincible = false
    private var p1HasCounter = false
    private var p2HasCounter = false
    
    private var p1HitBonus = 0
    private var p2HitBonus = 0
    private var p1BlowBonus = 0
    private var p2BlowBonus = 0
    
    private val _lastDamageInfo = MutableStateFlow("")
    val lastDamageInfo = _lastDamageInfo.asStateFlow()
    
    // プレイヤーのバフ・ステータス状態を監視可能に
    private val _p1StatusEffects = MutableStateFlow("")
    val p1StatusEffects = _p1StatusEffects.asStateFlow()
    
    private val _p2StatusEffects = MutableStateFlow("")
    val p2StatusEffects = _p2StatusEffects.asStateFlow()
    
    // リプレイシステム用
    private val _replayMessage = MutableStateFlow("")
    val replayMessage = _replayMessage.asStateFlow()
    
    private val _showReplayOverlay = MutableStateFlow(false)
    val showReplayOverlay = _showReplayOverlay.asStateFlow()
    
    private val _showCardSelectDialog = MutableStateFlow(false)
    val showCardSelectDialog = _showCardSelectDialog.asStateFlow()

    fun setDigitCount(count: Int) { digitCount = count }

    // MainActivityから渡されるフラグをセット
    fun setCardMode(enabled: Boolean) {
        isCardMode = enabled
        if (enabled) {
            // カードモードの場合のみ、ラウンド開始時のカード選択へ
            startNewRound()
        } else {
            // 通常モードは数字設定から開始
            _phase.value = GamePhase.SETTING_P1
        }
    }

    private fun startNewRound() {
        // ラウンド開始時に両プレイヤーにカード選択の機会を与える
        _phase.value = GamePhase.CARD_SELECT_P1
        prepareRoundStartCards()
        updateStatusEffects() // ステータスを更新
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
            GamePhase.PLAYING -> {
                // P1の入力
                p1CurrentInput = input
                _phase.value = GamePhase.WAITING_P2_INPUT
                _currentPlayer.value = Player.P2
            }
            GamePhase.WAITING_P2_INPUT -> {
                // P2の入力完了 → リプレイ開始
                p2CurrentInput = input
                startReplay()
            }
            else -> {}
        }
    }

    // リプレイシステム：両プレイヤーの行動を順番に表示
    private fun startReplay() {
        _phase.value = GamePhase.REPLAYING
        _showReplayOverlay.value = true
        
        // リプレイメッセージを構築
        val p1Result = calculator.judge(p2Answer, p1CurrentInput)
        val p2Result = calculator.judge(p1Answer, p2CurrentInput)
        
        val message = buildString {
            appendLine("🎯 P1の推測: $p1CurrentInput")
            appendLine("結果: ${p1Result.hit} Hit / ${p1Result.blow} Blow")
            appendLine()
            appendLine("🎯 P2の推測: $p2CurrentInput")
            appendLine("結果: ${p2Result.hit} Hit / ${p2Result.blow} Blow")
            
            if (isCardMode) {
                appendLine()
                appendLine("⚔️ ダメージ計算中...")
            }
        }
        
        _replayMessage.value = message
        
        // P1の行動を処理
        processPlayerAction(Player.P1, p1CurrentInput)
        
        // P2の行動を処理
        processPlayerAction(Player.P2, p2CurrentInput)
        
        // リプレイ完了後、次のターンへ
        finishReplay()
    }
    
    private fun processPlayerAction(player: Player, input: String) {
        val target = if (player == Player.P1) p2Answer else p1Answer
        val result = calculator.judge(target, input)

        // ログの記録
        val newGuess = Guess(player.name, input, result.hit, result.blow)
        if (player == Player.P1) _p1Logs.value += newGuess else _p2Logs.value += newGuess

        // ターン数をカウント
        _totalTurns.value += 1

        if (isCardMode) {
            // ダメージ計算
            calculateCardModeDamage(input, result.hit, result.blow, player)

            // 3ヒット（正解）した場合の処理
            if (result.hit == digitCount) {
                if (_winner.value == null) {
                    // 補助系カードを獲得できる選択肢を提供
                    prepareNextRoundCards()
                }
            }

            // 決着チェック
            if (_winner.value != null) {
                _phase.value = GamePhase.FINISHED
            }
        } else {
            // 通常モード（digitCount分のヒットで即終了：3桁なら3hit、4桁なら4hit）
            if (result.hit == digitCount) {
                _winner.value = player
                _phase.value = GamePhase.FINISHED
            }
        }
    }
    
    private fun finishReplay() {
        // リプレイ完了処理
        if (_winner.value == null && _phase.value != GamePhase.FINISHED) {
            // ターン進行チェック（6ターンごとにカード配布）
            if (isCardMode) {
                checkRoundProgress()
            }
            
            // 次のターン準備
            _phase.value = GamePhase.PLAYING
            _currentPlayer.value = Player.P1
            p1CurrentInput = ""
            p2CurrentInput = ""
        }
        
        _showReplayOverlay.value = false
    }

    // カードバトルの特殊ルール
    private fun calculateCardModeDamage(guess: String, hit: Int, blow: Int, current: Player) {
        val myAnswer = if (current == Player.P1) p1Answer else p2Answer
        var damageLog = ""

        // Hit/Blowボーナスダメージ
        var bonusDamage = 0
        if (current == Player.P1 && p1HitBonus > 0 && hit > 0) {
            bonusDamage += hit * p1HitBonus
            p1HitBonus = 0
        } else if (current == Player.P2 && p2HitBonus > 0 && hit > 0) {
            bonusDamage += hit * p2HitBonus
            p2HitBonus = 0
        }
        
        if (current == Player.P1 && p1BlowBonus > 0 && blow > 0) {
            bonusDamage += blow * p1BlowBonus
            p1BlowBonus = 0
        } else if (current == Player.P2 && p2BlowBonus > 0 && blow > 0) {
            bonusDamage += blow * p2BlowBonus
            p2BlowBonus = 0
        }

        // 1. 【自傷ダメージ】
        if (hit == 0 && blow == 0) {
            val isInvincible = if (current == Player.P1) p1IsInvincible else p2IsInvincible
            if (!isInvincible) {
                var selfDamage = myAnswer.map { it.digitToInt() }.sum()
                
                // 防御バフを適用
                if (current == Player.P1) {
                    selfDamage = ((selfDamage - p1DefenseReduction) * p1DefenseMultiplier).toInt().coerceAtLeast(0)
                    p1DefenseReduction = 0
                    p1DefenseMultiplier = 1.0
                } else {
                    selfDamage = ((selfDamage - p2DefenseReduction) * p2DefenseMultiplier).toInt().coerceAtLeast(0)
                    p2DefenseReduction = 0
                    p2DefenseMultiplier = 1.0
                }
                
                if (current == Player.P1) {
                    _p1Hp.value = (p1Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "P1が自傷ダメージ -$selfDamage"
                } else {
                    _p2Hp.value = (p2Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "P2が自傷ダメージ -$selfDamage"
                }
            } else {
                damageLog = "${current.name}は無敵状態で自傷を無効化！"
            }
            // 効果を使ったらリセット
            if (current == Player.P1) p1IsInvincible = false else p2IsInvincible = false
        }

        // 2. 【攻撃ダメージ】
        if (hit == digitCount) {
            val baseAttack = guess.map { it.digitToInt() }.sum()
            var attackDamage = 0
            
            // 攻撃バフを適用
            if (current == Player.P1) {
                attackDamage = ((baseAttack + p1AttackBonus) * p1AttackMultiplier).toInt()
                val multiplierText = if (p1AttackMultiplier > 1.0) " (×${p1AttackMultiplier})" else ""
                val bonusText = if (p1AttackBonus > 0) " (+${p1AttackBonus})" else ""
                p1AttackBonus = 0
                p1AttackMultiplier = 1.0
                
                // 反撃チェック
                if (p2HasCounter) {
                    _p1Hp.value = (p1Hp.value - attackDamage).coerceIn(0, 100)
                    damageLog = "P2の反撃！P1に${attackDamage}ダメージ${multiplierText}${bonusText}"
                    p2HasCounter = false
                } else {
                    _p2Hp.value = (p2Hp.value - attackDamage - bonusDamage).coerceIn(0, 100)
                    damageLog = "P1がP2に攻撃ダメージ -${attackDamage + bonusDamage}${multiplierText}${bonusText}"
                }
            } else {
                attackDamage = ((baseAttack + p2AttackBonus) * p2AttackMultiplier).toInt()
                val multiplierText = if (p2AttackMultiplier > 1.0) " (×${p2AttackMultiplier})" else ""
                val bonusText = if (p2AttackBonus > 0) " (+${p2AttackBonus})" else ""
                p2AttackBonus = 0
                p2AttackMultiplier = 1.0
                
                // 反撃チェック
                if (p1HasCounter) {
                    _p2Hp.value = (p2Hp.value - attackDamage).coerceIn(0, 100)
                    damageLog = "P1の反撃！P2に${attackDamage}ダメージ${multiplierText}${bonusText}"
                    p1HasCounter = false
                } else {
                    _p1Hp.value = (p1Hp.value - attackDamage - bonusDamage).coerceIn(0, 100)
                    damageLog = "P2がP1に攻撃ダメージ -${attackDamage + bonusDamage}${multiplierText}${bonusText}"
                }
            }
        }

        if (bonusDamage > 0 && hit != digitCount) {
            damageLog += " (Hit/Blowボーナス +$bonusDamage)"
        }

        _lastDamageInfo.value = damageLog

        // 死亡チェック
        if (_p1Hp.value <= 0) _winner.value = Player.P2
        if (_p2Hp.value <= 0) _winner.value = Player.P1
        
        // ステータス効果を更新
        updateStatusEffects()
    }
    // processGuess の最後の方、ターン交代の直前に追加
    private fun checkRoundProgress() {
        turnCounter++
        _currentTurn.value = (turnCounter / 2) + 1 // 両者で1ターン
        
        if (turnCounter >= 6) { // 両者3回ずつ（計6回）で1ラウンド終了
            turnCounter = 0
            _currentTurn.value = 1
            // 新ラウンド開始：カード選択フェーズへ
            _phase.value = GamePhase.CARD_SELECT_P1
            prepareRoundStartCards()
        }
    }

    // ラウンド開始時のカード配布（バフ系のみ）
    private fun prepareRoundStartCards() {
        val buffCards = CardType.values().filter { it.category == CardCategory.BUFF }
        val selectedCards = buffCards.shuffled().take(3)
        _availableCards.value = selectedCards
    }

    // 正解時のボーナスカード配布（補助系のみ）
    private fun prepareNextRoundCards() {
        val supportCards = CardType.values().filter { it.category == CardCategory.SUPPORT }
        val selectedCards = supportCards.shuffled().take(3)
        _availableCards.value = selectedCards
    }

    // カードを選んだ時の処理
    fun onCardSelected(player: Player, card: CardType) {
        if (card.category == CardCategory.BUFF) {
            // バフ系カード：即時効果を適用
            applyBuffCard(player, card)
            
            // P1が選択完了したらP2へ、P2が完了したら数字設定フェーズへ
            when (_phase.value) {
                GamePhase.CARD_SELECT_P1 -> {
                    _phase.value = GamePhase.CARD_SELECT_P2
                    prepareRoundStartCards() // P2用にカードを再生成
                }
                GamePhase.CARD_SELECT_P2 -> {
                    _phase.value = GamePhase.SETTING_P1
                }
                else -> {}
            }
        } else {
            // 補助系カード：手札に追加
            if (player == Player.P1) {
                _p1HandCards.value += card
            } else {
                _p2HandCards.value += card
            }
        }
        
        _availableCards.value = emptyList()
    }
    
    // バフカードの効果を適用
    private fun applyBuffCard(player: Player, card: CardType) {
        when (card) {
            CardType.ATTACK_SMALL -> {
                if (player == Player.P1) p1AttackBonus = 5 else p2AttackBonus = 5
            }
            CardType.ATTACK_MEDIUM -> {
                if (player == Player.P1) p1AttackBonus = 10 else p2AttackBonus = 10
            }
            CardType.ATTACK_LARGE -> {
                if (player == Player.P1) p1AttackMultiplier = 2.0 else p2AttackMultiplier = 2.0
            }
            CardType.DEFENSE_SMALL -> {
                if (player == Player.P1) p1DefenseReduction = 5 else p2DefenseReduction = 5
            }
            CardType.DEFENSE_MEDIUM -> {
                if (player == Player.P1) p1DefenseMultiplier = 0.5 else p2DefenseMultiplier = 0.5
            }
            CardType.DEFENSE_LARGE -> {
                if (player == Player.P1) p1IsInvincible = true else p2IsInvincible = true
            }
            CardType.HEAL_SMALL -> {
                if (player == Player.P1) _p1Hp.value = (p1Hp.value + 10).coerceIn(0, 100)
                else _p2Hp.value = (p2Hp.value + 10).coerceIn(0, 100)
            }
            CardType.HEAL_MEDIUM -> {
                if (player == Player.P1) _p1Hp.value = (p1Hp.value + 20).coerceIn(0, 100)
                else _p2Hp.value = (p2Hp.value + 20).coerceIn(0, 100)
            }
            CardType.HEAL_LARGE -> {
                if (player == Player.P1) _p1Hp.value = (p1Hp.value + 30).coerceIn(0, 100)
                else _p2Hp.value = (p2Hp.value + 30).coerceIn(0, 100)
            }
            else -> {} // 補助系カードはここでは処理しない
        }
        updateStatusEffects() // ステータス更新
    }
    
    // 補助系カードを使用する
    fun useHandCard(player: Player, card: CardType) {
        when (card) {
            CardType.COUNTER -> {
                if (player == Player.P1) p1HasCounter = true else p2HasCounter = true
            }
            CardType.INVINCIBLE -> {
                if (player == Player.P1) p1IsInvincible = true else p2IsInvincible = true
            }
            CardType.HIT_BONUS -> {
                if (player == Player.P1) p1HitBonus = 5 else p2HitBonus = 5
            }
            CardType.BLOW_BONUS -> {
                if (player == Player.P1) p1BlowBonus = 3 else p2BlowBonus = 3
            }
            CardType.STEAL_HP -> {
                if (player == Player.P1) {
                    val steal = 10.coerceAtMost(p2Hp.value)
                    _p2Hp.value = (p2Hp.value - steal).coerceIn(0, 100)
                    _p1Hp.value = (p1Hp.value + steal).coerceIn(0, 100)
                    _lastDamageInfo.value = "P1がP2のHPを${steal}奪った！"
                } else {
                    val steal = 10.coerceAtMost(p1Hp.value)
                    _p1Hp.value = (p1Hp.value - steal).coerceIn(0, 100)
                    _p2Hp.value = (p2Hp.value + steal).coerceIn(0, 100)
                    _lastDamageInfo.value = "P2がP1のHPを${steal}奪った！"
                }
            }
            else -> {}
        }
        
        // 手札から削除
        if (player == Player.P1) {
            _p1HandCards.value = _p1HandCards.value.filter { it != card }
        } else {
            _p2HandCards.value = _p2HandCards.value.filter { it != card }
        }
        updateStatusEffects() // ステータス更新
    }
    
    // ステータス効果を文字列化して表示用に更新
    private fun updateStatusEffects() {
        val p1Effects = mutableListOf<String>()
        if (p1AttackBonus > 0) p1Effects.add("攻撃+${p1AttackBonus}")
        if (p1AttackMultiplier > 1.0) p1Effects.add("攻撃×${p1AttackMultiplier}")
        if (p1DefenseReduction > 0) p1Effects.add("防御+${p1DefenseReduction}")
        if (p1DefenseMultiplier < 1.0) p1Effects.add("防御×${p1DefenseMultiplier}")
        if (p1IsInvincible) p1Effects.add("無敵")
        if (p1HasCounter) p1Effects.add("反撃")
        if (p1HitBonus > 0) p1Effects.add("Hit×${p1HitBonus}")
        if (p1BlowBonus > 0) p1Effects.add("Blow×${p1BlowBonus}")
        
        val p2Effects = mutableListOf<String>()
        if (p2AttackBonus > 0) p2Effects.add("攻撃+${p2AttackBonus}")
        if (p2AttackMultiplier > 1.0) p2Effects.add("攻撃×${p2AttackMultiplier}")
        if (p2DefenseReduction > 0) p2Effects.add("防御+${p2DefenseReduction}")
        if (p2DefenseMultiplier < 1.0) p2Effects.add("防御×${p2DefenseMultiplier}")
        if (p2IsInvincible) p2Effects.add("無敵")
        if (p2HasCounter) p2Effects.add("反撃")
        if (p2HitBonus > 0) p2Effects.add("Hit×${p2HitBonus}")
        if (p2BlowBonus > 0) p2Effects.add("Blow×${p2BlowBonus}")
        
        _p1StatusEffects.value = if (p1Effects.isEmpty()) "" else p1Effects.joinToString(" | ")
        _p2StatusEffects.value = if (p2Effects.isEmpty()) "" else p2Effects.joinToString(" | ")
    }

}