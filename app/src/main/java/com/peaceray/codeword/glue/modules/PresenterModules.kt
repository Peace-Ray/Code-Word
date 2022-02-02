package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.presentation.contracts.CodeGameContract
import com.peaceray.codeword.presentation.contracts.HelloWorldContract
import com.peaceray.codeword.presentation.presenters.CodeGamePresenter
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
abstract class CodeGameModule {
    @Binds
    abstract fun bindCodeGamePresenter(presenter: CodeGamePresenter): CodeGameContract.Presenter
}