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

    private var currentInputString = ""
    private val digitCount = 3 // 必要に応じて変更可能

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初期設定
        val digitCount = intent.getIntExtra("DIGIT_COUNT", 3)
        val isCardMode = intent.getBooleanExtra("IS_CARD_MODE", false)

        viewModel.setCardMode(isCardMode)
        viewModel.setDigitCount(digitCount)

        if (isCardMode) {
            binding.layoutHp.visibility = View.VISIBLE
        }

        setupRecyclerViews()
        setupNumericKeypad()
        setupObservers()
    }

    private fun setupRecyclerViews() {
        p1Adapter = GuessLogAdapter()
        p2Adapter = GuessLogAdapter()
        binding.recyclerP1Logs.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = p1Adapter
        }
        binding.recyclerP2Logs.apply {
            layoutManager = LinearLayoutManager(this@GameActivity)
            adapter = p2Adapter
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
        // --- 1. フェーズとターンの総合監視 ---
        lifecycleScope.launch {
            viewModel.phase.collect { phase ->
                updateInputDisplay()
                binding.textInstruction.text = when (phase) {
                    GamePhase.SETTING_P1 -> "P1: セット"
                    GamePhase.SETTING_P2 -> "P2: セット"
                    GamePhase.PLAYING -> "バトル中"
                    GamePhase.FINISHED -> "試合終了"
                }
            }
        }

        // --- ラウンドとターンの表示 ---
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
        
        // --- ダメージ情報の表示 ---
        lifecycleScope.launch {
            viewModel.lastDamageInfo.collect { damageInfo ->
                if (damageInfo.isNotEmpty()) {
                    binding.textDamageInfo.text = damageInfo
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPlayer.collect { player ->
                val playerName = if (player == Player.P1) "P1" else "P2"
                binding.textCurrentPlayer.text = "$playerName の番です"

                // 視覚的なターン強調（アクティブなプレイヤー以外を薄くする）
                binding.recyclerP1Logs.alpha = if (player == Player.P1) 1.0f else 0.3f
                binding.recyclerP2Logs.alpha = if (player == Player.P2) 1.0f else 0.3f
                binding.layoutP1Status.alpha = if (player == Player.P1) 1.0f else 0.3f
                binding.layoutP2Status.alpha = if (player == Player.P2) 1.0f else 0.3f
            }
        }

        // --- 2. カード選択画面の監視 ---
        lifecycleScope.launch {
            viewModel.availableCards.collect { cards ->
                if (cards.isNotEmpty()) {
                    val options = cards.map { it.title }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(this@GameActivity)
                        .setTitle("ボーナス")
                        .setItems(options) { _, which ->
                            viewModel.onCardSelected(viewModel.currentPlayer.value, cards[which])
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }

        // --- 3. HP・ログ・勝利監視 ---
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
}