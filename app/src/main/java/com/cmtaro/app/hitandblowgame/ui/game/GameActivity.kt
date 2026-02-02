package com.cmtaro.app.hitandblowgame.ui.game

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cmtaro.app.hitandblowgame.databinding.ActivityGameBinding
import kotlinx.coroutines.launch

/**
 * ヒットアンドブロー（およびカードモード）のメインゲーム画面を表示するActivity。
 * ViewModelからの状態を監視し、UIの更新やアニメーションの制御を行います。
 */
class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    private lateinit var p1Adapter: GuessLogAdapter
    private lateinit var p2Adapter: GuessLogAdapter
    private lateinit var battleLogAdapter: BattleLogAdapter

    /** 現在入力中の文字列 */
    private var currentInputString = ""
    /** 推測する数字の桁数（初期値は3） */
    private var digitCount = 3 
    
    /** アニメーション制御用の前回ラウンド数 */
    private var lastRound = 1
    /** アニメーション制御用の前回ターン数 */
    private var lastTurn = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // インテントから設定値を取得
        digitCount = intent.getIntExtra("DIGIT_COUNT", 3)
        val isCardMode = intent.getBooleanExtra("IS_CARD_MODE", false)

        viewModel.setCardMode(isCardMode)
        viewModel.setDigitCount(digitCount)

        // カードモード専用UIの初期表示制御
        if (isCardMode) {
            binding.layoutHp.visibility = View.VISIBLE
            binding.layoutProgressInfo.visibility = View.VISIBLE
            binding.textDamageInfo.visibility = View.VISIBLE
            binding.recyclerBattleLog.visibility = View.VISIBLE
        } else {
            binding.layoutHp.visibility = View.GONE
            binding.layoutProgressInfo.visibility = View.GONE
            binding.textDamageInfo.visibility = View.GONE
            binding.recyclerBattleLog.visibility = View.GONE
        }

        setupRecyclerViews()
        setupNumericKeypad()
        setupObservers()
    }

    /**
     * ログ表示用のRecyclerViewを初期化します。
     */
    private fun setupRecyclerViews() {
        p1Adapter = GuessLogAdapter()
        p2Adapter = GuessLogAdapter()
        battleLogAdapter = BattleLogAdapter()

        binding.recyclerP1Logs.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = p1Adapter
        }
        binding.recyclerP2Logs.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = p2Adapter
        }
        binding.recyclerBattleLog.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = battleLogAdapter
        }
    }

    /**
     * 数字キーパッドのクリックリスナーを設定します。
     */
    private fun setupNumericKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )

        buttons.forEach { button ->
            button.setOnClickListener {
                if (currentInputString.length < digitCount) {
                    currentInputString += button.text
                    updateInputDisplay()
                    // ボタン押下時のフィードバックアニメーション
                    animateButtonPress(button)
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            if (currentInputString.isNotEmpty()) {
                currentInputString = currentInputString.dropLast(1)
                updateInputDisplay()
                animateButtonPress(binding.btnDelete)
            }
        }

        binding.buttonSubmit.setOnClickListener {
            if (currentInputString.length == digitCount) {
                viewModel.onInputSubmitted(currentInputString)
                currentInputString = ""
                updateInputDisplay()
                animateButtonPress(binding.buttonSubmit)
            }
        }
    }

    /**
     * 現在の入力状態を画面に反映します。
     * 設定フェーズでは数字を伏せ、プレイフェーズでは数字を表示します。
     */
    private fun updateInputDisplay() {
        val phase = viewModel.phase.value
        if (phase == GamePhase.SETTING_P1 || phase == GamePhase.SETTING_P2) {
            // 設定フェーズは伏せ字
            binding.textCurrentInput.text = "● ".repeat(currentInputString.length) +
                    "- ".repeat(digitCount - currentInputString.length)
        } else {
            // プレイフェーズは数字表示
            binding.textCurrentInput.text = currentInputString.padEnd(digitCount, '-').chunked(1).joinToString(" ")
        }
    }

    /**
     * ViewModelの各StateFlowを監視し、UIを更新します。
     */
    private fun setupObservers() {
        val isCardMode = intent.getBooleanExtra("IS_CARD_MODE", false)

        // --- 1. フェーズとターンの総合監視 ---
        lifecycleScope.launch {
            viewModel.phase.collect { phase ->
                // フェーズ変更時にアニメーション
                animateFadeIn(binding.textInstruction)

                updateInputDisplay()
                binding.textInstruction.text = when (phase) {
                    GamePhase.SETTING_P1 -> "P1: 数字セット"
                    GamePhase.CARD_SELECT_P1 -> "P1: バフカード選択"
                    GamePhase.HAND_CONFIRM_P1 -> "P1: 手札確認（OKを押してください）"
                    GamePhase.SETTING_P2 -> "P2: 数字セット"
                    GamePhase.CARD_SELECT_P2 -> "P2: バフカード選択"
                    GamePhase.HAND_CONFIRM_P2 -> "P2: 手札確認（OKを押してください）"
                    GamePhase.PLAYING -> if (isCardMode) "P1: 数字を入力" else "P1: 推測"
                    GamePhase.CARD_USE_P1 -> "P1: 手札カードを使用できます"
                    GamePhase.WAITING_P2_INPUT -> if (isCardMode) "P2: 数字を入力" else "P2: 推測"
                    GamePhase.CARD_USE_P2 -> "P2: 手札カードを使用できます"
                    GamePhase.REPLAYING -> "リプレイ中..."
                    GamePhase.FINISHED -> "試合終了"
                }

                // 入力エリアの表示/非表示制御
                val showInput = phase in listOf(
                    GamePhase.SETTING_P1, GamePhase.SETTING_P2,
                    GamePhase.PLAYING, GamePhase.WAITING_P2_INPUT
                )
                binding.layoutInput.visibility = if (showInput) View.VISIBLE else View.GONE

                // 手札確認フェーズの処理
                if (isCardMode && (phase == GamePhase.HAND_CONFIRM_P1 || phase == GamePhase.HAND_CONFIRM_P2)) {
                    showHandConfirmDialog(phase)
                }

                // 手札カード使用フェーズの処理
                if (isCardMode && (phase == GamePhase.CARD_USE_P1 || phase == GamePhase.CARD_USE_P2)) {
                    showHandCardDialog(phase)
                }
            }
        }

        // --- カードモード専用の監視 ---
        if (isCardMode) {
            // 手札の監視（自分のターンのみ表示）
            lifecycleScope.launch {
                viewModel.currentPlayer.collect { player ->
                    lifecycleScope.launch {
                        viewModel.p1HandCards.collect { cards ->
                            if (player == Player.P1 && cards.isNotEmpty()) {
                                val cardText = "手札: ${cards.joinToString(", ") { it.title }}"
                                binding.textDamageInfo.text = cardText
                            }
                        }
                    }
                    lifecycleScope.launch {
                        viewModel.p2HandCards.collect { cards ->
                            if (player == Player.P2 && cards.isNotEmpty()) {
                                val cardText = "手札: ${cards.joinToString(", ") { it.title }}"
                                binding.textDamageInfo.text = cardText
                            }
                        }
                    }
                }
            }

            // リプレイエフェクトの監視
            lifecycleScope.launch {
                viewModel.replayEffect.collect { effect ->
                    when (effect.type) {
                        EffectType.RESULT_DISPLAY -> {
                            showResultDisplay(effect.player, effect.hit, effect.blow)
                        }
                        EffectType.ATTACK -> {
                            showAttackEffect(effect.player, effect.targetPlayer!!, effect.value)
                        }
                        EffectType.DEFENSE -> {
                            showDefenseEffect(effect.player)
                        }
                        EffectType.HEAL -> {
                            showHealEffect(effect.player, effect.value)
                        }
                        EffectType.BARRIER -> {
                            val targetView = if (effect.player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
                            showBarrierEffect(targetView)
                        }
                        EffectType.COUNTER -> {
                            showCounterEffect(effect.player)
                        }
                        EffectType.STEAL_HP -> {
                            showStealHpEffect(effect.player, effect.targetPlayer!!, effect.value)
                        }
                        EffectType.NONE -> { /* 何もしない */ }
                    }
                }
            }

            // ラウンドとターンの表示
            lifecycleScope.launch {
                viewModel.currentRound.collect { round ->
                    binding.textRoundInfo.text = "ラウンド: $round"
                    // ラウンド変更時のみアニメーション（初回はスキップ）
                    if (round != lastRound && lastRound > 0) {
                        animatePulse(binding.textRoundInfo)
                    }
                    lastRound = round
                }
            }

            lifecycleScope.launch {
                viewModel.currentTurn.collect { turn ->
                    binding.textTurnInfo.text = "ターン: $turn"
                    // ターン変更時のみアニメーション（初回はスキップ）
                    if (turn != lastTurn && lastTurn > 0) {
                        animatePulse(binding.textTurnInfo)
                    }
                    lastTurn = turn
                }
            }

            lifecycleScope.launch {
                viewModel.totalTurns.collect { total ->
                    binding.textTotalTurns.text = "総ターン数: $total"
                }
            }

            // HP監視
            lifecycleScope.launch {
                viewModel.p1Hp.collect { hp ->
                    val prevHp = binding.progressP1Hp.progress
                    binding.progressP1Hp.progress = hp
                    binding.textP1Hp.text = "P1 HP: $hp"

                    // HPが減った場合、ダメージアニメーション
                    if (hp < prevHp) {
                        animateDamage(binding.layoutP1Status)
                    }
                }
            }
            lifecycleScope.launch {
                viewModel.p2Hp.collect { hp ->
                    val prevHp = binding.progressP2Hp.progress
                    binding.progressP2Hp.progress = hp
                    binding.textP2Hp.text = "P2 HP: $hp"

                    // HPが減った場合、ダメージアニメーション
                    if (hp < prevHp) {
                        animateDamage(binding.layoutP2Status)
                    }
                }
            }

            // バトルログ監視
            lifecycleScope.launch {
                viewModel.battleLog.collect { logs ->
                    battleLogAdapter.submitList(logs)
                    // 最新ログを表示するため、スクロール
                    if (logs.isNotEmpty()) {
                        binding.recyclerBattleLog.smoothScrollToPosition(logs.size - 1)
                    }
                }
            }

            // カード選択画面の監視
            lifecycleScope.launch {
                viewModel.availableCards.collect { cards ->
                    if (cards.isNotEmpty()) {
                        val currentPhase = viewModel.phase.value
                        val player = when (currentPhase) {
                            GamePhase.CARD_SELECT_P1 -> Player.P1
                            GamePhase.CARD_SELECT_P2 -> Player.P2
                            else -> viewModel.currentPlayer.value
                        }

                        val playerName = if (player == Player.P1) "P1" else "P2"
                        val phaseText = when (currentPhase) {
                            GamePhase.CARD_SELECT_P1, GamePhase.CARD_SELECT_P2 ->
                                "🎴【ラウンド開始】$playerName がカードを選択してください"
                            else -> "🎁【ボーナス】$playerName がカードを獲得！"
                        }

                        val categoryText = if (cards.firstOrNull()?.category == CardCategory.BUFF) {
                            "\n\n✨ラウンド中に効果が適用されます"
                        } else {
                            "\n\n🃏ゲーム中に手動で使用できます"
                        }

                        val items = cards.mapIndexed { index, card ->
                            "${index + 1}. 【${card.title}】\n   ${card.description}"
                        }.toTypedArray()

                        val dialog = androidx.appcompat.app.AlertDialog.Builder(this@GameActivity)
                            .setTitle(phaseText + categoryText)
                            .setItems(items) { _, which ->
                                viewModel.onCardSelected(player, cards[which])
                            }
                            .setCancelable(false)
                            .create()

                        dialog.show()

                        // ダイアログにアニメーションを追加
                        dialog.window?.decorView?.let { animatePopUp(it) }
                    }
                }
            }
        }

        // --- 共通の監視（通常モード・カードモード共通） ---
        lifecycleScope.launch {
            viewModel.currentPlayer.collect { player ->
                val playerName = if (player == Player.P1) "P1" else "P2"
                binding.textCurrentPlayer.text = "$playerName の番です"

                // 視覚的なターン強調（アニメーション付き）
                animatePlayerSwitch(
                    binding.recyclerP1Logs,
                    binding.recyclerP2Logs,
                    player == Player.P1
                )

                // カードモードの場合のみHPステータスの強調
                if (isCardMode) {
                    animatePlayerSwitch(
                        binding.layoutP1Status,
                        binding.layoutP2Status,
                        player == Player.P1
                    )
                }
            }
        }

        // ログと勝利監視
        lifecycleScope.launch { viewModel.p1Logs.collect { p1Adapter.submitList(it) } }
        lifecycleScope.launch { viewModel.p2Logs.collect { p2Adapter.submitList(it) } }
        lifecycleScope.launch {
            viewModel.winner.collect { winner ->
                winner?.let {
                    Toast.makeText(this@GameActivity, "${it.name} の勝利！", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 手札確認ダイアログを表示します。
     * @param phase 現在のゲームフェーズ
     */
    private fun showHandConfirmDialog(phase: GamePhase) {
        val player = if (phase == GamePhase.HAND_CONFIRM_P1) Player.P1 else Player.P2
        val playerName = if (player == Player.P1) "P1" else "P2"
        val handCards = if (player == Player.P1)
            viewModel.p1HandCards.value else viewModel.p2HandCards.value

        val cardList = handCards.mapIndexed { index, card ->
            "${index + 1}. 【${card.title}】 - ${card.description}"
        }.joinToString("\n")

        val message = "このラウンドで使える手札カード（3枚）:\n\n$cardList\n\n※ターンごとに1枚使用できます\n※相手には見えません"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$playerName の手札カード配布")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                viewModel.confirmHandCards()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 手札カード使用ダイアログを表示します。
     * @param phase 現在のゲームフェーズ
     */
    private fun showHandCardDialog(phase: GamePhase) {
        val player = if (phase == GamePhase.CARD_USE_P1) Player.P1 else Player.P2
        val playerName = if (player == Player.P1) "P1" else "P2"
        val handCards = if (player == Player.P1)
            viewModel.p1HandCards.value else viewModel.p2HandCards.value

        if (handCards.isEmpty()) {
            // 手札がない場合は自動的にスキップ
            viewModel.skipCardUse()
            return
        }

        val items = mutableListOf<String>()
        items.add("【スキップ】カードを使わない")
        handCards.forEachIndexed { index, card ->
            items.add("${index + 1}. 【${card.title}】 - ${card.description}")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$playerName の手札カード\n※相手には見えません")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    // スキップ選択
                    viewModel.skipCardUse()
                } else {
                    // カード使用
                    val selectedCard = handCards[which - 1]
                    viewModel.useHandCard(player, selectedCard)
                }
            }
            .setCancelable(false)
            .show()
    }

    // === アニメーション関数 ===

    /**
     * Viewをフェードインさせるアニメーション。
     * @param view 対象のView
     * @param duration アニメーション時間（ミリ秒）
     */
    private fun animateFadeIn(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * Viewをフェードアウトさせるアニメーション。
     * @param view 対象のView
     * @param duration アニメーション時間（ミリ秒）
     * @param onEnd アニメーション終了時のコールバック
     */
    private fun animateFadeOut(view: View, duration: Long = 300, onEnd: () -> Unit = {}) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction(onEnd)
            .start()
    }

    /**
     * プレイヤーの交代を視覚的に強調するアニメーション。
     * アクティブなプレイヤーのViewを不透明かつ少し大きくし、非アクティブな方を半透明かつ小さくします。
     */
    private fun animatePlayerSwitch(view1: View, view2: View, isPlayer1Active: Boolean) {
        val activeAlpha = 1.0f
        val inactiveAlpha = 0.3f
        val activeScale = 1.05f
        val inactiveScale = 0.95f

        // Player 1
        view1.animate()
            .alpha(if (isPlayer1Active) activeAlpha else inactiveAlpha)
            .scaleX(if (isPlayer1Active) activeScale else inactiveScale)
            .scaleY(if (isPlayer1Active) activeScale else inactiveScale)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // Player 2
        view2.animate()
            .alpha(if (isPlayer1Active) inactiveAlpha else activeAlpha)
            .scaleX(if (isPlayer1Active) inactiveScale else activeScale)
            .scaleY(if (isPlayer1Active) inactiveScale else activeScale)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * ダメージを受けた際の揺れと色の変化によるアニメーション。
     */
    private fun animateDamage(view: View) {
        val originalBackground = view.background
        val originalElevation = view.elevation

        // 震えるアニメーション
        val shakeX = ObjectAnimator.ofFloat(view, "translationX", 0f, -15f, 15f, -10f, 10f, -5f, 5f, 0f)
        shakeX.duration = 400

        // 赤く点滅
        view.setBackgroundColor(Color.parseColor("#FFCCCC"))
        view.elevation = 8f

        view.postDelayed({
            view.background = originalBackground
            view.elevation = originalElevation
        }, 400)

        shakeX.start()
    }

    /**
     * Viewが飛び出すようなスケールアニメーション。
     */
    private fun animatePopUp(view: View) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.2f, 1f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, alpha)
        animSet.duration = 400
        animSet.interpolator = OvershootInterpolator()
        animSet.start()
    }

    /**
     * ボタンが押された際のフィードバックアニメーション（縮小して元に戻る）。
     */
    private fun animateButtonPress(view: View) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f)
            )
            duration = 50
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1f)
            )
            duration = 100
            interpolator = OvershootInterpolator()
        }

        scaleDown.start()
        scaleDown.doOnEnd { scaleUp.start() }
    }
    
    /**
     * Viewを一瞬大きくして強調するパルスアニメーション。
     */
    private fun animatePulse(view: View) {
        val pulse = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f, 1f)
            )
            duration = 300
            interpolator = OvershootInterpolator()
        }
        pulse.start()
    }

    // === リプレイ演出専用のアニメーション ===

    /**
     * Hit/Blow結果を表示
     * @param player 結果を表示するプレイヤー
     * @param hit Hit数
     * @param blow Blow数
     */
    fun showResultDisplay(player: Player, hit: Int, blow: Int) {
        lifecycleScope.launch {
            val targetView = if (player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            
            // 結果テキストを表示
            binding.textFloatingDamage.text = "$hit Hit / $blow Blow"
            binding.textFloatingDamage.setTextColor(Color.parseColor("#FFD700"))
            binding.textFloatingDamage.textSize = 28f
            binding.textFloatingDamage.x = location[0].toFloat() + (targetView.width / 2) - 150
            binding.textFloatingDamage.y = location[1].toFloat() + targetView.height / 2 - 50
            binding.textFloatingDamage.visibility = View.VISIBLE
            binding.textFloatingDamage.alpha = 0f
            binding.textFloatingDamage.scaleX = 0.5f
            binding.textFloatingDamage.scaleY = 0.5f
            
            // フェードイン＆拡大
            val fadeIn = ObjectAnimator.ofFloat(binding.textFloatingDamage, "alpha", 0f, 1f)
            val scaleUpX = ObjectAnimator.ofFloat(binding.textFloatingDamage, "scaleX", 0.5f, 1.2f, 1f)
            val scaleUpY = ObjectAnimator.ofFloat(binding.textFloatingDamage, "scaleY", 0.5f, 1.2f, 1f)
            
            val showAnim = AnimatorSet().apply {
                playTogether(fadeIn, scaleUpX, scaleUpY)
                duration = 400
            }
            
            showAnim.start()
            
            // 0.8秒後にフェードアウト
            binding.textFloatingDamage.postDelayed({
                val fadeOut = ObjectAnimator.ofFloat(binding.textFloatingDamage, "alpha", 1f, 0f)
                fadeOut.duration = 300
                fadeOut.start()
                fadeOut.doOnEnd {
                    binding.textFloatingDamage.visibility = View.GONE
                    binding.textFloatingDamage.textSize = 48f // 元に戻す
                }
            }, 800)
        }
    }

    /**
     * 攻撃エフェクトを表示（画面全体を使った演出）
     * @param fromPlayer 攻撃元のプレイヤー
     * @param toPlayer 攻撃先のプレイヤー
     * @param damage ダメージ量
     */
    fun showAttackEffect(fromPlayer: Player, toPlayer: Player, damage: Int) {
        lifecycleScope.launch {
            // 攻撃元と攻撃先の位置を取得
            val fromView = if (fromPlayer == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            val toView = if (toPlayer == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            val fromLocation = IntArray(2)
            fromView.getLocationOnScreen(fromLocation)
            val toLocation = IntArray(2)
            toView.getLocationOnScreen(toLocation)
            
            // エフェクトビューを大きく、目立つように設定
            binding.viewAttackEffect.visibility = View.VISIBLE
            binding.viewAttackEffect.x = fromLocation[0].toFloat() + fromView.width / 2 - 100
            binding.viewAttackEffect.y = fromLocation[1].toFloat() + fromView.height / 2 - 100
            binding.viewAttackEffect.setBackgroundColor(Color.parseColor("#FF3D00"))
            binding.viewAttackEffect.alpha = 1f
            binding.viewAttackEffect.scaleX = 2f
            binding.viewAttackEffect.scaleY = 2f
            
            // 大きく派手に相手に向かって移動
            val targetX = toLocation[0].toFloat() + toView.width / 2 - 100
            val targetY = toLocation[1].toFloat() + toView.height / 2 - 100
            
            val moveX = ObjectAnimator.ofFloat(binding.viewAttackEffect, "x", 
                binding.viewAttackEffect.x, targetX)
            val moveY = ObjectAnimator.ofFloat(binding.viewAttackEffect, "y", 
                binding.viewAttackEffect.y, targetY)
            val scale = ObjectAnimator.ofFloat(binding.viewAttackEffect, "scaleX", 2f, 3f, 2f)
            val scaleY = ObjectAnimator.ofFloat(binding.viewAttackEffect, "scaleY", 2f, 3f, 2f)
            val rotate = ObjectAnimator.ofFloat(binding.viewAttackEffect, "rotation", 0f, 360f)
            
            val attackAnim = AnimatorSet().apply {
                playTogether(moveX, moveY, scale, scaleY, rotate)
                duration = 800
            }
            
            attackAnim.start()
            attackAnim.doOnEnd {
                // 着弾時にダメージ数値を表示＆振動エフェクト
                animateDamage(toView)
                showFloatingDamage(toView, damage, false)
                binding.viewAttackEffect.visibility = View.GONE
                binding.viewAttackEffect.rotation = 0f
            }
        }
    }
    
    /**
     * 防御エフェクトを表示（プレイヤーの画面半分に）
     * @param player 防御するプレイヤー
     */
    fun showDefenseEffect(player: Player) {
        lifecycleScope.launch {
            val targetView = if (player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            // シールドのような青い光
            binding.viewAttackEffect.visibility = View.VISIBLE
            binding.viewAttackEffect.setBackgroundColor(Color.parseColor("#2196F3"))
            
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            binding.viewAttackEffect.x = location[0].toFloat()
            binding.viewAttackEffect.y = location[1].toFloat()
            binding.viewAttackEffect.alpha = 0f
            binding.viewAttackEffect.scaleX = 3f
            binding.viewAttackEffect.scaleY = 3f
            
            // パルスアニメーション
            val fadeIn = ObjectAnimator.ofFloat(binding.viewAttackEffect, "alpha", 0f, 0.8f, 0f)
            val pulse = AnimatorSet().apply {
                play(fadeIn)
                duration = 600
            }
            
            pulse.start()
            pulse.doOnEnd {
                binding.viewAttackEffect.visibility = View.GONE
            }
        }
    }
    
    /**
     * 回復エフェクトを表示（プレイヤーの画面半分に）
     * @param player 回復するプレイヤー
     * @param amount 回復量
     */
    fun showHealEffect(player: Player, amount: Int) {
        lifecycleScope.launch {
            val targetView = if (player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            // 緑の光
            binding.viewAttackEffect.visibility = View.VISIBLE
            binding.viewAttackEffect.setBackgroundColor(Color.parseColor("#4CAF50"))
            
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            binding.viewAttackEffect.x = location[0].toFloat()
            binding.viewAttackEffect.y = location[1].toFloat()
            binding.viewAttackEffect.alpha = 0f
            binding.viewAttackEffect.scaleX = 2f
            binding.viewAttackEffect.scaleY = 2f
            
            // パルスアニメーション
            val fadeIn = ObjectAnimator.ofFloat(binding.viewAttackEffect, "alpha", 0f, 0.7f, 0f)
            val scaleUp = ObjectAnimator.ofFloat(binding.viewAttackEffect, "scaleX", 2f, 4f)
            val scaleUpY = ObjectAnimator.ofFloat(binding.viewAttackEffect, "scaleY", 2f, 4f)
            
            val healAnim = AnimatorSet().apply {
                playTogether(fadeIn, scaleUp, scaleUpY)
                duration = 800
            }
            
            healAnim.start()
            healAnim.doOnEnd {
                showFloatingDamage(targetView, amount, true)
                binding.viewAttackEffect.visibility = View.GONE
            }
        }
    }
    
    /**
     * カウンターエフェクトを表示
     * @param player カウンターを構えるプレイヤー
     */
    fun showCounterEffect(player: Player) {
        lifecycleScope.launch {
            val targetView = if (player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            // オレンジの閃光
            binding.viewAttackEffect.visibility = View.VISIBLE
            binding.viewAttackEffect.setBackgroundColor(Color.parseColor("#FF9800"))
            
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            binding.viewAttackEffect.x = location[0].toFloat()
            binding.viewAttackEffect.y = location[1].toFloat()
            binding.viewAttackEffect.alpha = 0f
            binding.viewAttackEffect.scaleX = 2.5f
            binding.viewAttackEffect.scaleY = 2.5f
            
            // 素早い点滅
            val flash1 = ObjectAnimator.ofFloat(binding.viewAttackEffect, "alpha", 0f, 1f, 0f)
            flash1.duration = 200
            val flash2 = ObjectAnimator.ofFloat(binding.viewAttackEffect, "alpha", 0f, 1f, 0f)
            flash2.duration = 200
            
            val counterAnim = AnimatorSet().apply {
                playSequentially(flash1, flash2)
            }
            
            counterAnim.start()
            counterAnim.doOnEnd {
                binding.viewAttackEffect.visibility = View.GONE
            }
        }
    }
    
    /**
     * HP吸収エフェクトを表示
     * @param player HP吸収するプレイヤー
     * @param targetPlayer HP吸収されるプレイヤー
     * @param amount 吸収量
     */
    fun showStealHpEffect(player: Player, targetPlayer: Player, amount: Int) {
        lifecycleScope.launch {
            val fromView = if (targetPlayer == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            val toView = if (player == Player.P1) binding.layoutP1Status else binding.layoutP2Status
            
            // 紫の吸収エフェクト
            binding.viewAttackEffect.visibility = View.VISIBLE
            binding.viewAttackEffect.setBackgroundColor(Color.parseColor("#9C27B0"))
            
            val fromLocation = IntArray(2)
            val toLocation = IntArray(2)
            fromView.getLocationOnScreen(fromLocation)
            toView.getLocationOnScreen(toLocation)
            
            // 対象から自分へ移動
            binding.viewAttackEffect.x = fromLocation[0].toFloat()
            binding.viewAttackEffect.y = fromLocation[1].toFloat()
            binding.viewAttackEffect.alpha = 0.8f
            binding.viewAttackEffect.scaleX = 2f
            binding.viewAttackEffect.scaleY = 2f
            
            val moveX = ObjectAnimator.ofFloat(binding.viewAttackEffect, "x", fromLocation[0].toFloat(), toLocation[0].toFloat())
            val moveY = ObjectAnimator.ofFloat(binding.viewAttackEffect, "y", fromLocation[1].toFloat(), toLocation[1].toFloat())
            val fadeOut = ObjectAnimator.ofFloat(binding.viewAttackEffect, "alpha", 0.8f, 0f)
            
            val stealAnim = AnimatorSet().apply {
                playTogether(moveX, moveY, fadeOut)
                duration = 600
            }
            
            stealAnim.start()
            stealAnim.doOnEnd {
                // 吸収元にマイナス表示
                showFloatingDamage(fromView, amount, false)
                // 吸収先にプラス表示
                showFloatingDamage(toView, amount, true)
                binding.viewAttackEffect.visibility = View.GONE
            }
        }
    }
    
    /**
     * フローティングダメージ数値を表示
     * @param targetView ダメージを受けたView
     * @param value ダメージ/回復量
     * @param isHeal 回復かどうか
     */
    fun showFloatingDamage(targetView: View, value: Int, isHeal: Boolean) {
        lifecycleScope.launch {
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            
            binding.textFloatingDamage.text = if (isHeal) "+$value" else "-$value"
            binding.textFloatingDamage.setTextColor(if (isHeal) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
            binding.textFloatingDamage.x = location[0].toFloat() + (targetView.width / 2) - 50
            binding.textFloatingDamage.y = location[1].toFloat()
            binding.textFloatingDamage.visibility = View.VISIBLE
            binding.textFloatingDamage.alpha = 1f
            binding.textFloatingDamage.scaleX = 0.5f
            binding.textFloatingDamage.scaleY = 0.5f
            
            // 上に浮かび上がりながらフェードアウト
            val moveUp = ObjectAnimator.ofFloat(binding.textFloatingDamage, "translationY", 0f, -150f)
            val fadeOut = ObjectAnimator.ofFloat(binding.textFloatingDamage, "alpha", 1f, 0f)
            val scaleUpX = ObjectAnimator.ofFloat(binding.textFloatingDamage, "scaleX", 0.5f, 1.5f, 1f)
            val scaleUpY = ObjectAnimator.ofFloat(binding.textFloatingDamage, "scaleY", 0.5f, 1.5f, 1f)
            
            val floatAnim = AnimatorSet().apply {
                playTogether(moveUp, fadeOut, scaleUpX, scaleUpY)
                duration = 1000
            }
            
            floatAnim.start()
            floatAnim.doOnEnd {
                binding.textFloatingDamage.visibility = View.GONE
                binding.textFloatingDamage.translationY = 0f
            }
        }
    }
    
    /**
     * バリア演出を表示（無敵時）
     * @param targetView 対象のView
     */
    fun showBarrierEffect(targetView: View) {
        lifecycleScope.launch {
            val originalElevation = targetView.elevation
            val originalBackground = targetView.background
            
            // 青白く光らせる
            targetView.setBackgroundColor(Color.parseColor("#80D1F5FF"))
            targetView.elevation = 12f
            
            val pulse = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(targetView, "scaleX", 1f, 1.1f, 1f),
                    ObjectAnimator.ofFloat(targetView, "scaleY", 1f, 1.1f, 1f)
                )
                duration = 500
                interpolator = OvershootInterpolator()
            }
            
            pulse.start()
            pulse.doOnEnd {
                targetView.postDelayed({
                    targetView.background = originalBackground
                    targetView.elevation = originalElevation
                }, 300)
            }
            
            // 「BLOCKED!」の表示
            showFloatingDamage(targetView, 0, false)
            binding.textFloatingDamage.text = "BLOCKED!"
            binding.textFloatingDamage.setTextColor(Color.parseColor("#2196F3"))
        }
    }

}
