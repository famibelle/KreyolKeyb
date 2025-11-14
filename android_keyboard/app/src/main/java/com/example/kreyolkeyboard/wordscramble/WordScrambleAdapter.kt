package com.example.kreyolkeyboard.wordscramble

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Adaptateur pour afficher les lettres mélangées
 */
class ScrambledLettersAdapter(
    private val context: Context,
    private var letters: List<Char>
) : BaseAdapter() {
    
    private val selectedPositions = mutableSetOf<Int>()
    
    override fun getCount(): Int = letters.size
    
    override fun getItem(position: Int): Char = letters[position]
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val frameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(120, 120)
            setPadding(8, 8, 8, 8)
        }
        
        val tvLetter = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            text = letters[position].toString().uppercase()
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        
        // Griser les lettres déjà utilisées
        if (selectedPositions.contains(position)) {
            tvLetter.alpha = 0.3f
            tvLetter.setBackgroundColor(Color.parseColor("#BDBDBD"))
        } else {
            tvLetter.alpha = 1.0f
            tvLetter.setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        
        frameLayout.addView(tvLetter)
        return frameLayout
    }
    
    fun markAsSelected(position: Int) {
        selectedPositions.add(position)
        notifyDataSetChanged()
    }
    
    fun clearSelections() {
        selectedPositions.clear()
        notifyDataSetChanged()
    }
    
    fun updateLetters(newLetters: List<Char>) {
        letters = newLetters
        selectedPositions.clear()
        notifyDataSetChanged()
    }
}

/**
 * Adaptateur pour afficher la réponse en construction
 */
class AnswerLettersAdapter(
    private val context: Context,
    private var letters: List<Char?>
) : BaseAdapter() {
    
    override fun getCount(): Int = letters.size
    
    override fun getItem(position: Int): Char? = letters[position]
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val frameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(120, 120)
            setPadding(8, 8, 8, 8)
        }
        
        val tvLetter = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        
        val letter = letters[position]
        if (letter != null) {
            tvLetter.text = letter.toString().uppercase()
            tvLetter.setBackgroundColor(Color.parseColor("#2196F3"))
            tvLetter.setTextColor(Color.WHITE)
        } else {
            tvLetter.text = "_"
            tvLetter.setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        
        frameLayout.addView(tvLetter)
        return frameLayout
    }
    
    fun updateLetters(newLetters: List<Char?>) {
        letters = newLetters
        notifyDataSetChanged()
    }
}
