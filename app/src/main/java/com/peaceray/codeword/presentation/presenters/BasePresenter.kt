package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.presentation.contracts.BaseContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import timber.log.Timber

open class BasePresenter<T: BaseContract.View>: BaseContract.Presenter<T> {
    protected var view: T? = null
        private set

    /**
     * A CoroutineScope useful for launching suspend functions that should be canceled
     * when the view is detached. Will be active (not canceled) up until the completion
     * of a child class's [onDetached] function.
     */
    protected lateinit var viewScope: CoroutineScope
        private set

    open fun onAttached() {
        Timber.v("onAttached")
    }

    open fun onDetached() {
        Timber.v("onDetached")
    }

    final override fun attachView(view: T) {
        Timber.v("attachView $view")
        if (this.view != null) {
            Timber.w("attachView called for $view when ${this.view} already attached")
        }

        this.view = view
        this.viewScope = CoroutineScope(Dispatchers.Main)
        onAttached()
    }

    final override fun detachView(view: T) {
        Timber.v("detachView $view")
        if (this.view == view) {
            onDetached()
            this.view = null
            this.viewScope.cancel("View Detached")
        } else {
            Timber.w("detachView called for unattached $view")
        }
    }
}