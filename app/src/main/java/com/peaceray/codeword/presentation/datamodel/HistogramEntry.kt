package com.peaceray.codeword.presentation.datamodel

import com.peaceray.codeword.utils.histogram.IntHistogram


data class HistogramEntry(
    val histogram: IntHistogram,
    val key: Int,
    val span: Span
) {
    enum class Span {
        EQ,
        NEQ,
        LT,
        GT,
        LTE,
        GTE
    };

    constructor(histogram: IntHistogram, key: Int): this(histogram, key, Span.EQ)

    val value by lazy {
        when(span) {
            Span.EQ -> histogram[key]
            Span.NEQ -> histogram.total - histogram[key]
            Span.LT, Span.LTE -> {  // calculate LT sum, then add the entry itself
                val lt = if ((histogram.min ?: key) >= key) 0 else histogram.sum { it.key < key }
                lt + if (span == Span.LT) 0 else histogram[key]
            }
            Span.GT, Span.GTE -> {  // calculate GT sum, then add the entry itself
                val gt = if ((histogram.max ?: key) <= key) 0 else histogram.sum { it.key > key }
                gt + if (span == Span.GT) 0 else histogram[key]
            }
        }
    }

    fun includesKey(key: Int) = when(span) {
        Span.EQ -> key == this.key
        Span.NEQ -> key != this.key
        Span.LT -> key < this.key
        Span.GT -> key > this.key
        Span.LTE -> key <= this.key
        Span.GTE -> key >= this.key
    }

    companion object {
        /**
         * Create and return a list of HistogramEntries in ascending order of size [max] - [min] + 1.
         * If [outOfBounds], also include [Span.LTE] and [Span.GTE] entries on either end IF the
         * histogram value there is nonzero.
         */
        fun entries(histogram: IntHistogram, min: Int, max: Int, outOfBounds: Boolean = false): List<HistogramEntry> {
            val within = (min..max).map { HistogramEntry(histogram, it) }
            return if (!outOfBounds) within else {
                val lb = HistogramEntry(histogram, min - 1, Span.LTE)
                val gb = HistogramEntry(histogram, max + 1, Span.GTE)
                val all = mutableListOf<HistogramEntry>()
                if (lb.value > 0) all.add(lb)
                all.addAll(within)
                if (gb.value > 0) all.add(gb)
                all.toList()
            }
        }
    }
}