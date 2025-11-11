package com.example.kreyolkeyboard.wordsearch

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

/**
 * Adaptateur pour la grille de mots mêlés
 */
class WordSearchGridAdapter(
    private val context: Context,
    private val puzzle: WordSearchPuzzle
) : BaseAdapter() {
    
    private val selectedCells = mutableSetOf<Int>()
    private val foundCells = mutableSetOf<Int>()
    private var isSelecting = false
    private var selectionStart = -1
    private var onWordFoundListener: ((String) -> Unit)? = null
    
    fun setOnWordFoundListener(listener: (String) -> Unit) {
        onWordFoundListener = listener
    }
    
    override fun getCount(): Int = puzzle.gridSize * puzzle.gridSize
    
    override fun getItem(position: Int): Any = getCharAt(position)
    
    override fun getItemId(position: Int): Long = position.toLong()
    
    private fun getCharAt(position: Int): Char {
        val row = position / puzzle.gridSize
        val col = position % puzzle.gridSize
        return puzzle.grid[row][col]
    }
    
    private fun getRowCol(position: Int): Pair<Int, Int> {
        return Pair(position / puzzle.gridSize, position % puzzle.gridSize)
    }
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val textView = convertView as? TextView ?: TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(80, 80)
            gravity = Gravity.CENTER
            textSize = 18f
            setBackgroundResource(android.R.drawable.btn_default)
            setPadding(4, 4, 4, 4)
        }
        
        // Afficher la lettre
        textView.text = getCharAt(position).toString()
        
        // Appliquer les couleurs selon l'état
        when {
            foundCells.contains(position) -> {
                // Mot déjà trouvé - vert
                textView.setBackgroundColor(Color.parseColor("#4CAF50"))
                textView.setTextColor(Color.WHITE)
            }
            selectedCells.contains(position) -> {
                // Sélection en cours - bleu
                textView.setBackgroundColor(Color.parseColor("#2196F3"))
                textView.setTextColor(Color.WHITE)
            }
            else -> {
                // Normal - gris clair
                textView.setBackgroundColor(Color.parseColor("#EEEEEE"))
                textView.setTextColor(Color.BLACK)
            }
        }
        
        // Gérer les interactions tactiles
        textView.setOnTouchListener { _, event ->
            handleTouch(position, event)
            true
        }
        
        return textView
    }
    
    private fun handleTouch(position: Int, event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startSelection(position)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    updateSelection(position)
                }
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    endSelection(position)
                }
                return true
            }
        }
        return false
    }
    
    private fun startSelection(position: Int) {
        if (foundCells.contains(position)) return
        
        isSelecting = true
        selectionStart = position
        selectedCells.clear()
        selectedCells.add(position)
        notifyDataSetChanged()
    }
    
    private fun updateSelection(position: Int) {
        if (!isSelecting || selectionStart == -1) return
        
        // Calculer la ligne droite entre le début et la position actuelle
        val cellsInLine = getCellsInLine(selectionStart, position)
        
        selectedCells.clear()
        selectedCells.addAll(cellsInLine.filter { !foundCells.contains(it) })
        notifyDataSetChanged()
    }
    
    private fun endSelection(position: Int) {
        if (!isSelecting) return
        
        isSelecting = false
        
        // Vérifier si la sélection forme un mot valide
        val selectedWord = getSelectedWord()
        if (isValidWord(selectedWord)) {
            // Mot trouvé !
            foundCells.addAll(selectedCells)
            onWordFoundListener?.invoke(selectedWord)
        }
        
        selectedCells.clear()
        notifyDataSetChanged()
    }
    
    private fun getCellsInLine(start: Int, end: Int): List<Int> {
        val startPos = getRowCol(start)
        val endPos = getRowCol(end)
        
        val deltaRow = when {
            endPos.first > startPos.first -> 1
            endPos.first < startPos.first -> -1
            else -> 0
        }
        
        val deltaCol = when {
            endPos.second > startPos.second -> 1
            endPos.second < startPos.second -> -1
            else -> 0
        }
        
        // Vérifier que c'est une ligne droite valide
        if (deltaRow != 0 && deltaCol != 0 && 
            kotlin.math.abs(endPos.first - startPos.first) != kotlin.math.abs(endPos.second - startPos.second)) {
            return listOf(start) // Pas une diagonale valide
        }
        
        val cells = mutableListOf<Int>()
        var currentRow = startPos.first
        var currentCol = startPos.second
        
        while (true) {
            val position = currentRow * puzzle.gridSize + currentCol
            cells.add(position)
            
            if (currentRow == endPos.first && currentCol == endPos.second) break
            
            currentRow += deltaRow
            currentCol += deltaCol
            
            // Sécurité pour éviter les boucles infinies
            if (currentRow < 0 || currentRow >= puzzle.gridSize || 
                currentCol < 0 || currentCol >= puzzle.gridSize) {
                break
            }
        }
        
        return cells
    }
    
    private fun getSelectedWord(): String {
        return selectedCells.sorted().map { getCharAt(it) }.joinToString("")
    }
    
    private fun isValidWord(word: String): Boolean {
        // Vérifier si le mot (ou son inverse) est dans la liste des mots à trouver
        val normalizedWord = word.uppercase()
        val reversedWord = normalizedWord.reversed()
        
        return puzzle.words.any { puzzleWord ->
            val puzzleWordUpper = puzzleWord.word.uppercase()
            puzzleWordUpper == normalizedWord || puzzleWordUpper == reversedWord
        }
    }
}