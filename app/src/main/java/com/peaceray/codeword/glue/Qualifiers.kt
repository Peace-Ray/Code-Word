package com.peaceray.codeword.glue

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForApplication

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForActivity

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForFragment

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForLocalIO

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForRemoteIO

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForComputation
