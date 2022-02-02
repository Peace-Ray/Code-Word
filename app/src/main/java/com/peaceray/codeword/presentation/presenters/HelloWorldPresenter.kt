package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.presentation.contracts.HelloWorldContract
import timber.log.Timber
import javax.inject.Inject

class HelloWorldPresenter @Inject constructor(): HelloWorldContract.Presenter, BasePresenter<HelloWorldContract.View>() {
    companion object Counter {
        var instances = 1
    }

    private val instance = instances++

    override fun onAttached() {
        view?.setText("Hello World $instance")

        Timber.d("Instance ${instance} onAttached")
    }

    override fun onStackButtonClicked() {
        view?.stackHelloWorld()

        Timber.d("Instance ${instance} onStackButtonClicked")
    }

    override fun onGameButtonClicked() {
        view?.stackCodeGame()

        Timber.d("Instance ${instance} onGameButtonClicked")
    }
}