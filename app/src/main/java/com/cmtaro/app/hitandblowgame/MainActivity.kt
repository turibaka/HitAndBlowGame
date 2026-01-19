package com.cmtaro.app.hitandblowgame

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cmtaro.app.hitandblowgame.databinding.ActivityMenuBinding // 名前が自動で変わります
import com.cmtaro.app.hitandblowgame.ui.game.GameActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3桁ボタン
        binding.buttonStart3Digit.setOnClickListener {
            startGame(3)
        }

        // 4桁ボタン
        binding.buttonStart4Digit.setOnClickListener {
            startGame(4)
        }
    }

    private fun startGame(digit: Int) {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("DIGIT_COUNT", digit) // 桁数を渡す
        startActivity(intent)
    }
}