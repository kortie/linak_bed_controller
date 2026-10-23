package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.pairing.AndroidCompanionPairing
import dev.linakbed.app.pairing.CompanionPairing
import dev.linakbed.app.pairing.IdGenerator

@Module
@InstallIn(SingletonComponent::class)
object PairingModule {
    @Provides
    fun provideCompanionPairing(
        @ApplicationContext context: Context,
    ): CompanionPairing = AndroidCompanionPairing(context)

    @Provides
    fun provideIdGenerator(): IdGenerator = IdGenerator.RANDOM_UUID
}
