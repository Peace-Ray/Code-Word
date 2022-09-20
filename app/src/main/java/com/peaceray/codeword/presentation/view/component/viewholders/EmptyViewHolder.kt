package com.peaceray.codeword.presentation.view.component.viewholders

import android.view.View
import androidx.recyclerview.widget.RecyclerView

class EmptyViewHolder(
    itemView: View
): RecyclerView.ViewHolder(itemView) {

    init {
        itemView.visibility = View.INVISIBLE
    }

    fun bind() {
        itemView.visibility = View.INVISIBLE
    }

}