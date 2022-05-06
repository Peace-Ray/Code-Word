package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
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
    }

    var onKeyListener: OnKeyListener? = null
    private val onKeyClickListener = object: OnClickListener {
        override fun onClick(view: View?) {
            if (view is CodeKeyView) {
                Timber.v("onClick: ${view.type}")
                when(view.type) {
                    CodeKeyView.KeyType.CHARACTER -> onKeyListener?.onCharacter(view.character!!)
                    CodeKeyView.KeyType.ENTER -> onKeyListener?.onEnter()
                    CodeKeyView.KeyType.DELETE -> onKeyListener?.onDelete()
                }
            } else {
                Timber.v("onClickListener invoked for non-CodeKeyView $view")
            }
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
        val context = context
        if (context is LifecycleOwner) {
            colorSwatchManager.colorSwatchLiveData.observe(context) { swatch ->
                updateKeyColors(swatch)
            }
        }

        Timber.v("onFinishInflate")
        updateKeyColors(colorSwatchManager.colorSwatch)
    }

    // TODO listen for view hierarchy changes and re-traverse
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Operation
    //---------------------------------------------------------------------------------------------
    @Inject
    lateinit var colorSwatchManager: ColorSwatchManager
    private val keys = mutableListOf<CodeKeyView>()
    private var characterEvaluations: Map<Char, CharacterEvaluation> = mapOf()

    private fun traverse(parent: View) {
        if (parent == this) {
            keys.clear()
        }

        // Assumption: Keys do not contain each other.
        if (parent is CodeKeyView) {
            parent.setOnClickListener(onKeyClickListener)
            keys.add(parent)
        } else if (parent is ViewGroup) {
            parent.children.forEach { traverse(it) }
        }
    }

    fun setCharacterEvaluations(evaluations: Map<Char, CharacterEvaluation>) {
        characterEvaluations = evaluations
        updateKeyColors(colorSwatchManager.colorSwatch)
    }

    private fun updateKeyColors(swatch: ColorSwatch) {
        Timber.v("updateKeyColors")
        keys.forEach { view ->
            val markup = characterEvaluations[view.character]?.markup
            if (view.backgroundView == null) {
                view.setBackgroundColor(swatch.evaluation.color(markup))
            } else {
                val drawable = view.backgroundView?.background
                drawable?.colorFilter = PorterDuffColorFilter(
                    swatch.evaluation.color(markup),
                    PorterDuff.Mode.MULTIPLY
                )
                view.backgroundView?.background = drawable
            }
            view.textView?.setTextColor(swatch.evaluation.onColor(markup))
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

}