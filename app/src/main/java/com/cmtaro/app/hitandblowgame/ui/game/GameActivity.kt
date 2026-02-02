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

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    private lateinit var p1Adapter: GuessLogAdapter
    private lateinit var p2Adapter: GuessLogAdapter
    private lateinit var battleLogAdapter: BattleLogAdapter

    private var currentInputString = ""
    private var digitCount = 3 // Intentから受け取った値で上書きされる
    
    // アニメーション制御用
    private var lastRound = 1
    private var lastTurn = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初期設定
        digitCount = intent.getIntExtra("DIGIT_COUNT", 3)
        val isCardMode = intent.getBooleanExtra("IS_CARD_MODE", false)

        viewModel.setCardMode(isCardMode)
        viewModel.setDigitCount(digitCount)

        // カードモード専用UIの表示制御
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

            // リプレイオーバーレイの監視
            lifecycleScope.launch {
                viewModel.showReplayOverlay.collect { show ->
                    if (show) {
                        binding.layoutReplayOverlay.visibility = View.VISIBLE
                        animateFadeIn(binding.layoutReplayOverlay)
                    } else {
                        animateFadeOut(binding.layoutReplayOverlay) {
                            binding.layoutReplayOverlay.visibility = View.GONE
                        }
                    }
                }
            }

            lifecycleScope.launch {
                viewModel.replayMessage.collect { message ->
                    binding.textReplayMessage.text = message
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

            // ダメージ情報の表示
            lifecycleScope.launch {
                viewModel.lastDamageInfo.collect { damageInfo ->
                    if (damageInfo.isNotEmpty()) {
                        binding.textDamageInfo.text = damageInfo
                        // ダメージ情報表示時にアニメーション
                        animatePopUp(binding.textDamageInfo)
                    }
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

            // ステータス効果監視
            lifecycleScope.launch {
                viewModel.p1StatusEffects.collect { status ->
                    binding.textP1Status.text = status
                    if (status.isEmpty()) {
                        if (binding.textP1Status.visibility == View.VISIBLE) {
                            animateFadeOut(binding.textP1Status) {
                                binding.textP1Status.visibility = View.GONE
                            }
                        }
                    } else {
                        if (binding.textP1Status.visibility != View.VISIBLE) {
                            binding.textP1Status.visibility = View.VISIBLE
                            animateFadeIn(binding.textP1Status)
                        } else {
                            animatePopUp(binding.textP1Status)
                        }
                    }
                }
            }
            lifecycleScope.launch {
                viewModel.p2StatusEffects.collect { status ->
                    binding.textP2Status.text = status
                    if (status.isEmpty()) {
                        if (binding.textP2Status.visibility == View.VISIBLE) {
                            animateFadeOut(binding.textP2Status) {
                                binding.textP2Status.visibility = View.GONE
                            }
                        }
                    } else {
                        if (binding.textP2Status.visibility != View.VISIBLE) {
                            binding.textP2Status.visibility = View.VISIBLE
                            animateFadeIn(binding.textP2Status)
                        } else {
                            animatePopUp(binding.textP2Status)
                        }
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

    // 手札確認ダイアログを表示
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

    // 手札カード使用ダイアログを表示
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

    // フェードインアニメーション
    private fun animateFadeIn(view: View, duration: Long = 300) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    // フェードアウトアニメーション
    private fun animateFadeOut(view: View, onEnd: () -> Unit = {}, duration: Long = 300) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction(onEnd)
            .start()
    }

    // プレイヤー切り替えアニメーション
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

    // ダメージ受けた時のパルスアニメーション（赤く点滅）
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

    // スケールアニメーション（ポップアップ）
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

    // ボタンプレスアニメーション
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
    
    // パルスアニメーション（既存Viewの強調用）
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
}