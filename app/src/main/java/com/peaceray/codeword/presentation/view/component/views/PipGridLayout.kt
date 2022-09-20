package com.peaceray.codeword.presentation.view.component.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.annotation.LayoutRes
import androidx.core.content.withStyledAttributes
import com.peaceray.codeword.R
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * A self-styling GridLayout that inflates content views based on intended size. The item layouts
 * themselves are specified externally and left unexamined and styled by this container.
 */
@AndroidEntryPoint
class PipGridLayout: GridLayout {

    //region Construction / Inflation
    //---------------------------------------------------------------------------------------------
    constructor(context: Context): super(context) {
        initialize(context, null, 0)
    }
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        initialize(context, attrs, 0)
    }
    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ): super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr)
    }

    private fun initialize(context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int) {
        context.withStyledAttributes(attrs, R.styleable.PipGrid) {
            pipLayoutFor2x2 = getResourceId(R.styleable.PipGrid_pipLayoutFor2x2, pipLayoutFor2x2)
            pipLayoutFor3x3 = getResourceId(R.styleable.PipGrid_pipLayoutFor3x3, pipLayoutFor3x3)
            pipLayoutFor4x4 = getResourceId(R.styleable.PipGrid_pipLayoutFor4x4, pipLayoutFor4x4)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Pip Properties
    //---------------------------------------------------------------------------------------------
    @LayoutRes var pipLayoutFor2x2: Int = R.layout.cell_pip
        set(value) {
            if (field != value) {
                field = value
                refreshChildren()
            }

        }
    @LayoutRes var pipLayoutFor3x3: Int = R.layout.cell_pip_medium
        set(value) {
            if (field != value) {
                field = value
                refreshChildren()
            }
        }
    @LayoutRes var pipLayoutFor4x4: Int = R.layout.cell_pip_small
        set(value) {
            if (field != value) {
                field = value
                refreshChildren()
            }
        }

    var pipCount: Int = 0
        set(value) {
            if (field != value) {
                field = value
                pipsVisible = value
                refreshChildren()
            }
        }

    private var pipsVisible: Int = -1
    fun setPipsVisibility(visibleCount: Int) {
        pipsVisible = visibleCount
        refreshChildrenVisibility()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Child View Inflation
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var inflater: LayoutInflater

    private var inflatedPipLayoutAndSpan: Pair<Int, Int>? = null

    private fun refreshChildren(force: Boolean = false) {
        val layoutAndSpan = getPipLayoutAndSpan()
        val reinflate = force || layoutAndSpan != inflatedPipLayoutAndSpan

        Timber.d("refreshChildren with pipCount $pipCount layout and span $layoutAndSpan reinflate $reinflate")

        if (reinflate) {
            removeAllViews()
            if (layoutAndSpan != null && layoutAndSpan.first != 0) {
                rowCount = layoutAndSpan.second
                columnCount = layoutAndSpan.second
                for (i in 1..pipCount) {
                    Timber.d("inflating ${layoutAndSpan.first}")
                    inflater.inflate(layoutAndSpan.first, this, true)
                }
            }
            inflatedPipLayoutAndSpan = layoutAndSpan
        }

        refreshChildrenVisibility()
    }

    private fun getPipLayoutAndSpan(): Pair<Int, Int>? = when {
        pipCount <= 0 -> null
        pipCount <= 4 -> Pair(pipLayoutFor2x2, 2)
        pipCount <= 9 -> Pair(pipLayoutFor3x3, 3)
        pipCount <= 16 -> Pair(pipLayoutFor4x4, 4)
        else -> null
    }

    private fun refreshChildrenVisibility() {
        val visibleCount = if (pipsVisible < 0) pipCount else Math.min(pipCount, pipsVisible)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.visibility = if (i < visibleCount) View.VISIBLE else View.GONE
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}