package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.presentation.contracts.FeatureAvailabilityContract
import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.contracts.GameOutcomeContract
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.presenters.FeatureAvailabilityPresenter
import com.peaceray.codeword.presentation.presenters.GameOutcomePresenter
import com.peaceray.codeword.presentation.presenters.GamePresenter
import com.peaceray.codeword.presentation.presenters.GameSetupPresenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent

@Module
@InstallIn(ActivityComponent::class)
abstract class FeatureAvailabilityModule {
    @Binds
    abstract fun bindFeatureAvailabilityPresenter(presenter: FeatureAvailabilityPresenter): FeatureAvailabilityContract.Presenter
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

@Module
@InstallIn(FragmentComponent::class)
abstract class GameOutcomePresenter {
    @Binds
    abstract fun bindGameOutcomePresenter(presenter: GameOutcomePresenter): GameOutcomeContract.Presenter
}