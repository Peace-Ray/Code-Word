package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessAlphabet
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.utils.extensions.toLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Not a real Keyboard; a way to represent code character input buttons. Extends from
 * ConstraintLayout and (hopefully) supports any arbitrary layout as a result. Traverses its
 * inflated structure, and those of any added children, looking for CodeKeyViews which it
 */
@AndroidEntryPoint
class CodeKeyboardView: ConstraintLayout {

    //region Listeners
    //---------------------------------------------------------------------------------------------
    interface OnKeyListener {
        fun onCharacter(character: Char)
        fun onEnter()
        fun onDelete()
        fun onDeleteFully()
    }

    var onKeyListener: OnKeyListener? = null
    private val onKeyClickListener = object: OnClickListener {
        override fun onClick(view: View?) {
            when {
                view is CodeKeyView && isEnabled -> {
                    Timber.v("onClick: ENABLED ${view.type} (${view.character})")
                    when(view.type) {
                        CodeKeyView.KeyType.CHARACTER, CodeKeyView.KeyType.AVAILABLE -> {
                            val character = view.character
                            if (character != null) onKeyListener?.onCharacter(view.character!!)
                        }
                        CodeKeyView.KeyType.ENTER -> onKeyListener?.onEnter()
                        CodeKeyView.KeyType.DELETE -> onKeyListener?.onDelete()
                        CodeKeyView.KeyType.NONE -> Timber.w("No effect for NONE key")
                    }
                }
                view is CodeKeyView -> Timber.v("onClick: DISABLED ${view.type} (${view.character})")
                else -> Timber.v("onClickListener invoked for non-CodeKeyView $view")
            }
        }
    }

    private val onKeyLongClickListener = object: OnLongClickListener {
        override fun onLongClick(view: View?): Boolean {
            when {
                view is CodeKeyView && isEnabled -> {
                    Timber.v("onLongClick: ENABLED ${view.type} (${view.character})")
                    if (view.type == CodeKeyView.KeyType.DELETE) {
                        onKeyListener?.onDeleteFully()
                        return true
                    }
                }
                view is CodeKeyView -> Timber.v("onLongClick: DISABLED ${view.type} (${view.character})")
                else -> Timber.v("onLongClickListener invoked for non-CodeKeyView $view")
            }
            return false
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Construction / Inflation
    //---------------------------------------------------------------------------------------------
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ): super(context, attrs, defStyleAttr)

    override fun onFinishInflate() {
        super.onFinishInflate()

        // Traverse the hierarchy looking for Keys.
        traverse(this)

        // Set up automatic color changes
        val lifecycleOwner = context.toLifecycleOwner()
        if (lifecycleOwner != null) {
            colorSwatchManager.colorSwatchLiveData.observe(lifecycleOwner) { swatch ->
                updateKeyColors(keyStyle, swatch)
            }
        }

        Timber.v("onFinishInflate")
        updateKeyColors(keyStyle, colorSwatchManager.colorSwatch)
    }

    // TODO listen for view hierarchy changes and re-traverse
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Operation
    //---------------------------------------------------------------------------------------------
    enum class KeyStyle {
        MARKUP,
        CODE
    }

    @Inject
    lateinit var colorSwatchManager: ColorSwatchManager
    private val keys = mutableListOf<CodeKeyView>()
    private var guessAlphabet: GuessAlphabet = GuessAlphabet(emptyMap())
    private var codeCharacters: List<Char> = listOf()

    private var _keyStyle: KeyStyle = KeyStyle.MARKUP
    var keyStyle: KeyStyle
        get() = _keyStyle
        set(value) {
            if (_keyStyle != value) {
                _keyStyle = value
                updateKeyAvailability(value, codeCharacters)
                updateKeyColors(value, colorSwatchManager.colorSwatch)
            }
        }

    private fun traverse(parent: View) {
        if (parent == this) {
            keys.clear()
        }

        // Assumption: Keys do not contain each other.
        if (parent is CodeKeyView) {
            parent.setOnClickListener(onKeyClickListener)
            parent.setOnLongClickListener(onKeyLongClickListener)
            keys.add(parent)
        } else if (parent is ViewGroup) {
            parent.children.forEach { traverse(it) }
        }
    }

    fun setCodeCharacters(characters: Iterable<Char>) {
        codeCharacters = characters.toList()
        updateKeyAvailability(keyStyle, codeCharacters)
        updateKeyColors(keyStyle, colorSwatchManager.colorSwatch)
    }

    fun setGuessAlphabet(guessAlphabet: GuessAlphabet) {
        this.guessAlphabet = guessAlphabet
        updateKeyColors(keyStyle, colorSwatchManager.colorSwatch)
    }

    private fun updateKeyAvailability(keyStyle: KeyStyle, characters: List<Char>) {
        // find keys needed for character display
        val explicitChars = keys.filter { it.type == CodeKeyView.KeyType.CHARACTER }
            .map { it.character }
        val availableKeys = keys.filter { it.type == CodeKeyView.KeyType.AVAILABLE }
        val missingChars = characters.filter { it !in explicitChars }

        if (availableKeys.isNotEmpty()) {
            // we prefer equally-sized groups if possible, eliminating the last N views of each
            // group to get the needed number of keys and no more. Sort all keys in the order to be
            // kept.
            val availableByGroup = availableKeys.groupBy { it.group }
            val keysInKeepOrder = mutableListOf<CodeKeyView>()
            val groupIds = availableByGroup.keys.toList().sorted().toMutableList()
            var groupIndex = 0
            while (groupIds.isNotEmpty()) {
                groupIds.removeAll { availableByGroup[it]!!.size <= groupIndex }
                groupIds.forEach { keysInKeepOrder.add(availableByGroup[it]!![groupIndex]) }
                groupIndex++
            }

            val keepKeys = keysInKeepOrder.subList(0, missingChars.size)
            val hideKeys = keysInKeepOrder.subList(missingChars.size, keysInKeepOrder.size)
            val assignKeys = availableByGroup.keys.toList()
                .sorted()
                .flatMap { groupId -> keepKeys.filter { it.group == groupId } }

            // assign characters to views ('null' meaning no character assigned)
            hideKeys.forEach { it.setCharacter(null) }
            assignKeys.forEachIndexed { index, codeKeyView ->
                codeKeyView.setCharacter(missingChars[index])
            }

            if (missingChars.size > availableKeys.size) {
                Timber.w("Requires ${missingChars.size - assignKeys.size} additional keys available to hold character set")
            }
        } else if (missingChars.isNotEmpty()) {
            Timber.w("Requires ${missingChars} extra keys but none are available")
        }

        keys.forEach { view ->
            val show = when (view.type) {
                CodeKeyView.KeyType.CHARACTER, CodeKeyView.KeyType.AVAILABLE -> view.character in characters
                CodeKeyView.KeyType.ENTER, CodeKeyView.KeyType.DELETE -> true
                CodeKeyView.KeyType.NONE -> false
            }
            view.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun updateKeyColors(keyStyle: KeyStyle, swatch: ColorSwatch) {
        Timber.v("updateKeyColors")

        keys.forEach { view ->
            @ColorInt val onColor: Int
            @ColorInt val color: Int
            @ColorInt val colorPop: Int = swatch.container.onBackgroundAccent

            val markup = guessAlphabet.characters[view.character]?.markup ?: GuessMarkup.EMPTY
            val index = codeCharacters.indexOf(view.character)
            if (markup == GuessMarkup.EMPTY && keyStyle == KeyStyle.CODE && index >= 0) {
                onColor = swatch.code.onColor(index)
                color = swatch.code.color(index)
            } else {
                onColor = swatch.evaluation.onColor(markup)
                color = swatch.evaluation.color(markup)
            }

            // background color
            val backgroundView = view.backgroundView
            if (backgroundView is MaterialCardView) {
                backgroundView.setCardBackgroundColor(color)
                backgroundView.strokeColor = colorPop
            } else if (backgroundView != null) {
                val drawable = backgroundView.background
                drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
                backgroundView.background = drawable
            } else {
                view.setBackgroundColor(color)
            }

            // text color
            view.textView?.setTextColor(onColor)

            // image color
            view.imageView?.setColorFilter(onColor, PorterDuff.Mode.MULTIPLY)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Property Overrides
    //---------------------------------------------------------------------------------------------
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        keys.forEach { it.isEnabled = enabled }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Processing Key Events
    //---------------------------------------------------------------------------------------------
    fun onKeyPress(keyCode: Int): Boolean {
        // only process this keyCode if it corresponds to a valid key in the characters
        if (onKeyListener == null) return false
        return when(keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                onKeyListener?.onEnter()
                true
            }
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_DEL -> {
                onKeyListener?.onDelete()
                true
            }
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val character = (keyCode - KeyEvent.KEYCODE_A + 'a'.code).toChar()
                val characters = listOf(character.lowercaseChar(), character.uppercaseChar())
                val match = characters.firstOrNull { it in codeCharacters }
                if (match == null) false else {
                    onKeyListener?.onCharacter(match)
                    true
                }
            }
            else -> false
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}