package com.peaceray.codeword.glue.modules

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.peaceray.codeword.glue.ForActivity
import com.peaceray.codeword.glue.ForApplication
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
object ApplicationContextModule {
    @Provides
    @Singleton
    @ForApplication
    fun provideApplication(application: Application): Application {
        return application
    }

    @Provides
    @Singleton
    @ForApplication
    fun provideApplicationContext(application: Application): Context {
        return application
    }

    @Provides
    @Singleton
    @ForApplication
    fun provideApplicationResources(application: Application): Resources {
        return application.resources
    }

    @Provides
    @Singleton
    @ForApplication
    fun provideApplicationAssets(application: Application): AssetManager {
        return application.assets
    }
}

@Module
@InstallIn(ActivityComponent::class)
object ActivityContextModule {
    @Provides
    @ForActivity
    fun provideActivity(activity: Activity): Activity {
        return activity
    }

    @Provides
    @ForActivity
    fun provideActivityContext(activity: Activity): Context {
        return activity
    }

    @Provides
    @ForActivity
    fun provideActivityResources(activity: Activity): Resources {
        return activity.resources
    }

    @Provides
    @ForActivity
    fun provideActivityAssets(activity: Activity): AssetManager {
        return activity.assets
    }
}

@InstallIn(SingletonComponent::class)
@Module
abstract class OptionalActivityContextModule {
    @BindsOptionalOf
    @ForActivity
    abstract fun bindOptionalActivityContext(): Context

    @BindsOptionalOf
    @ForActivity
    abstract fun bindOptionalActivityResources(): Resources

    @BindsOptionalOf
    @ForActivity
    abstract fun bindOptionalActivityAssets(): AssetManager
}

@Module
@InstallIn(SingletonComponent::class)
object ContextModule {
    @Provides
    fun provideContext(
        @ForApplication context: Context,
        @ForActivity activityContext: Optional<Context>
    ): Context = activityContext.orElse(context)

    @Provides
    fun provideResources(
        @ForApplication resources: Resources,
        @ForActivity activityResources: Optional<Resources>
    ): Resources = activityResources.orElse(resources)

    @Provides
    fun provideAssets(
        @ForApplication assets: AssetManager,
        @ForActivity activityAssets: Optional<AssetManager>
    ): AssetManager = activityAssets.orElse(assets)
}