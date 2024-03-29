package com.peaceray.codeword.glue.modules

import com.peaceray.codeword.BuildConfig
import com.peaceray.codeword.data.source.CodeWordApi
import com.peaceray.codeword.data.source.CodeWordDb
import com.peaceray.codeword.data.source.impl.CodeWordDbImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CodeWordApiModule {
    @Provides
    fun provideApiBaseUrl() = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideOkHttpClient() = if (BuildConfig.DEBUG) {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    } else {
        OkHttpClient.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, apiBaseUrl: String): Retrofit {
        return Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(apiBaseUrl)
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideCodeWordApi(retrofit: Retrofit) = retrofit.create(CodeWordApi::class.java)

}

@Module
@InstallIn(SingletonComponent::class)
abstract class CodeWordDbModule {
    @Binds
    abstract fun bindCodeWordDb(db: CodeWordDbImpl): CodeWordDb
}