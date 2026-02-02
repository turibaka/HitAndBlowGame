package com.cmtaro.app.hitandblowgame.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cmtaro.app.hitandblowgame.domain.model.Guess
import com.cmtaro.app.hitandblowgame.domain.rule.HitBlowCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GamePhase { 
    SETTING_P1, SETTING_P2, 
    CARD_SELECT_P1, CARD_SELECT_P2,  // ラウンド開始時のバフカード選択
    HAND_CONFIRM_P1, HAND_CONFIRM_P2,  // 手札確認フェーズ
    PLAYING,
    CARD_USE_P1, CARD_USE_P2,  // 手札カード使用フェーズ
    WAITING_P2_INPUT,  // P1入力完了、P2待ち
    REPLAYING,         // リプレイ中
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
    
    // バトルログ（アニメーション付き履歴表示用）
    private val _battleLog = MutableStateFlow<List<String>>(emptyList())
    val battleLog = _battleLog.asStateFlow()
    
    // リプレイシステム用
    private val _replayMessage = MutableStateFlow("")
    val replayMessage = _replayMessage.asStateFlow()
    
    private val _showReplayOverlay = MutableStateFlow(false)
    val showReplayOverlay = _showReplayOverlay.asStateFlow()
    
    private val _showCardSelectDialog = MutableStateFlow(false)
    val showCardSelectDialog = _showCardSelectDialog.asStateFlow()

    // 手札カード使用確認用
    private val _showHandCardDialog = MutableStateFlow(false)
    val showHandCardDialog = _showHandCardDialog.asStateFlow()

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
        // ラウンド開始時：まず数字設定から
        _phase.value = GamePhase.SETTING_P1
        addBattleLog("🎮 ラウンド${_currentRound.value} 開始！")
        
        // 前ラウンドの手札を破棄
        if (_p1HandCards.value.isNotEmpty() || _p2HandCards.value.isNotEmpty()) {
            addBattleLog("🗑️ 前ラウンドの手札を破棄")
        }
        
        // 手札を初期化（各ラウンド新しい手札）
        _p1HandCards.value = emptyList()
        _p2HandCards.value = emptyList()
    }
    
    // バトルログに追加
    private fun addBattleLog(message: String) {
        _battleLog.value = _battleLog.value + message
        // 最新10件のみ保持
        if (_battleLog.value.size > 10) {
            _battleLog.value = _battleLog.value.takeLast(10)
        }
    }

    fun onInputSubmitted(input: String) {
        if (input.length != digitCount || input.toSet().size != digitCount) return

        when (_phase.value) {
            GamePhase.SETTING_P1 -> {
                p1Answer = input
                // P1の数字設定後、カード選択へ
                _phase.value = GamePhase.CARD_SELECT_P1
                prepareRoundStartCards()
            }
            GamePhase.SETTING_P2 -> {
                p2Answer = input
                // P2の数字設定後、ゲーム開始（P1のターン）
                _phase.value = GamePhase.PLAYING
                _currentPlayer.value = Player.P1
            }
            GamePhase.PLAYING -> {
                // P1の入力後、手札カード使用フェーズへ
                p1CurrentInput = input
                _phase.value = GamePhase.CARD_USE_P1
                _currentPlayer.value = Player.P1
            }
            GamePhase.WAITING_P2_INPUT -> {
                // P2の入力後、手札カード使用フェーズへ
                p2CurrentInput = input
                _phase.value = GamePhase.CARD_USE_P2
                _currentPlayer.value = Player.P2
            }
            else -> {}
        }
    }

    // リプレイシステム：両プレイヤーの行動を順番にアニメーション表示
    private fun startReplay() {
        viewModelScope.launch {
            _phase.value = GamePhase.REPLAYING
            _showReplayOverlay.value = true
            
            // P1の結果判定
            val p1Result = calculator.judge(p2Answer, p1CurrentInput)
            val p2Result = calculator.judge(p1Answer, p2CurrentInput)
            
            // ステップ1: P1の推測表示
            _replayMessage.value = "🎯 P1 の推測: $p1CurrentInput"
            addBattleLog("🎯 P1 → $p1CurrentInput")
            delay(1200)
            
            // ステップ2: P1の結果表示
            _replayMessage.value = buildString {
                appendLine("🎯 P1 の推測: $p1CurrentInput")
                appendLine("結果: ${p1Result.hit} Hit / ${p1Result.blow} Blow")
            }
            addBattleLog("   ${p1Result.hit}H / ${p1Result.blow}B")
            delay(1500)
            
            // ステップ3: P2の推測表示
            _replayMessage.value = buildString {
                appendLine("🎯 P1: ${p1Result.hit}H / ${p1Result.blow}B")
                appendLine()
                appendLine("🎯 P2 の推測: $p2CurrentInput")
            }
            addBattleLog("🎯 P2 → $p2CurrentInput")
            delay(1200)
            
            // ステップ4: P2の結果表示
            _replayMessage.value = buildString {
                appendLine("🎯 P1: ${p1Result.hit}H / ${p1Result.blow}B")
                appendLine()
                appendLine("🎯 P2 の推測: $p2CurrentInput")
                appendLine("結果: ${p2Result.hit} Hit / ${p2Result.blow} Blow")
            }
            addBattleLog("   ${p2Result.hit}H / ${p2Result.blow}B")
            delay(1500)
            
            // カードモードの場合、ダメージ計算を段階的に表示
            if (isCardMode) {
                _replayMessage.value = buildString {
                    appendLine("🎯 P1: ${p1Result.hit}H / ${p1Result.blow}B")
                    appendLine("🎯 P2: ${p2Result.hit}H / ${p2Result.blow}B")
                    appendLine()
                    appendLine("⚔️ ダメージ計算中...")
                }
                delay(800)
                
                // P1のダメージ計算を表示
                val p1DamageInfo = calculateDamagePreview(Player.P1, p1Result.hit, p1Result.blow)
                _replayMessage.value = buildString {
                    appendLine("🎯 P1: ${p1Result.hit}H / ${p1Result.blow}B")
                    appendLine("🎯 P2: ${p2Result.hit}H / ${p2Result.blow}B")
                    appendLine()
                    appendLine("⚔️ P1 のダメージ:")
                    appendLine(p1DamageInfo)
                }
                delay(1200)
                
                // P1の行動を処理
                processPlayerAction(Player.P1, p1CurrentInput)
                delay(800)
                
                // P2のダメージ計算を表示
                val p2DamageInfo = calculateDamagePreview(Player.P2, p2Result.hit, p2Result.blow)
                _replayMessage.value = buildString {
                    appendLine("🎯 P1: ${p1Result.hit}H / ${p1Result.blow}B")
                    appendLine("🎯 P2: ${p2Result.hit}H / ${p2Result.blow}B")
                    appendLine()
                    appendLine("⚔️ P1: $p1DamageInfo")
                    appendLine()
                    appendLine("⚔️ P2 のダメージ:")
                    appendLine(p2DamageInfo)
                }
                delay(1200)
                
                // P2の行動を処理
                processPlayerAction(Player.P2, p2CurrentInput)
                delay(800)
            } else {
                // 通常モードの処理
                processPlayerAction(Player.P1, p1CurrentInput)
                delay(1000)
                processPlayerAction(Player.P2, p2CurrentInput)
                delay(1000)
            }
            
            // リプレイ完了
            finishReplay()
        }
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

            // 決着チェック（HP 0以下）
            if (_winner.value != null) {
                _phase.value = GamePhase.FINISHED
                return
            }
            
            // 3ヒット（正解）した場合：ラウンド終了、次のラウンドへ
            if (result.hit == digitCount) {
                _currentRound.value += 1
                addBattleLog("🎯 ${player.name} が正解！ラウンド${_currentRound.value - 1} 終了")
                // 次のラウンド開始
                viewModelScope.launch {
                    delay(1500)
                    startNewRound()
                }
                return
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

        // Hit/Blowボーナスダメージ（カード効果がある場合のみ）
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

        // 1. 【0 Hit 0 Blow】→ ダメージなし（自傷ダメージ廃止）
        if (hit == 0 && blow == 0) {
            damageLog = "${current.name}はダメージなし"
            addBattleLog("➖ ${current.name} ダメージなし")
            return
        }

        // 2. 【攻撃ダメージ】正解時のみ
        if (hit == digitCount) {
            // 相手も正解しているかチェック（同時正解の特殊処理）
            val p1Result = calculator.judge(p2Answer, p1CurrentInput)
            val p2Result = calculator.judge(p1Answer, p2CurrentInput)
            val bothCorrect = p1Result.hit == digitCount && p2Result.hit == digitCount
            
            if (bothCorrect) {
                // 【両者同時正解】→ 自分の数字の合計ダメージを自分が受ける
                val selfDamage = myAnswer.map { it.digitToInt() }.sum()
                
                if (current == Player.P1) {
                    _p1Hp.value = (p1Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "両者正解！P1は自分の数字でダメージ -${selfDamage}"
                    addBattleLog("💥 両者正解！P1 → 自分 -${selfDamage} HP (残り: ${_p1Hp.value})")
                } else {
                    _p2Hp.value = (p2Hp.value - selfDamage).coerceIn(0, 100)
                    damageLog = "両者正解！P2は自分の数字でダメージ -${selfDamage}"
                    addBattleLog("💥 両者正解！P2 → 自分 -${selfDamage} HP (残り: ${_p2Hp.value})")
                }
            } else {
                // 【通常の攻撃】片方だけ正解
                // 自分が設定した数字の合計がダメージになる
                val digits = myAnswer.map { it.digitToInt() }
                val baseAttack = digits.sum()
                var attackDamage = 0
                
                // 基礎ダメージの計算式を作成 (例: 2+3+4=9)
                val baseDamageFormula = "${digits.joinToString("+")}=$baseAttack"
                
                // 攻撃バフを適用
                if (current == Player.P1) {
                    attackDamage = ((baseAttack + p1AttackBonus) * p1AttackMultiplier).toInt()
                    val multiplierText = if (p1AttackMultiplier > 1.0) " ×${p1AttackMultiplier}" else ""
                    val bonusText = if (p1AttackBonus > 0) " +${p1AttackBonus}" else ""
                    val effectText = " [($baseDamageFormula)$bonusText$multiplierText]"
                    p1AttackBonus = 0
                    p1AttackMultiplier = 1.0
                    
                    // 反撃チェック
                    if (p2HasCounter) {
                        _p1Hp.value = (p1Hp.value - attackDamage).coerceIn(0, 100)
                        damageLog = "P2の反撃！P1に${attackDamage}ダメージ$effectText"
                        addBattleLog("🔄 P2 反撃！ → P1 -${attackDamage} HP$effectText (残り: ${_p1Hp.value})")
                        p2HasCounter = false
                    } else {
                        _p2Hp.value = (p2Hp.value - attackDamage - bonusDamage).coerceIn(0, 100)
                        damageLog = "P1がP2に攻撃ダメージ -${attackDamage + bonusDamage}$effectText"
                        addBattleLog("⚔️ P1 → P2 -${attackDamage + bonusDamage} HP$effectText (残り: ${_p2Hp.value})")
                    }
                } else {
                    attackDamage = ((baseAttack + p2AttackBonus) * p2AttackMultiplier).toInt()
                    val multiplierText = if (p2AttackMultiplier > 1.0) " ×${p2AttackMultiplier}" else ""
                    val bonusText = if (p2AttackBonus > 0) " +${p2AttackBonus}" else ""
                    val effectText = " [($baseDamageFormula)$bonusText$multiplierText]"
                    p2AttackBonus = 0
                    p2AttackMultiplier = 1.0
                    
                    // 反撃チェック
                    if (p1HasCounter) {
                        _p2Hp.value = (p2Hp.value - attackDamage).coerceIn(0, 100)
                        damageLog = "P1の反撃！P2に${attackDamage}ダメージ$effectText"
                        addBattleLog("🔄 P1 反撃！ → P2 -${attackDamage} HP$effectText (残り: ${_p2Hp.value})")
                        p1HasCounter = false
                    } else {
                        _p1Hp.value = (p1Hp.value - attackDamage - bonusDamage).coerceIn(0, 100)
                        damageLog = "P2がP1に攻撃ダメージ -${attackDamage + bonusDamage}$effectText"
                        addBattleLog("⚔️ P2 → P1 -${attackDamage + bonusDamage} HP$effectText (残り: ${_p1Hp.value})")
                    }
                }
            }
        } else if (hit > 0 || blow > 0) {
            // 3. 【Hit/Blow（正解以外）】→ ダメージなし（カード効果がある場合は追加ダメージのみ）
            if (bonusDamage > 0) {
                // Hit/Blowボーナスカードの効果がある場合のみダメージ
                if (current == Player.P1) {
                    _p2Hp.value = (p2Hp.value - bonusDamage).coerceIn(0, 100)
                    damageLog = "P1のHit/Blowボーナス！P2に${bonusDamage}ダメージ"
                    addBattleLog("✨ P1 Hit/Blowボーナス → P2 -${bonusDamage} HP (残り: ${_p2Hp.value})")
                } else {
                    _p1Hp.value = (p1Hp.value - bonusDamage).coerceIn(0, 100)
                    damageLog = "P2のHit/Blowボーナス！P1に${bonusDamage}ダメージ"
                    addBattleLog("✨ P2 Hit/Blowボーナス → P1 -${bonusDamage} HP (残り: ${_p1Hp.value})")
                }
            } else {
                damageLog = "${current.name}はダメージなし (${hit}H ${blow}B)"
                addBattleLog("➖ ${current.name} ダメージなし (${hit}H ${blow}B)")
            }
        }

        _lastDamageInfo.value = damageLog

        // 死亡チェック
        if (_p1Hp.value <= 0) _winner.value = Player.P2
        if (_p2Hp.value <= 0) _winner.value = Player.P1
        
        // ステータス効果を更新
        updateStatusEffects()
    }

    // ラウンド開始時のカード配布（バフ系のみ）
    private fun prepareRoundStartCards() {
        val buffCards = CardType.values().filter { it.category == CardCategory.BUFF }
        val selectedCards = buffCards.shuffled().take(3)
        _availableCards.value = selectedCards
    }

    // カードを選んだ時の処理
    fun onCardSelected(player: Player, card: CardType) {
        val playerName = if (player == Player.P1) "P1" else "P2"
        
        if (card.category == CardCategory.BUFF) {
            // バフ系カード：即時効果を適用
            applyBuffCard(player, card)
            
            // カード選択をログに記録
            addBattleLog("🃏 $playerName が「${card.title}」を選択")
            
            // P1が選択完了したら手札配布＆確認へ、P2が完了したら手札配布＆確認へ
            when (_phase.value) {
                GamePhase.CARD_SELECT_P1 -> {
                    // P1の手札を配布
                    distributeHandCards(Player.P1)
                    _phase.value = GamePhase.HAND_CONFIRM_P1
                }
                GamePhase.CARD_SELECT_P2 -> {
                    // P2の手札を配布
                    distributeHandCards(Player.P2)
                    _phase.value = GamePhase.HAND_CONFIRM_P2
                }
                else -> {}
            }
        }
        
        _availableCards.value = emptyList()
    }
    
    // 手札カードを配布（各ラウンド3枚、1回限り）
    private fun distributeHandCards(player: Player) {
        val supportCards = CardType.values().filter { it.category == CardCategory.SUPPORT }
        val newCards = supportCards.shuffled().take(3)
        
        if (player == Player.P1) {
            _p1HandCards.value = newCards
            addBattleLog("🎴 P1 が手札カードを3枚獲得")
        } else {
            _p2HandCards.value = newCards
            addBattleLog("🎴 P2 が手札カードを3枚獲得")
        }
    }
    
    // 手札確認完了
    fun confirmHandCards() {
        when (_phase.value) {
            GamePhase.HAND_CONFIRM_P1 -> {
                // P1の確認完了 → P2の数字設定へ
                _phase.value = GamePhase.SETTING_P2
            }
            GamePhase.HAND_CONFIRM_P2 -> {
                // P2の確認完了 → ゲーム開始（P1のターン）
                _phase.value = GamePhase.PLAYING
                _currentPlayer.value = Player.P1
            }
            else -> {}
        }
    }
    
    // バフカードの効果を適用
    private fun applyBuffCard(player: Player, card: CardType) {
        val playerName = if (player == Player.P1) "P1" else "P2"
        when (card) {
            CardType.ATTACK_SMALL -> {
                if (player == Player.P1) {
                    p1AttackBonus = 5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃+5")
                } else {
                    p2AttackBonus = 5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃+5")
                }
            }
            CardType.ATTACK_MEDIUM -> {
                if (player == Player.P1) {
                    p1AttackBonus = 10
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃+10")
                } else {
                    p2AttackBonus = 10
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃+10")
                }
            }
            CardType.ATTACK_LARGE -> {
                if (player == Player.P1) {
                    p1AttackMultiplier = 2.0
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃×2")
                } else {
                    p2AttackMultiplier = 2.0
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 攻撃×2")
                }
            }
            CardType.DEFENSE_SMALL -> {
                if (player == Player.P1) {
                    p1DefenseReduction = 5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 防御+5")
                } else {
                    p2DefenseReduction = 5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 防御+5")
                }
            }
            CardType.DEFENSE_MEDIUM -> {
                if (player == Player.P1) {
                    p1DefenseMultiplier = 0.5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 防御×0.5")
                } else {
                    p2DefenseMultiplier = 0.5
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 防御×0.5")
                }
            }
            CardType.DEFENSE_LARGE -> {
                if (player == Player.P1) {
                    p1IsInvincible = true
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 無敵付与")
                } else {
                    p2IsInvincible = true
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → 無敵付与")
                }
            }
            CardType.HEAL_SMALL -> {
                if (player == Player.P1) {
                    _p1Hp.value = (p1Hp.value + 10).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+10")
                } else {
                    _p2Hp.value = (p2Hp.value + 10).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+10")
                }
            }
            CardType.HEAL_MEDIUM -> {
                if (player == Player.P1) {
                    _p1Hp.value = (p1Hp.value + 20).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+20")
                } else {
                    _p2Hp.value = (p2Hp.value + 20).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+20")
                }
            }
            CardType.HEAL_LARGE -> {
                if (player == Player.P1) {
                    _p1Hp.value = (p1Hp.value + 30).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+30")
                } else {
                    _p2Hp.value = (p2Hp.value + 30).coerceIn(0, 100)
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP+30")
                }
            }
            else -> {} // 補助系カードはここでは処理しない
        }
        updateStatusEffects() // ステータス更新
    }
    
    // 補助系カードを使用する
    fun useHandCard(player: Player, card: CardType) {
        val playerName = if (player == Player.P1) "P1" else "P2"
        
        when (card) {
            CardType.COUNTER -> {
                if (player == Player.P1) p1HasCounter = true else p2HasCounter = true
                addBattleLog("🃏 $playerName カード使用: ${card.title} → 反撃準備")
            }
            CardType.INVINCIBLE -> {
                if (player == Player.P1) p1IsInvincible = true else p2IsInvincible = true
                addBattleLog("🃏 $playerName カード使用: ${card.title} → 無敵付与")
            }
            CardType.HIT_BONUS -> {
                if (player == Player.P1) p1HitBonus = 5 else p2HitBonus = 5
                addBattleLog("🃏 $playerName カード使用: ${card.title} → Hit×5")
            }
            CardType.BLOW_BONUS -> {
                if (player == Player.P1) p1BlowBonus = 3 else p2BlowBonus = 3
                addBattleLog("🃏 $playerName カード使用: ${card.title} → Blow×3")
            }
            CardType.STEAL_HP -> {
                if (player == Player.P1) {
                    val steal = 10.coerceAtMost(p2Hp.value)
                    _p2Hp.value = (p2Hp.value - steal).coerceIn(0, 100)
                    _p1Hp.value = (p1Hp.value + steal).coerceIn(0, 100)
                    _lastDamageInfo.value = "P1がP2のHPを${steal}奪った！"
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP${steal}吸収")
                } else {
                    val steal = 10.coerceAtMost(p1Hp.value)
                    _p1Hp.value = (p1Hp.value - steal).coerceIn(0, 100)
                    _p2Hp.value = (p2Hp.value + steal).coerceIn(0, 100)
                    _lastDamageInfo.value = "P2がP1のHPを${steal}奪った！"
                    addBattleLog("🃏 $playerName カード使用: ${card.title} → HP${steal}吸収")
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
        
        // カード使用後、次のフェーズへ
        when (_phase.value) {
            GamePhase.CARD_USE_P1 -> {
                _phase.value = GamePhase.WAITING_P2_INPUT
                _currentPlayer.value = Player.P2
            }
            GamePhase.CARD_USE_P2 -> {
                startReplay()
            }
            else -> {}
        }
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
    
    // リプレイ用：ダメージの事前計算と表示テキスト生成
    private fun calculateDamagePreview(player: Player, hit: Int, blow: Int): String {
        val myAnswer = if (player == Player.P1) p1Answer else p2Answer
        val playerName = if (player == Player.P1) "P1" else "P2"
        val targetName = if (player == Player.P1) "P2" else "P1"
        
        // 1. 0 Hit 0 Blow → ダメージなし
        if (hit == 0 && blow == 0) {
            return "➖ $playerName → ダメージなし"
        }
        
        // 2. 正解（全Hit）→ 同時正解チェック
        if (hit == digitCount) {
            // 相手も正解しているかチェック
            val p1Result = calculator.judge(p2Answer, p1CurrentInput)
            val p2Result = calculator.judge(p1Answer, p2CurrentInput)
            val bothCorrect = p1Result.hit == digitCount && p2Result.hit == digitCount
            
            if (bothCorrect) {
                // 【両者同時正解】→ 自分の数字の合計ダメージを自分が受ける
                val digits = myAnswer.map { it.digitToInt() }
                val selfDamage = digits.sum()
                val damageFormula = "${digits.joinToString("+")}=$selfDamage"
                val currentHp = if (player == Player.P1) p1Hp.value else p2Hp.value
                val newHp = (currentHp - selfDamage).coerceIn(0, 100)
                return "💥 両者正解！ $playerName → 自分 -${selfDamage} HP [($damageFormula)] (${currentHp} → ${newHp})"
            }
            
            // 【通常の攻撃】片方だけ正解
            val digits = myAnswer.map { it.digitToInt() }
            val baseAttack = digits.sum()
            val baseDamageFormula = "${digits.joinToString("+")}=$baseAttack"
            val attackBonus = if (player == Player.P1) p1AttackBonus else p2AttackBonus
            val attackMultiplier = if (player == Player.P1) p1AttackMultiplier else p2AttackMultiplier
            val hitBonus = if (player == Player.P1) p1HitBonus else p2HitBonus
            val blowBonus = if (player == Player.P1) p1BlowBonus else p2BlowBonus
            
            var bonusDamage = 0
            if (hitBonus > 0 && hit > 0) bonusDamage += hit * hitBonus
            if (blowBonus > 0 && blow > 0) bonusDamage += blow * blowBonus
            
            val attackDamage = ((baseAttack + attackBonus) * attackMultiplier).toInt()
            val totalDamage = attackDamage + bonusDamage
            
            // 効果テキストの作成
            val multiplierText = if (attackMultiplier > 1.0) " ×${attackMultiplier}" else ""
            val bonusText = if (attackBonus > 0) " +${attackBonus}" else ""
            val effectText = " [($baseDamageFormula)$bonusText$multiplierText]"
            
            // 反撃チェック
            val hasCounter = if (player == Player.P1) p2HasCounter else p1HasCounter
            
            if (hasCounter) {
                val currentHp = if (player == Player.P1) p1Hp.value else p2Hp.value
                val newHp = (currentHp - attackDamage).coerceIn(0, 100)
                return "🔄 $targetName の反撃！ → $playerName -${attackDamage} HP$effectText (${currentHp} → ${newHp})"
            } else {
                val targetHp = if (player == Player.P1) p2Hp.value else p1Hp.value
                val newHp = (targetHp - totalDamage).coerceIn(0, 100)
                
                val bonusDamageText = if (bonusDamage > 0) " (+${bonusDamage})" else ""
                return "⚔️ $playerName → $targetName -${totalDamage} HP$effectText$bonusDamageText (${targetHp} → ${newHp})"
            }
        }
        
        // 3. Hit/Blow（正解以外）→ ダメージなし（カード効果がある場合は追加ダメージのみ）
        val hitBonus = if (player == Player.P1) p1HitBonus else p2HitBonus
        val blowBonus = if (player == Player.P1) p1BlowBonus else p2BlowBonus
        
        var bonusDamage = 0
        if (hitBonus > 0 && hit > 0) bonusDamage += hit * hitBonus
        if (blowBonus > 0 && blow > 0) bonusDamage += blow * blowBonus
        
        if (bonusDamage > 0) {
            val targetHp = if (player == Player.P1) p2Hp.value else p1Hp.value
            val newHp = (targetHp - bonusDamage).coerceIn(0, 100)
            return "✨ $playerName Hit/Blowボーナス → $targetName -${bonusDamage} HP (${targetHp} → ${newHp})"
        }
        
        return "➖ $playerName → ダメージなし (${hit}H ${blow}B)"
    }

    // 手札カード使用のスキップ機能を追加
    // 手札カード使用フェーズをスキップ（カードを使わない）
    fun skipCardUse() {
        when (_phase.value) {
            GamePhase.CARD_USE_P1 -> {
                // P1がスキップしたら、P2の数字入力フェーズへ
                addBattleLog("⏭️ P1 カード使用をスキップ")
                _phase.value = GamePhase.WAITING_P2_INPUT
                _currentPlayer.value = Player.P2
            }
            GamePhase.CARD_USE_P2 -> {
                // P2がスキップしたら、リプレイ開始
                addBattleLog("⏭️ P2 カード使用をスキップ")
                startReplay()
            }
            else -> {}
        }
    }
    
    fun confirmCardUsePhase() {
        _showHandCardDialog.value = true
    }
    
    fun dismissCardUseDialog() {
        _showHandCardDialog.value = false
    }
}