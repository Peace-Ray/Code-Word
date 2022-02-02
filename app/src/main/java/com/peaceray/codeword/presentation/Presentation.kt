package com.peaceray.codeword.presentation

import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.peaceray.codeword.presentation.contracts.BaseContract

/**
 * An association between a View and a Presentation (both parties in a presentation contract).
 * The association automatically handles the [detachView] operation when appropriate, based
 * an the appropriate LifecycleEvent. You should probably not create a Presentation directly;
 * instead, call [attach] from the instance which is the [LifecycleOwner] and [BaseContract.View].
 */
class Presentation<T: BaseContract.View>(
    private val view: T,
    private val viewLifecycle: Lifecycle,
    private val presenter: BaseContract.Presenter<T>
): LifecycleEventObserver {
    init {
        presenter.attachView(view)
        viewLifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            presenter.detachView(view)
            viewLifecycle.removeObserver(this)
        }
    }
}

/**
 * Attach this [BaseContract.View] and [LifecycleOwner] to the provided presenter. The caller's
 * [Lifecycle] will be observed from this point, automatically calling
 * [BaseContract.Presenter.detachView] when the view is destroyed.
 *
 * Intended to be called from one of:
 *   AppCompatActivity.onCreate
 *   Fragment.onViewCreated
 *
 * @receiver An implementation of [BaseContract.View] which is assumed to have an Android [Lifecycle].
 * @param presenter An implementation of [BaseContract.Presenter], or the subinterface matching the receiver.
 */
fun <V: BaseContract.View, P: BaseContract.Presenter<V>> V.attach(presenter: P) {
    when(this) {
        is Fragment -> Presentation(this, viewLifecycleOwner.lifecycle, presenter)
        is LifecycleOwner -> Presentation(this, lifecycle, presenter)
        else -> throw IllegalArgumentException("Must be a LifecycleOwner to attach")
    }
}