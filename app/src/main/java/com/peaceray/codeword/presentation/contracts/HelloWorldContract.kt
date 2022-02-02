package com.peaceray.codeword.presentation.contracts

interface HelloWorldContract: BaseContract {
    interface View: BaseContract.View {
        fun setText(text: String)
        fun stackHelloWorld()
        fun stackCodeGame()
    }

    interface Presenter: BaseContract.Presenter<View> {
        fun onStackButtonClicked()
        fun onGameButtonClicked()
    }
}