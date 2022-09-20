package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.presentation.contracts.BaseContract
import timber.log.Timber

open class BasePresenter<T: BaseContract.View>: BaseContract.Presenter<T> {
    protected var view: T? = null
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
        onAttached()
    }

    final override fun detachView(view: T) {
        Timber.v("detachView $view")
        if (this.view == view) {
            onDetached()
            this.view = null
        } else {
            Timber.w("detachView called for unattached $view")
        }
    }
}