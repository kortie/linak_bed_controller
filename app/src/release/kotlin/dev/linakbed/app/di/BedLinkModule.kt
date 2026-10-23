package dev.linakbed.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.domain.port.BedLink

/** 릴리스: 실제 BLE 링크만. 디버그 빌드는 같은 이름의 모듈이 가상 침대 전환을 끼운다. */
@Module
@InstallIn(SingletonComponent::class)
object BedLinkModule {
    @Provides
    fun provideBedLink(link: AndroidBedLink): BedLink = link
}
