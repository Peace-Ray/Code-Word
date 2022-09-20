package com.peaceray.codeword.presentation.view.component.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.HistogramEntry
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.viewholders.HistogramViewHolder
import com.peaceray.codeword.utils.extensions.toLifecycleOwner
import com.peaceray.codeword.utils.histogram.IntHistogram
import javax.inject.Inject

class HistogramAdapter @Inject constructor(
    private val layoutInflater: LayoutInflater,
    private val colorSwatchManager: ColorSwatchManager
): RecyclerView.Adapter<HistogramViewHolder>() {

    //region View configuration
    //---------------------------------------------------------------------------------------------
    @LayoutRes
    var layoutId: Int = R.layout.cell_histogram_row

    constructor(
        layoutInflater: LayoutInflater,
        colorSwatchManager: ColorSwatchManager,
        @LayoutRes layoutId: Int
    ): this(layoutInflater, colorSwatchManager) {
        this.layoutId = layoutId
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistogramViewHolder {
        val cellView = layoutInflater.inflate(layoutId, parent, false)
        return HistogramViewHolder(cellView,  layoutInflater, colorSwatchManager)
    }

    override fun onBindViewHolder(holder: HistogramViewHolder, position: Int) {
        holder.bind(histogramEntries[position], outcome, max)
    }

    override fun getItemCount() = histogramEntries.size
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Model
    //---------------------------------------------------------------------------------------------
    var limit: Int = 20
        set(value) {
            field = value
            if (outcome != null && histogram != null) bind(histogram!!, outcome!!)
        }

    var histogram: IntHistogram? = null
        private set

    var histogramEntries: List<HistogramEntry> = listOf()
        private set

    var outcome: GameOutcome? = null
        private set

    var max: Int = 0
        private set

    fun bind(outcome: GameOutcome) {
        if (histogram != null) {
            bind(histogram!!, outcome)
        } else {
            this.outcome = outcome
        }
    }

    fun bind(histogram: IntHistogram) {
        if (outcome != null) {
            bind(histogram, outcome!!)
        } else {
            this.histogram = histogram
        }
    }

    fun bind(histogram: IntHistogram, outcome: GameOutcome) {
        this.histogram = histogram
        this.outcome = outcome

        // display up to "limit" rows of the histogram, possibly fewer.
        val maximum = Math.min(limit, Math.max(histogram.max ?: 0, outcome.rounds))
        val entries = HistogramEntry.entries(
            histogram,
            1,
            maximum,
            true
        )

        // include an additional OOB entry if necessary
        histogramEntries = if (entries.firstOrNull { it.key == maximum + 1 } != null) entries else {
            val mutEntries = entries.toMutableList()
            mutEntries.add(HistogramEntry(histogram, maximum + 1, HistogramEntry.Span.GTE))
            mutEntries.toList()
        }

        max = if (histogramEntries.isEmpty()) 0 else histogramEntries.maxOf { it.value }

        notifyDataSetChanged()
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