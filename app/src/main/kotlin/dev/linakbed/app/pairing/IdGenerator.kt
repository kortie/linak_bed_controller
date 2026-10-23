package dev.linakbed.app.pairing

import java.util.UUID

/** 새 bedId 생성 (테스트에서 고정값으로 바꾼다). */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val RANDOM_UUID = IdGenerator { UUID.randomUUID().toString() }
    }
}
