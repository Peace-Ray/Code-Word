package com.peaceray.codeword.presentation.contracts

interface BaseContract {
    // TODO make "Model" explicit? Can't see the merit of an abstraction layer
    // between the Presenter and the domain managers / model data it interacts with.

    interface View {

    }

    interface Presenter<T: View> {
        fun attachView(view: T)
        fun detachView(view: T)
    }
}