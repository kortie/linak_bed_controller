package dev.linakbed.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.feature.about.PlayTipBilling
import dev.linakbed.app.feature.about.TipBilling

/** 커피 한 잔 후원의 Google Play 결제 (ADR-014). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    abstract fun bindTipBilling(impl: PlayTipBilling): TipBilling
}
