package com.peaceray.codeword.glue.modules

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.peaceray.codeword.glue.ForActivity
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.glue.utils.optional.OptionalUtils
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import java.util.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationSystemServiceModule {
    @Provides
    @Singleton
    @ForApplication
    fun provideClipboardManager(application: Application): ClipboardManager {
        return application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}

@Module
@InstallIn(ActivityComponent::class)
object ActivitySystemServiceModule {
    @Provides
    @ForActivity
    fun provideClipboardManager(activity: Activity): ClipboardManager {
        return activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class OptionalActivitySystemServiceModule {
    @BindsOptionalOf
    @ForActivity
    abstract fun bindOptionalActivityClipboardManager(): ClipboardManager
}

@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {
    @Provides
    fun provideClipboardManager(
        @ForApplication clipboardManager: ClipboardManager,
        @ForActivity activityClipboardManager: Optional<ClipboardManager>
    ): ClipboardManager = OptionalUtils.orElse(activityClipboardManager, clipboardManager)
}