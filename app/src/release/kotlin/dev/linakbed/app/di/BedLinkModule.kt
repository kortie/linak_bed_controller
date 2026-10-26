package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.domain.log.BedLogger
import kotlinx.coroutines.CoroutineScope

/** 릴리스: 침대마다 실제 BLE 링크. 디버그 빌드는 `SimBedModule`이 가상 침대를 끼운다. */
@Module
@InstallIn(SingletonComponent::class)
object BedLinkModule {
    @Provides
    fun provideBedLinkFactory(
        @ApplicationContext context: Context,
        logger: BedLogger,
        @ApplicationScope scope: CoroutineScope,
    ): BedLinkFactory = BedLinkFactory { newAndroidBedLink(context, logger, scope) }
}
