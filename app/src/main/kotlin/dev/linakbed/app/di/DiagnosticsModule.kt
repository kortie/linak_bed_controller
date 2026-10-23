package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.feature.diagnostics.AndroidSystemChecks
import dev.linakbed.app.feature.diagnostics.LogExporter
import dev.linakbed.app.feature.diagnostics.SystemChecks
import dev.linakbed.app.log.LogDirectory
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.PairingStore

/** 원격 연동 조건 판정과 로그 내보내기 (S07). */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides
    fun provideSystemChecks(
        @ApplicationContext context: Context,
        pairingStore: PairingStore,
    ): SystemChecks = AndroidSystemChecks(context, pairingStore)

    @Provides
    fun provideLogExporter(
        @ApplicationContext context: Context,
        clock: Clock,
    ): LogExporter = LogExporter(context, LogDirectory.resolve(context), clock)
}
