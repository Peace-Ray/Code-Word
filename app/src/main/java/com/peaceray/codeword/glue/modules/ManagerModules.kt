package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.creation.impl.GameCreationManagerImpl
import com.peaceray.codeword.data.manager.game.defaults.GameDefaultsManager
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.game.setup.GameSetupManager
import com.peaceray.codeword.data.manager.game.defaults.impl.GameDefaultsManagerImpl
import com.peaceray.codeword.data.manager.game.persistence.impl.GamePersistenceManagerImpl
import com.peaceray.codeword.data.manager.game.play.GamePlayManager
import com.peaceray.codeword.data.manager.game.play.impl.GamePlayManagerImpl
import com.peaceray.codeword.data.manager.game.setup.impl.setup.BaseGameSetupManager
import com.peaceray.codeword.data.manager.record.GameRecordManager
import com.peaceray.codeword.data.manager.record.impl.GameRecordManagerImpl
import com.peaceray.codeword.data.manager.settings.BotSettingsManager
import com.peaceray.codeword.data.manager.settings.impl.SettingsManager
import com.peaceray.codeword.data.manager.version.VersionsManager
import com.peaceray.codeword.data.manager.version.impl.VersionsManagerImpl
import com.peaceray.codeword.presentation.manager.accessibility.AccessibilityManager
import com.peaceray.codeword.presentation.manager.accessibility.impl.AccessibilityManagerImpl
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.manager.color.impl.ColorManager
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackManager
import com.peaceray.codeword.presentation.manager.feedback.impl.GameFeedbackManagerImpl
import com.peaceray.codeword.presentation.manager.share.ShareManager
import com.peaceray.codeword.presentation.manager.share.impl.ShareManagerImpl
import com.peaceray.codeword.presentation.manager.tutorial.TutorialManager
import com.peaceray.codeword.presentation.manager.tutorial.impl.TutorialManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent

//region Domain Managers
//-------------------------------------------------------------------------------------------------

@Module
@InstallIn(SingletonComponent::class)
abstract class GameManagerModule {
    @Binds
    abstract fun bindGameCreationManager(manager: GameCreationManagerImpl): GameCreationManager

    @Binds
    abstract fun bindGameDefaultsManager(manager: GameDefaultsManagerImpl): GameDefaultsManager

    @Binds
    abstract fun bindGamePersistenceManager(manager: GamePersistenceManagerImpl): GamePersistenceManager

    @Binds
    abstract fun bindGamePlayManager(manager: GamePlayManagerImpl): GamePlayManager

    @Binds
    abstract fun bindGameSetupManager(manager: BaseGameSetupManager): GameSetupManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameRecordManagerModule {
    @Binds
    abstract fun bindGameRecordManager(manager: GameRecordManagerImpl): GameRecordManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsManagerModule {
    @Binds
    abstract fun bindBotSettingsManager(manager: SettingsManager): BotSettingsManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VersionsManagerModule {
    @Binds
    abstract fun bindVersionsManager(manager: VersionsManagerImpl): VersionsManager
}

//-------------------------------------------------------------------------------------------------
//endregion

//region Presentation Managers
//-------------------------------------------------------------------------------------------------

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityManagerModule {
    @Binds
    abstract fun bindAccessibilityManager(manager: AccessibilityManagerImpl): AccessibilityManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ColorManagerModule {
    @Binds
    abstract fun bindColorSwatchManager(manager: ColorManager): ColorSwatchManager

    @Binds
    abstract fun bindColorSettingsManager(manager: ColorManager): ColorSettingsManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameFeedbackManagerModule {
    @Binds
    abstract fun bindGameFeedbackManager(manager: GameFeedbackManagerImpl): GameFeedbackManager
}

@Module
@InstallIn(ActivityComponent::class)
abstract class ShareManagerModule {
    @Binds
    abstract fun bindShareManager(manager: ShareManagerImpl): ShareManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TutorialManagerModule {
    @Binds
    abstract fun bindTutorialManager(manager: TutorialManagerImpl): TutorialManager
}

//-------------------------------------------------------------------------------------------------
//endregion