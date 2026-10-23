package dev.linakbed.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.SystemClock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideClock(): Clock = SystemClock
}
