package com.peaceray.codeword.domain.manager.genie.impl

import com.peaceray.codeword.domain.manager.genie.GenieGameSetupSettingsManager
import com.peaceray.codeword.domain.manager.genie.GenieSettingsManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenieSettingsManagerImpl @Inject constructor():
        GenieSettingsManager,
        GenieGameSetupSettingsManager
{

    //region GenieSettingsManager
    //---------------------------------------------------------------------------------------------

    override var developerMode: Boolean = false

    //---------------------------------------------------------------------------------------------
    //endregion

    //region GenieGameSetupSettingsManager
    //---------------------------------------------------------------------------------------------
    override val allowCustomSecret: Boolean
        get() = developerMode
    override val allowCustomVersionCheck: Boolean
        get() = developerMode
    //---------------------------------------------------------------------------------------------
    //endregion

}