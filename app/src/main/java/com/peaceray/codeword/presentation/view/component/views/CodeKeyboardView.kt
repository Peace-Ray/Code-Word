package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import timber.log.Timber

/**
 * Not a real Keyboard; a way to represent code character input buttons. Extends from
 * ConstraintLayout and (hopefully) supports any arbitrary layout as a result. Traverses its
 * inflated structure, and those of any added children, looking for CodeKeyViews which it
 */
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
    }

    // TODO listen for view hierarchy changes and re-traverse
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Keys
    //---------------------------------------------------------------------------------------------
    private val characterKeys = mutableMapOf<Char, CodeKeyView>()

    private fun traverse(parent: View) {
        if (parent == this) {
            characterKeys.clear()
        }

        // Assumption: Keys do not contain each other.
        if (parent is CodeKeyView) {
            parent.setOnClickListener(onKeyClickListener)
            if (parent.type == CodeKeyView.KeyType.CHARACTER) {
                characterKeys[parent.character!!] = parent
            }
        } else if (parent is ViewGroup) {
            parent.children.forEach { traverse(it) }
        }
    }

    fun setCharacterEvaluations(evaluations: Map<Char, CharacterEvaluation>) {
        evaluations.forEach { (c, evaluation) ->
            Timber.v("applying character evaluations for $c: ${evaluation.markup}")
            // TODO apply custom color scheme
            characterKeys[c]?.setBackgroundColor(when(evaluation.markup) {
                Constraint.MarkupType.EXACT -> resources.getColor(R.color.green_500)
                Constraint.MarkupType.INCLUDED -> resources.getColor(R.color.amber_300)
                Constraint.MarkupType.NO -> resources.getColor(R.color.black)
                else -> resources.getColor(R.color.gray_500)
            })

            // TODO animate any change?
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}