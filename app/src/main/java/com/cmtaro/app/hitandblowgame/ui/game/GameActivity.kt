package com.cmtaro.app.hitandblowgame.ui.game

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            if (currentInputString.isNotEmpty()) {
                currentInputString = currentInputString.dropLast(1)
                updateInputDisplay()
            }
        }

        binding.buttonSubmit.setOnClickListener {
            if (currentInputString.length == digitCount) {
                viewModel.onInputSubmitted(currentInputString)
                currentInputString = ""
                updateInputDisplay()
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
                    binding.layoutReplayOverlay.visibility = if (show) View.VISIBLE else View.GONE
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
                }
            }
            
            lifecycleScope.launch {
                viewModel.currentTurn.collect { turn ->
                    binding.textTurnInfo.text = "ターン: $turn"
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
                    }
                }
            }
            
            // HP監視
            lifecycleScope.launch {
                viewModel.p1Hp.collect { hp ->
                    binding.progressP1Hp.progress = hp
                    binding.textP1Hp.text = "P1 HP: $hp"
                }
            }
            lifecycleScope.launch {
                viewModel.p2Hp.collect { hp ->
                    binding.progressP2Hp.progress = hp
                    binding.textP2Hp.text = "P2 HP: $hp"
                }
            }
            
            // ステータス効果監視
            lifecycleScope.launch {
                viewModel.p1StatusEffects.collect { status ->
                    binding.textP1Status.text = status
                    binding.textP1Status.visibility = if (status.isEmpty()) View.GONE else View.VISIBLE
                }
            }
            lifecycleScope.launch {
                viewModel.p2StatusEffects.collect { status ->
                    binding.textP2Status.text = status
                    binding.textP2Status.visibility = if (status.isEmpty()) View.GONE else View.VISIBLE
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
                                "【ラウンド開始】$playerName がカードを選択してください"
                            else -> "【ボーナス】$playerName がカードを獲得！"
                        }
                        
                        val categoryText = if (cards.firstOrNull()?.category == CardCategory.BUFF) {
                            "\n※ラウンド中に効果が適用されます"
                        } else {
                            "\n※ゲーム中に手動で使用できます"
                        }
                        
                        val items = cards.mapIndexed { index, card -> 
                            "${index + 1}. 【${card.title}】\n   ${card.description}"
                        }.toTypedArray()
                        
                        androidx.appcompat.app.AlertDialog.Builder(this@GameActivity)
                            .setTitle(phaseText + categoryText)
                            .setItems(items) { _, which ->
                                viewModel.onCardSelected(player, cards[which])
                            }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }

        // --- 共通の監視（通常モード・カードモード共通） ---
        lifecycleScope.launch {
            viewModel.currentPlayer.collect { player ->
                val playerName = if (player == Player.P1) "P1" else "P2"
                binding.textCurrentPlayer.text = "$playerName の番です"

                // 視覚的なターン強調（アクティブなプレイヤー以外を薄くする）
                binding.recyclerP1Logs.alpha = if (player == Player.P1) 1.0f else 0.3f
                binding.recyclerP2Logs.alpha = if (player == Player.P2) 1.0f else 0.3f
                
                // カードモードの場合のみHPステータスの強調
                if (isCardMode) {
                    binding.layoutP1Status.alpha = if (player == Player.P1) 1.0f else 0.3f
                    binding.layoutP2Status.alpha = if (player == Player.P2) 1.0f else 0.3f
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
}