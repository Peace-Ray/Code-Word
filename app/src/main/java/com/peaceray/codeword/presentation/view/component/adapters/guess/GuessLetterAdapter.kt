package com.peaceray.codeword.presentation.view.component.adapters.guess

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.layouts.CellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintLineLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout
import com.peaceray.codeword.presentation.view.component.viewholders.AggregatedConstraintViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessAggregatedPipGridViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.EmptyViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessLetterViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedCountsAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedExactAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedIncludedAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterCodeAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterEntryAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterMarkupAppearance
import com.peaceray.codeword.utils.extensions.toLifecycleOwner
import timber.log.Timber
import javax.inject.Inject

/**
 * A [RecyclerView.Adapter] displaying Constraints and a pending guess
 * (as Guess objects, the View DataModel wrapper for this concept). Each item in the RecyclerView
 * represents on [GuessLetter] in the Guess; this Adapter is appropriate for Grid-style layouts.
 *
 * Default behavior is to display the Guess, if any, as the final item on the list.
 */
class GuessLetterAdapter @Inject constructor(
    private val layoutInflater: LayoutInflater,
    private val colorSwatchManager: ColorSwatchManager
): BaseGuessAdapter<RecyclerView.ViewHolder>() {

    //region ViewHolder Styles
    //---------------------------------------------------------------------------------------------
    /**
     * Different item styles create different numbers of items
     */
    enum class ItemCount {
        /**
         * One time per letter in the guess (for partial guesses, padded to fill length
         * of a complete guess)
         */
        LETTER,

        /**
         * One single item per guess, with the same width as a Letter.
         */
        SINGLE,

        /**
         * One single item per guess, which is meant to fill an entire row.
         */
        FULL_ROW;
    }

    /**
     * A style of displaying one Guess/Constraint as a row. Used as ItemTypes when creating
     * ViewHolders.
     */
    enum class ItemStyle(val itemCount: ItemCount = ItemCount.LETTER) {
        /**
         * Display letters of the candidate guess and any per-character markup they've received.
         */
        LETTER_MARKUP,

        /**
         * Display letters of the candidate guess, color-coded based on code index.
         */
        LETTER_CODE,

        /**
         * Display letters of the candidate guess, possibly styled to indicate whether
         * they are a current entry or previous Constraint, but without color-coding
         * or markup.
         */
        LETTER_ENTRY,

        /**
         * Display the aggregated evaluation "pips" w/o any associated candidate characters.
         * Represented as a full row item.
         */
        AGGREGATED_PIP_LINE(itemCount = ItemCount.FULL_ROW),

        /**
         * Display the aggregated evaluation "pips"
         */
        AGGREGATED_PIP_CLUSTER(itemCount = ItemCount.SINGLE),

        /**
         * Display the aggregated evaluation "pips" for Exact matches only
         */
        EXACT_PIP_CLUSTER(itemCount = ItemCount.SINGLE),

        /**
         * Display the aggregated evaluation "pips"
         */
        INCLUDED_PIP_CLUSTER(itemCount = ItemCount.SINGLE),

        /**
         * Display an empty cell at this location.
         */
        EMPTY(itemCount = ItemCount.SINGLE);
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region View configuration
    //---------------------------------------------------------------------------------------------
    private val _cellDefaultLayout: Map<ItemStyle, CellLayout>
    init {
        val resources = layoutInflater.context.resources
        val letterLayout = GuessLetterCellLayout.create(resources)
        _cellDefaultLayout = mapOf(
            Pair(ItemStyle.LETTER_MARKUP, letterLayout),
            Pair(ItemStyle.LETTER_CODE, letterLayout),
            Pair(ItemStyle.LETTER_ENTRY, letterLayout),
            Pair(ItemStyle.AGGREGATED_PIP_LINE, GuessAggregateConstraintLineLayout.create(resources)),
            Pair(ItemStyle.AGGREGATED_PIP_CLUSTER, GuessAggregateConstraintCellLayout.create(resources, 4)),
            Pair(ItemStyle.EXACT_PIP_CLUSTER, GuessAggregateConstraintCellLayout.create(resources, 4)),
            Pair(ItemStyle.INCLUDED_PIP_CLUSTER, GuessAggregateConstraintCellLayout.create(resources, 4)),
            Pair(ItemStyle.EMPTY, letterLayout)
        )
    }

    private val _cellLayout: MutableMap<ItemStyle, CellLayout> = _cellDefaultLayout.toMutableMap()
    val cellLayout: Map<ItemStyle, CellLayout> = _cellLayout

    private val _itemStyles = mutableListOf(ItemStyle.LETTER_MARKUP)
    val itemStyles: List<ItemStyle> = _itemStyles

    private val itemStyleOffset = mutableListOf(0)

    // appearance details
    private val letterStyleAppearance: MutableMap<ItemStyle, GuessLetterAppearance> = mutableMapOf()
    private val aggregatedStyleAppearance: MutableMap<ItemStyle, GuessAggregatedAppearance> = mutableMapOf()
    init {
        val context = layoutInflater.context
        letterStyleAppearance[ItemStyle.LETTER_MARKUP] = GuessLetterMarkupAppearance(context, cellLayout[ItemStyle.LETTER_MARKUP] as GuessLetterCellLayout)
        letterStyleAppearance[ItemStyle.LETTER_CODE] = GuessLetterCodeAppearance(context, cellLayout[ItemStyle.LETTER_CODE] as GuessLetterCellLayout)
        letterStyleAppearance[ItemStyle.LETTER_ENTRY] = GuessLetterEntryAppearance(context, cellLayout[ItemStyle.LETTER_ENTRY] as GuessLetterCellLayout)

        aggregatedStyleAppearance[ItemStyle.AGGREGATED_PIP_CLUSTER] = GuessAggregatedCountsAppearance(context, cellLayout[ItemStyle.AGGREGATED_PIP_CLUSTER] as GuessAggregateConstraintCellLayout)
        aggregatedStyleAppearance[ItemStyle.EXACT_PIP_CLUSTER] = GuessAggregatedExactAppearance(context, cellLayout[ItemStyle.EXACT_PIP_CLUSTER] as GuessAggregateConstraintCellLayout)
        aggregatedStyleAppearance[ItemStyle.INCLUDED_PIP_CLUSTER] = GuessAggregatedIncludedAppearance(context, cellLayout[ItemStyle.INCLUDED_PIP_CLUSTER] as GuessAggregateConstraintCellLayout)
    }

    // game details (cached for recreation of subfields, such as styleAppearance)
    private var _codeCharacters: List<Char> = listOf()

    constructor(
        layoutInflater: LayoutInflater,
        colorSwatchManager: ColorSwatchManager,
        itemStyles: List<ItemStyle>
    ): this(layoutInflater, colorSwatchManager) {
        setItemStyles(itemStyles)
    }



    fun setCellLayout(itemStyle: ItemStyle, cellLayout: CellLayout?) {
        val layout = (cellLayout ?: _cellDefaultLayout[itemStyle])!!

        // verify layout type and (possibly) set styleAppearance
        when (itemStyle) {
            ItemStyle.LETTER_MARKUP -> {
                require(layout is GuessLetterCellLayout)
                letterStyleAppearance[itemStyle] = GuessLetterMarkupAppearance(layoutInflater.context, layout)
            }
            ItemStyle.LETTER_CODE -> {
                require(layout is GuessLetterCellLayout)
                letterStyleAppearance[itemStyle] = GuessLetterCodeAppearance(layoutInflater.context, layout, _codeCharacters)
            }
            ItemStyle.LETTER_ENTRY -> {
                require(layout is GuessLetterCellLayout)
                letterStyleAppearance[itemStyle] = GuessLetterEntryAppearance(layoutInflater.context, layout)
            }
            ItemStyle.EXACT_PIP_CLUSTER -> {
                require(layout is GuessAggregateConstraintCellLayout)
                aggregatedStyleAppearance[itemStyle] = GuessAggregatedExactAppearance(layoutInflater.context, layout)
            }
            ItemStyle.INCLUDED_PIP_CLUSTER -> {
                require(layout is GuessAggregateConstraintCellLayout)
                aggregatedStyleAppearance[itemStyle] = GuessAggregatedIncludedAppearance(layoutInflater.context, layout)
            }
            ItemStyle.AGGREGATED_PIP_CLUSTER -> {
                require(layout is GuessAggregateConstraintCellLayout)
                aggregatedStyleAppearance[itemStyle] = GuessAggregatedCountsAppearance(layoutInflater.context, layout)
            }
            ItemStyle.AGGREGATED_PIP_LINE -> {
                require(layout is GuessAggregateConstraintLineLayout)
            }
            ItemStyle.EMPTY -> {
                // nothing to do here; empty is empty
            }
        }

        // update layout entry
        _cellLayout[itemStyle] = layout
    }

    fun setCellLayouts(cellLayouts: Map<ItemStyle, CellLayout?> = mapOf(), clear: Boolean = false) {
        for (style in ItemStyle.values()) {
            if (clear || style in cellLayouts) {
                setCellLayout(style, cellLayouts[style])
            }
        }
    }

    fun setItemStyles(itemStyles: List<ItemStyle>) {
        _itemStyles.clear()
        _itemStyles.addAll(itemStyles)
        refreshItemStyles()
        notifyDataSetChanged()
    }

    fun setItemStyles(vararg itemStyles: ItemStyle) {
        setItemStyles(itemStyles.toList())
    }

    fun setCodeCharacters(codeCharacters: Iterable<Char>) {
        _codeCharacters = codeCharacters.toList()
        letterStyleAppearance[ItemStyle.LETTER_CODE] = GuessLetterCodeAppearance(
            layoutInflater.context,
            cellLayout[ItemStyle.LETTER_CODE] as GuessLetterCellLayout,
            _codeCharacters)
        notifyDataSetChanged()
    }

    private fun refreshItemStyles() {
        var offset = 0
        itemStyleOffset.clear()
        for (style in _itemStyles) {
            itemStyleOffset.add(offset)
            offset += when (style.itemCount) {
                ItemCount.LETTER -> length
                ItemCount.SINGLE, ItemCount.FULL_ROW -> 1
            }
        }
        itemsPerGameRow = offset
        Timber.d("refreshItemStyles with styles $_itemStyles itemsPerRow $itemsPerGameRow")
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val style = ItemStyle.values()[viewType]
        val layout = _cellLayout[style]!!
        val cellView = layoutInflater.inflate(layout.layoutId, parent, false)
        return when (style) {
            ItemStyle.LETTER_MARKUP,
            ItemStyle.LETTER_CODE,
            ItemStyle.LETTER_ENTRY -> GuessLetterViewHolder(cellView, colorSwatchManager, letterStyleAppearance[style]!!)
            ItemStyle.AGGREGATED_PIP_LINE -> AggregatedConstraintViewHolder(cellView, layoutInflater, colorSwatchManager)
            ItemStyle.AGGREGATED_PIP_CLUSTER,
            ItemStyle.EXACT_PIP_CLUSTER,
            ItemStyle.INCLUDED_PIP_CLUSTER -> GuessAggregatedPipGridViewHolder(cellView, colorSwatchManager, aggregatedStyleAppearance[style]!!)
            ItemStyle.EMPTY -> EmptyViewHolder(cellView)

        }
    }

    override fun onBindGuessToViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        guess: Guess
    ) {
        val (style, positionInStyle) = positionToStylePosition(position)
        when(holder) {
            is GuessLetterViewHolder -> {
                val bindLetter = guess.lettersPadded[positionInStyle]
                holder.appearance = letterStyleAppearance[style]!!
                holder.bind(bindLetter)
            }
            is AggregatedConstraintViewHolder -> holder.bind(guess)
            is GuessAggregatedPipGridViewHolder -> holder.bind(guess)
            is EmptyViewHolder -> holder.bind()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val (style, positionInStyle) = positionToStylePosition(position)
        // Timber.d("getItemViewType for $position: $style, $positionInStyle")
        return style.ordinal
    }

    private fun positionToStylePosition(position: Int): Pair<ItemStyle, Int> {
        val positionInRow = position % itemsPerGameRow
        var index = itemStyleOffset.size - 1
        for (i in 1 until itemStyleOffset.size) {
            if (positionInRow < itemStyleOffset[i]) {
                index = i - 1
            }
        }

        return Pair(_itemStyles[index], positionInRow - itemStyleOffset[index])
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Binding
    //---------------------------------------------------------------------------------------------
    override fun setGameFieldSize(length: Int, rows: Int) {
        super.setGameFieldSize(length, rows)
        refreshItemStyles()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    override var itemsPerGameRow: Int = 0
        private set

    override fun positionSpan(position: Int): Int {
        val (style, positionInStyle) = positionToStylePosition(position)
        return when(style.itemCount) {
            ItemCount.LETTER, ItemCount.SINGLE -> 1
            ItemCount.FULL_ROW -> length
        }
    }

    override fun guessRangeToItemRange(guessStart: Int, guessCount: Int) = Pair(
        guessStart * itemsPerGameRow,
        guessCount * itemsPerGameRow
    )

    override fun guessSliceToItemRange(guessPosition: Int, sliceStart: Int, sliceCount: Int): Pair<Int, Int> {
        val ranges = _itemStyles.mapIndexed { index, style ->
            val startInStyle = when(style.itemCount) {
                ItemCount.LETTER, ItemCount.SINGLE -> sliceStart
                ItemCount.FULL_ROW -> 0
            }
            val countInStyle = if (sliceCount == 0) 0 else when(style.itemCount) {
                ItemCount.FULL_ROW -> 1
                ItemCount.SINGLE -> 1
                else -> sliceCount
            }

            Pair(guessPosition * itemsPerGameRow + itemStyleOffset[index] + startInStyle, countInStyle)
        }
        return ranges.reduce { acc, pair ->
            val start = Math.min(acc.first, pair.first)
            val end = Math.max(acc.first + acc.second - 1, pair.first + pair.second - 1)
            Pair(start, end - start + 1)
        }
    }

    override fun itemRangeToGuessRange(itemStart: Int, itemCount: Int): Pair<Int, Int> {
        val guessStart = itemStart / itemsPerGameRow
        val guessEnd = (itemStart + itemCount - 1) / itemsPerGameRow
        return Pair(guessStart, guessEnd - guessStart + 1)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region ColorSwatch Observation
    //---------------------------------------------------------------------------------------------
    init {
        val lifecycleOwner = layoutInflater.context.toLifecycleOwner()
        if (lifecycleOwner != null) {
            colorSwatchManager.colorSwatchLiveData.observe(lifecycleOwner) { swatch ->
                notifyDataSetChanged()
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}