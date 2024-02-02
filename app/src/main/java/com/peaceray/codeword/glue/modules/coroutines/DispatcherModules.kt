package com.peaceray.codeword.glue.modules.coroutines

import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.glue.ForLocalIO
import com.peaceray.codeword.glue.ForRemoteIO
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @Singleton
    @ForLocalIO
    fun provideLocalIODispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    @Singleton
    @ForRemoteIO
    fun provideRemoteIODispatcher(): CoroutineDispatcher {
        return Dispatchers.IO
    }

    @Provides
    @Singleton
    @ForComputation
    fun provideComputationDispatcher(): CoroutineDispatcher {
        return Dispatchers.Default
    }
}