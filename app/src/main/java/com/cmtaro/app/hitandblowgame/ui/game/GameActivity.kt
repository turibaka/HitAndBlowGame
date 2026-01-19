package com.cmtaro.app.hitandblowgame.ui.game

import android.os.Bundle
import android.widget.Button
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
    private var digitCount = 3

    // 入力中の数字を保持する
    private var currentInputString = ""

    private val p1Adapter = GuessLogAdapter()
    private val p2Adapter = GuessLogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        digitCount = intent.getIntExtra("DIGIT_COUNT", 3)
        viewModel.setDigitCount(digitCount)

        // RecyclerView設定
        setupRecyclerViews()

        // テンキーの設定
        setupNumericKeypad()

        // 決定ボタンの設定
        binding.buttonSubmit.setOnClickListener {
            if (currentInputString.length == digitCount) {
                viewModel.onInputSubmitted(currentInputString)
                resetInputState() // 入力をクリアしてボタンを復活させる
            } else {
                Toast.makeText(this, "${digitCount}桁入力してください", Toast.LENGTH_SHORT).show()
            }
        }

        setupObservers()
    }

    private fun setupRecyclerViews() {
        binding.recyclerP1Logs.apply {
            adapter = p1Adapter
            layoutManager = LinearLayoutManager(this@GameActivity)
        }
        binding.recyclerP2Logs.apply {
            adapter = p2Adapter
            layoutManager = LinearLayoutManager(this@GameActivity)
        }
    }

    private fun setupNumericKeypad() {
        // 数字ボタンをリストにまとめる
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )

        numberButtons.forEach { button ->
            button.setOnClickListener {
                val num = button.text.toString()

                // 桁数上限まで、かつ重複していなければ追加
                if (currentInputString.length < digitCount && !currentInputString.contains(num)) {
                    currentInputString += num
                    button.isEnabled = false // 同じ数字を2回押せないようにする
                    updateInputDisplay()
                }
            }
        }

        // 消去ボタンの処理
        binding.btnDelete.setOnClickListener {
            if (currentInputString.isNotEmpty()) {
                val lastChar = currentInputString.last().toString()
                currentInputString = currentInputString.dropLast(1)

                // 消した数字のボタンを再び有効にする
                numberButtons.find { it.text == lastChar }?.isEnabled = true
                updateInputDisplay()
            }
        }
    }

    // 入力エリアの文字表示を更新
    private fun updateInputDisplay() {
        binding.textCurrentInput.text = currentInputString.padEnd(digitCount, '-').chunked(1).joinToString(" ")

    }

    // 送信後にリセット
    private fun resetInputState() {
        currentInputString = ""
        updateInputDisplay()

        // 全ボタンを有効に戻す
        listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        ).forEach { it.isEnabled = true }
    }

    private fun setupObservers() {
        // フェーズ監視
        lifecycleScope.launch {
            viewModel.phase.collect { phase ->
                when (phase) {
                    GamePhase.SETTING_P1 -> binding.textInstruction.text = "プレイヤー1：${digitCount}桁の数字を決定"
                    GamePhase.SETTING_P2 -> binding.textInstruction.text = "プレイヤー2：${digitCount}桁の数字を決定"
                    GamePhase.PLAYING -> binding.textInstruction.text = "ゲーム開始！"
                    GamePhase.FINISHED -> {
                        binding.textInstruction.text = "ゲーム終了！"
                        binding.buttonSubmit.isEnabled = false
                    }
                }
                updateInputDisplay() // フェーズが変わったら伏せ字/表示を切り替える
            }
        }

        // ターン監視
        lifecycleScope.launch {
            viewModel.currentPlayer.collect { player ->
                binding.textCurrentPlayer.text = "現在：${player.name} のターン"
                if (player == Player.P1) {
                    binding.recyclerP1Logs.alpha = 1.0f
                    binding.recyclerP2Logs.alpha = 0.4f
                } else {
                    binding.recyclerP1Logs.alpha = 0.4f
                    binding.recyclerP2Logs.alpha = 1.0f
                }
            }
        }

        // ログ監視
        lifecycleScope.launch { viewModel.p1Logs.collect { p1Adapter.submitList(it) } }
        lifecycleScope.launch { viewModel.p2Logs.collect { p2Adapter.submitList(it) } }

        // 勝利監視
        lifecycleScope.launch {
            viewModel.winner.collect { winner ->
                winner?.let { showWinDialog(it) }
            }
        }
    }

    private fun showWinDialog(winner: Player) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("ゲーム終了！")
            .setMessage("勝者：${winner.name}\nおめでとうございます！")
            .setPositiveButton("メニューに戻る") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}