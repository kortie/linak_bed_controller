package dev.linakbed.app.feature.diagnostics

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.service.ServiceRemoteRequestDispatcher
import dev.linakbed.app.service.ServiceStarter
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.fake.FakeRemoteCommandHandler
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val clock = FakeClock()
    private val logs = File(app.filesDir, "diag-logs").apply { deleteRecursively() }
    private val remoteDispatcher =
        ServiceRemoteRequestDispatcher(
            handler = FakeRemoteCommandHandler(),
            starter = ServiceStarter { throw ForegroundServiceStartNotAllowedException("background") },
            notifier = { _, _ -> },
            executionLog = FakeExecutionLog(),
            clock = clock,
            logger = FakeBedLogger(),
        )
    private lateinit var viewModel: DiagnosticsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        resetFileProviderCache()
        viewModel =
            DiagnosticsViewModel(
                systemChecks = { mapOf(SystemCheck.PAIRED to true, SystemCheck.POST_NOTIFICATIONS to false) },
                exporter = LogExporter(app, logs, clock),
                dispatcher = remoteDispatcher,
                io = dispatcher,
            )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows checks and the log directory`() =
        runTest(dispatcher) {
            runCurrent()

            assertThat(viewModel.uiState.value.checks[SystemCheck.POST_NOTIFICATIONS]).isFalse()
            assertThat(viewModel.uiState.value.logDirectory).isEqualTo(logs.absolutePath)
        }

    @Test
    fun `warns after a fallback execution`() =
        runTest(dispatcher) {
            runCurrent()
            assertThat(viewModel.uiState.value.lastFallback).isNull()

            remoteDispatcher.dispatch(RemoteRequest("r", RemoteChannel.SMARTTHINGS_NOTIFICATION, RemoteCommand.Stop, Instant.EPOCH, null))
            runCurrent()

            assertThat(viewModel.uiState.value.lastFallback).isEqualTo(clock.now())
        }

    @Test
    fun `export with no logs reports empty, with logs yields a share intent`() =
        runTest(dispatcher) {
            viewModel.export()
            runCurrent()
            assertThat(viewModel.uiState.value.exportEmpty).isTrue()
            assertThat(viewModel.uiState.value.shareIntent).isNull()

            logs.mkdirs()
            File(logs, "linakbed-0.log").writeText("line")
            viewModel.export()
            runCurrent()
            assertThat(viewModel.uiState.value.shareIntent).isNotNull()

            viewModel.onShareHandled()
            assertThat(viewModel.uiState.value.shareIntent).isNull()
        }
}
