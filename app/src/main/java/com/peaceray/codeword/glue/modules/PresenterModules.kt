package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.contracts.HelloWorldContract
import com.peaceray.codeword.presentation.presenters.GamePresenter
import com.peaceray.codeword.presentation.presenters.GameSetupPresenter
import com.peaceray.codeword.presentation.presenters.HelloWorldPresenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class HelloWorldModule {
    @Binds
    abstract fun bindHelloWorldPresenter(presenter: HelloWorldPresenter): HelloWorldContract.Presenter
}

@Module
@InstallIn(FragmentComponent::class)
abstract class GameModule {
    @Binds
    abstract fun bindGamePresenter(presenter: GamePresenter): GameContract.Presenter
}

@Module
@InstallIn(FragmentComponent::class)
abstract class GameSetupModule {
    @Binds
    abstract fun bindGameSetupPresenter(presenter: GameSetupPresenter): GameSetupContract.Presenter
}