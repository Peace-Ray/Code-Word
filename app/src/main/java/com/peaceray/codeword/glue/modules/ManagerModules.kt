package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.domain.manager.game.GameDefaultsManager
import com.peaceray.codeword.domain.manager.game.GameSessionManager
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.domain.manager.game.impl.GameDefaultsManagerImpl
import com.peaceray.codeword.domain.manager.game.impl.GameSessionManagerImpl
import com.peaceray.codeword.domain.manager.game.impl.WordPuzzleGameSetupManager
import com.peaceray.codeword.domain.manager.settings.BotSettingsManager
import com.peaceray.codeword.domain.manager.settings.impl.SettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.manager.color.impl.ColorManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GameSetupManagerModule {
    @Binds
    abstract fun bindWordGameSetupManager(manager: WordPuzzleGameSetupManager): GameSetupManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameSessionManagerModule {
    @Binds
    abstract fun bindGameSessionManager(manager: GameSessionManagerImpl): GameSessionManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameDefaultsManagerModule {
    @Binds
    abstract fun bindGameDefaultsManager(manager: GameDefaultsManagerImpl): GameDefaultsManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsManagerModule {
    @Binds
    abstract fun bindBotSettingsManager(manager: SettingsManager): BotSettingsManager
}

@Module
@InstallIn(ActivityComponent::class)
abstract class ColorManagerModule {
    @Binds
    abstract fun bindColorSwatchManager(manager: ColorManager): ColorSwatchManager

    @Binds
    abstract fun bindColorSettingsManager(manager: ColorManager): ColorSettingsManager
}