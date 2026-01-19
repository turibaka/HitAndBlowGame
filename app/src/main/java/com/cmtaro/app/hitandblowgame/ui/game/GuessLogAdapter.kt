package com.cmtaro.app.hitandblowgame.ui.game

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cmtaro.app.hitandblowgame.databinding.ItemGuessLogBinding
import com.cmtaro.app.hitandblowgame.domain.model.Guess

class GuessLogAdapter : RecyclerView.Adapter<GuessLogAdapter.ViewHolder>() {

    private var logs: List<Guess> = emptyList()

    // データを更新するメソッド
    fun submitList(newList: List<Guess>) {
        logs = newList
        notifyDataSetChanged() // 本来はDiffUtilが推奨されますが、学習用にはこれでOK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGuessLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val guess = logs[position]
        holder.binding.textNumber.text = guess.number
        holder.binding.textResult.text = "${guess.hit}H ${guess.blow}B"
    }

    override fun getItemCount(): Int = logs.size

    class ViewHolder(val binding: ItemGuessLogBinding) : RecyclerView.ViewHolder(binding.root)
}