package com.peaceray.codeword.presentation.view.fragments.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.presentation.view.fragments.GameInfoFragment
import timber.log.Timber

abstract class CodeWordDialogFragment(@LayoutRes layoutId: Int): DialogFragment(layoutId) {
    constructor(): this(0)

    interface OnLifecycleListener {
        fun onCancel(dialogFragment: CodeWordDialogFragment)
        fun onDismiss(dialogFragment: CodeWordDialogFragment)
    }

    private var listener: OnLifecycleListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is OnLifecycleListener -> parentFragment as OnLifecycleListener
            context is OnLifecycleListener -> context
            else -> {
                Timber.w("CodeWordDialogFragment attached, but has no OnLifecycleListener")
                null
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCancel(dialog: DialogInterface) {
        listener?.onCancel(this)
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        listener?.onDismiss(this)
        super.onDismiss(dialog)
    }
}
