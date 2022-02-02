package com.peaceray.codeword.glue.modules

import android.app.Activity
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.peaceray.codeword.glue.ForActivity
import com.peaceray.codeword.glue.ForFragment
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent
import java.util.*

@Module
@InstallIn(ActivityComponent::class)
object ActivityLayoutInflaterModule {
    @Provides
    @ForActivity
    fun provideLayoutInflater(activity: Activity): LayoutInflater {
        return activity.layoutInflater
    }
}

@Module
@InstallIn(FragmentComponent::class)
object FragmentLayoutInflaterModule {
    @Provides
    @ForFragment
    fun provideLayoutInflater(fragment: Fragment): LayoutInflater {
        return fragment.layoutInflater
    }
}

@InstallIn(ActivityComponent::class)
@Module
abstract class OptionalFragmentLayoutInflaterModule {
    @BindsOptionalOf
    @ForFragment
    abstract fun bindOptionalFragmentLayoutInflaterModule(): LayoutInflater
}

@Module
@InstallIn(ActivityComponent::class)
object LayoutInflaterModule {
    @Provides
    fun provideLayoutInflater(
        @ForActivity inflater: LayoutInflater,
        @ForFragment fragmentLayoutInflater: Optional<LayoutInflater>
    ): LayoutInflater = fragmentLayoutInflater.orElse(inflater)
}