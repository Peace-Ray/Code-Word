package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.domain.manager.genie.GenieGameSetupSettingsManager
import com.peaceray.codeword.domain.manager.genie.GenieSettingsManager
import com.peaceray.codeword.domain.manager.genie.impl.GenieSettingsManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

//region Domain Managers
//-------------------------------------------------------------------------------------------------

@Module
@InstallIn(SingletonComponent::class)
abstract class GenieSettingsManagerModule {
    @Binds
    abstract fun bindGenieSettingsManager(manager: GenieSettingsManagerImpl): GenieSettingsManager
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GenieGameSetupSettingsManagerModule {
    @Binds
    abstract fun bindGenieGameSetupSettingsManager(manager: GenieSettingsManagerImpl): GenieGameSetupSettingsManager
}

//-------------------------------------------------------------------------------------------------
//endregion
