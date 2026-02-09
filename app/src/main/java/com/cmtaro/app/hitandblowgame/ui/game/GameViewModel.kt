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

// リプレイ演出用のエフェクトデータ
data class ReplayEffect(
    val type: EffectType,
    val player: Player,
    val targetPlayer: Player? = null,
    val value: Int = 0,
    val hit: Int = 0,      // Hit数
    val blow: Int = 0,     // Blow数
    val timestamp: Long = System.currentTimeMillis()
)

enum class EffectType {
    NONE,
    RESULT_DISPLAY,  // Hit/Blow結果表示
    ATTACK,      // 攻撃演出（playerからtargetPlayerへ）
    DEFENSE,     // 防御演出（playerの領域に）
    HEAL,        // 回復演出（playerの領域に）
    BARRIER,     // バリア演出（無敵時）
    COUNTER,     // カウンター演出
    STEAL_HP     // HP吸収演出
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

    // カード使用の記録
    private var p1UsedCard: CardType? = null
    private var p2UsedCard: CardType? = null

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

    // リプレイシステム用 - エフェクト通知
    private val _replayEffect = MutableStateFlow(ReplayEffect(EffectType.NONE, Player.P1))
    val replayEffect = _replayEffect.asStateFlow()

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

        // ターンカウントをリセット
        _currentTurn.value = 1
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
                if (isCardMode) {
                    // カードモード：カード選択へ
                    _phase.value = GamePhase.CARD_SELECT_P1
                    prepareRoundStartCards()
                } else {
                    // 通常モード：P2の数字設定へ
                    _phase.value = GamePhase.SETTING_P2
                }
            }
            GamePhase.SETTING_P2 -> {
                p2Answer = input
                if (isCardMode) {
                    // カードモード：カード選択へ
                    _phase.value = GamePhase.CARD_SELECT_P2
                    prepareRoundStartCards()
                } else {
                    // 通常モード：ゲーム開始
                    _phase.value = GamePhase.PLAYING
                    _currentPlayer.value = Player.P1
                }
            }
            GamePhase.PLAYING -> {
                p1CurrentInput = input
                if (isCardMode) {
                    // カードモード：手札カード使用フェーズへ
                    _phase.value = GamePhase.CARD_USE_P1
                    _currentPlayer.value = Player.P1
                } else {
                    // 通常モード：P2の入力待ちへ
                    _phase.value = GamePhase.WAITING_P2_INPUT
                    _currentPlayer.value = Player.P2
                }
            }
            GamePhase.WAITING_P2_INPUT -> {
                p2CurrentInput = input
                if (isCardMode) {
                    // カードモード：手札カード使用フェーズへ
                    _phase.value = GamePhase.CARD_USE_P2
                    _currentPlayer.value = Player.P2
                } else {
                    // 通常モード：リプレイ開始
                    startReplay()
                }
            }
            else -> {}
        }
    }

    // リプレイシステム：両プレイヤーの行動を順番にアニメーション表示
    private fun startReplay() {
        viewModelScope.launch {
            _phase.value = GamePhase.REPLAYING

            // P1の結果判定
            val p1Result = calculator.judge(p2Answer, p1CurrentInput)
            val p2Result = calculator.judge(p1Answer, p2CurrentInput)

            addBattleLog("🎯 P1 → $p1CurrentInput")
            addBattleLog("   ${p1Result.hit}H / ${p1Result.blow}B")
            addBattleLog("🎯 P2 → $p2CurrentInput")
            addBattleLog("   ${p2Result.hit}H / ${p2Result.blow}B")

            // カードモードの場合、エフェクト付きでダメージ計算
            if (isCardMode) {
                // ステップ1: 初手スキル（ラウンド開始時のバフカード）発動演出
                if (p1NextBuff != null) {
                    showCardEffect(Player.P1, p1NextBuff!!)
                }
                if (p2NextBuff != null) {
                    showCardEffect(Player.P2, p2NextBuff!!)
                }
                delay(600)
                
                // ステップ2: 手札スキル発動演出
                if (p1UsedCard != null) {
                    showCardEffect(Player.P1, p1UsedCard!!)
                }
                if (p2UsedCard != null) {
                    showCardEffect(Player.P2, p2UsedCard!!)
                }
                delay(600)
                
                // ステップ3: Hit/Blow結果表示
                _replayEffect.value = ReplayEffect(EffectType.RESULT_DISPLAY, Player.P1, null, 0, p1Result.hit, p1Result.blow)
                delay(2000)
                
                _replayEffect.value = ReplayEffect(EffectType.RESULT_DISPLAY, Player.P2, null, 0, p2Result.hit, p2Result.blow)
                delay(2000)
                
                // ステップ4: P1の攻撃演出（ダメージがある場合のみ）
                val p1Damage = calculateActualDamage(Player.P1, p1Result.hit, p1Result.blow)
                if (p1Damage > 0) {
                    _replayEffect.value = ReplayEffect(EffectType.ATTACK, Player.P1, Player.P2, p1Damage)
                    delay(2000)
                }
                
                processPlayerAction(Player.P1, p1CurrentInput)
                delay(600)

                // ステップ5: P2の攻撃演出（ダメージがある場合のみ）
                val p2Damage = calculateActualDamage(Player.P2, p2Result.hit, p2Result.blow)
                if (p2Damage > 0) {
                    _replayEffect.value = ReplayEffect(EffectType.ATTACK, Player.P2, Player.P1, p2Damage)
                    delay(2000)
                }

                processPlayerAction(Player.P2, p2CurrentInput)
                delay(600)
            } else {
                // 通常モードの処理（Hit/Blowのみで攻撃なし）
                // P1の結果を表示
                _replayEffect.value = ReplayEffect(EffectType.RESULT_DISPLAY, Player.P1, null, 0, p1Result.hit, p1Result.blow)
                delay(2000)
                processPlayerAction(Player.P1, p1CurrentInput)
                delay(1000)
                
                // P2の結果を表示
                _replayEffect.value = ReplayEffect(EffectType.RESULT_DISPLAY, Player.P2, null, 0, p2Result.hit, p2Result.blow)
                delay(2000)
                processPlayerAction(Player.P2, p2CurrentInput)
                delay(1000)
            }

            // リプレイ完了
            finishReplay()
        }
    }
    
    // カード使用時のエフェクト表示
    private suspend fun showCardEffect(player: Player, card: CardType) {
        when (card) {
            CardType.DEFENSE_SMALL, CardType.DEFENSE_MEDIUM, CardType.DEFENSE_LARGE -> {
                _replayEffect.value = ReplayEffect(EffectType.DEFENSE, player, null, 0)
                delay(800)
            }
            CardType.HEAL_SMALL, CardType.HEAL_MEDIUM, CardType.HEAL_LARGE -> {
                val healAmount = when (card) {
                    CardType.HEAL_SMALL -> 10
                    CardType.HEAL_MEDIUM -> 20
                    CardType.HEAL_LARGE -> 30
                    else -> 0
                }
                
                // リプレイ中にHPを変更（演出前に実行）
                if (player == Player.P1) {
                    _p1Hp.value = (p1Hp.value + healAmount).coerceIn(0, 100)
                } else {
                    _p2Hp.value = (p2Hp.value + healAmount).coerceIn(0, 100)
                }
                
                _replayEffect.value = ReplayEffect(EffectType.HEAL, player, null, healAmount)
                delay(1000)
            }
            CardType.INVINCIBLE -> {
                _replayEffect.value = ReplayEffect(EffectType.BARRIER, player, null, 0)
                delay(800)
            }
            CardType.COUNTER -> {
                _replayEffect.value = ReplayEffect(EffectType.COUNTER, player, null, 0)
                delay(800)
            }
            CardType.STEAL_HP -> {
                val targetPlayer = if (player == Player.P1) Player.P2 else Player.P1
                val stealAmount = if (player == Player.P1) {
                    10.coerceAtMost(p2Hp.value)
                } else {
                    10.coerceAtMost(p1Hp.value)
                }
                
                // リプレイ中にHPを変更（演出前に実行）
                if (player == Player.P1) {
                    _p2Hp.value = (p2Hp.value - stealAmount).coerceIn(0, 100)
                    _p1Hp.value = (p1Hp.value + stealAmount).coerceIn(0, 100)
                } else {
                    _p1Hp.value = (p1Hp.value - stealAmount).coerceIn(0, 100)
                    _p2Hp.value = (p2Hp.value + stealAmount).coerceIn(0, 100)
                }
                
                // HP吸収のエフェクトを表示
                _replayEffect.value = ReplayEffect(EffectType.STEAL_HP, player, targetPlayer, stealAmount)
                delay(1000)
            }
            else -> {
                // 攻撃バフなどは視覚的なエフェクトなし
                delay(400)
            }
        }
    }
    
    // 実際のダメージ量を計算（プレビューではなく確定値）
    private fun calculateActualDamage(attacker: Player, hit: Int, blow: Int): Int {
        // 正解時のみダメージが発生（自分の設定した数字の合計）
        if (hit != digitCount) {
            // 正解していない場合、Hit/Blowボーナスカードのダメージのみ
            val (_, _, hitBonus, blowBonus) = if (attacker == Player.P1) {
                Tuple4(p1AttackBonus, p1AttackMultiplier, p1HitBonus, p1BlowBonus)
            } else {
                Tuple4(p2AttackBonus, p2AttackMultiplier, p2HitBonus, p2BlowBonus)
            }
            
            var bonusDamage = 0
            if (hitBonus > 0 && hit > 0) bonusDamage += hit * hitBonus
            if (blowBonus > 0 && blow > 0) bonusDamage += blow * blowBonus
            
            return bonusDamage
        }
        
        // 正解した場合：自分の設定した数字の合計がベースダメージ
        val myAnswer = if (attacker == Player.P1) p1Answer else p2Answer
        val baseDamage = myAnswer.map { it.digitToInt() }.sum()
        
        val (attackBonus, attackMultiplier, hitBonus, blowBonus) = if (attacker == Player.P1) {
            Tuple4(p1AttackBonus, p1AttackMultiplier, p1HitBonus, p1BlowBonus)
        } else {
            Tuple4(p2AttackBonus, p2AttackMultiplier, p2HitBonus, p2BlowBonus)
        }
        
        // ボーナスダメージを追加
        var bonusDamage = 0
        if (hitBonus > 0 && hit > 0) bonusDamage += hit * hitBonus
        if (blowBonus > 0 && blow > 0) bonusDamage += blow * blowBonus
        
        return ((baseDamage + attackBonus) * attackMultiplier).toInt() + bonusDamage
    }
    
    // Tuple4ヘルパークラス
    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // 状態異常のサマリーを構築
    private fun buildStatusSummary(): String {
        val parts = mutableListOf<String>()

        // P1の状態異常
        val p1Status = mutableListOf<String>()
        if (p1AttackBonus > 0) p1Status.add("攻撃+${p1AttackBonus}")
        if (p1AttackMultiplier > 1.0) p1Status.add("攻撃×${p1AttackMultiplier}")
        if (p1DefenseReduction > 0) p1Status.add("防御-${p1DefenseReduction}")
        if (p1HasCounter) p1Status.add("反撃")
        if (p1IsInvincible) p1Status.add("無敵")
        if (p1HitBonus > 0) p1Status.add("Hit+${p1HitBonus}")
        if (p1BlowBonus > 0) p1Status.add("Blow+${p1BlowBonus}")

        if (p1Status.isNotEmpty()) {
            parts.add("【P1】${p1Status.joinToString(", ")}")
        }

        // P2の状態異常
        val p2Status = mutableListOf<String>()
        if (p2AttackBonus > 0) p2Status.add("攻撃+${p2AttackBonus}")
        if (p2AttackMultiplier > 1.0) p2Status.add("攻撃×${p2AttackMultiplier}")
        if (p2DefenseReduction > 0) p2Status.add("防御-${p2DefenseReduction}")
        if (p2HasCounter) p2Status.add("反撃")
        if (p2IsInvincible) p2Status.add("無敵")
        if (p2HitBonus > 0) p2Status.add("Hit+${p2HitBonus}")
        if (p2BlowBonus > 0) p2Status.add("Blow+${p2BlowBonus}")

        if (p2Status.isNotEmpty()) {
            parts.add("【P2】${p2Status.joinToString(", ")}")
        }

        return parts.joinToString("\n")
    }

    private var roundWinner: Player? = null

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

            // 3ヒット（正解）した場合：ラウンド終了フラグを立てる
            if (result.hit == digitCount) {
                roundWinner = player
                addBattleLog("🎯 ${player.name} が正解！ラウンド${_currentRound.value} 終了")
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
        // カード使用記録をクリア（使用したターンのみ）
        p1UsedCard = null
        p2UsedCard = null

        // 手札カード効果を1ターン後にクリア
        clearOneTimeCardEffects()

        // リプレイ完了処理
        if (_winner.value == null && _phase.value != GamePhase.FINISHED) {
            // ラウンド終了チェック
            if (roundWinner != null) {
                // 3hitされたプレイヤーがいる場合、次のラウンドへ
                _currentRound.value += 1
                roundWinner = null

                // Answerとログをリセット
                p1Answer = ""
                p2Answer = ""
                _p1Logs.value = emptyList()
                _p2Logs.value = emptyList()

                viewModelScope.launch {
                    delay(1500)
                    startNewRound()
                }
            } else {
                // 通常のターン継続
                _phase.value = GamePhase.PLAYING
                _currentPlayer.value = Player.P1
                p1CurrentInput = ""
                p2CurrentInput = ""

                // ターン数をインクリメント
                _currentTurn.value += 1
            }
        }
        
        // エフェクトをクリア
        _replayEffect.value = ReplayEffect(EffectType.NONE, Player.P1)
    }

    // 1ターン限定の手札カード効果をクリア
    private fun clearOneTimeCardEffects() {
        // 攻撃バフは使用後即クリア（既にcalculateCardModeDamageでクリアされている）
        // 以下は念のため確認してクリア
        p1AttackBonus = 0
        p2AttackBonus = 0
        p1AttackMultiplier = 1.0
        p2AttackMultiplier = 1.0

        // 防御系は使用されなければクリア
        p1DefenseReduction = 0
        p2DefenseReduction = 0
        p1DefenseMultiplier = 1.0
        p2DefenseMultiplier = 1.0

        // 無敵と反撃は1ターンで消える
        p1IsInvincible = false
        p2IsInvincible = false
        p1HasCounter = false
        p2HasCounter = false

        // Hit/Blowボーナスも使用後クリア（既にクリアされているが念のため）
        p1HitBonus = 0
        p2HitBonus = 0
        p1BlowBonus = 0
        p2BlowBonus = 0

        // ステータス表示を更新
        updateStatusEffects()
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
            CardType.HEAL_SMALL, CardType.HEAL_MEDIUM, CardType.HEAL_LARGE -> {
                // HP変更はリプレイ時にshowCardEffect内で実行されるため、ここでは何もしない
                addBattleLog("🃏 $playerName カード使用: ${card.title}")
            }
            else -> {} // 補助系カードはここでは処理しない
        }
        updateStatusEffects() // ステータス更新
    }

    // 補助系カードを使用する
    fun useHandCard(player: Player, card: CardType) {
        val playerName = if (player == Player.P1) "P1" else "P2"

        // カード使用を記録
        if (player == Player.P1) {
            p1UsedCard = card
        } else {
            p2UsedCard = card
        }

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
                // HP変更はリプレイ時にshowCardEffect内で実行されるため、ここでは何もしない
                addBattleLog("🃏 $playerName カード使用: ${card.title}")
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
                // P1がスキップしたら、使用カードをクリア
                p1UsedCard = null
                addBattleLog("⏭️ P1 カード使用をスキップ")
                _phase.value = GamePhase.WAITING_P2_INPUT
                _currentPlayer.value = Player.P2
            }
            GamePhase.CARD_USE_P2 -> {
                // P2がスキップしたら、使用カードをクリア
                p2UsedCard = null
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
