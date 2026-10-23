package dev.linakbed.app.log

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrashLoggerTest {
    private var original: Thread.UncaughtExceptionHandler? = null
    private val delegated = mutableListOf<Throwable>()

    @Before
    fun installFakePrevious() {
        original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> delegated += error }
    }

    @After
    fun restore() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun `records the crash and then delegates to the previous handler`() {
        val recorded = mutableListOf<Pair<String, Throwable>>()
        CrashLogger { thread, error -> recorded += thread.name to error }.install()
        val crash = IllegalStateException("fatal")

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread({}, "main-test"), crash)

        assertThat(recorded).containsExactly("main-test" to crash)
        assertThat(delegated).containsExactly(crash)
    }

    @Test
    fun `still delegates when recording fails`() {
        CrashLogger { _, _ -> error("disk full") }.install()
        val crash = IllegalStateException("fatal")

        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)

        assertThat(delegated).containsExactly(crash)
    }
}
