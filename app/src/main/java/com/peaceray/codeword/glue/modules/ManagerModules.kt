package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.domain.managers.game.GameSessionManager
import com.peaceray.codeword.domain.managers.game.GameSetupManager
import com.peaceray.codeword.domain.managers.game.impl.GameSessionManagerImpl
import com.peaceray.codeword.domain.managers.game.impl.WordPuzzleGameSetupManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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