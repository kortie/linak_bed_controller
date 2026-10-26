package dev.linakbed.app.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingViewModelTest {
    private val viewModel = OnboardingViewModel()
    private val state get() = viewModel.uiState.value

    @Test
    fun `starts at the intro`() {
        assertThat(state.step).isEqualTo(OnboardingStep.INTRO)
    }

    @Test
    fun `start without permission asks for it, granting moves to connect`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = false))
        assertThat(state.step).isEqualTo(OnboardingStep.PERMISSION)

        viewModel.onAction(OnboardingAction.PermissionResult(granted = true))
        assertThat(state.step).isEqualTo(OnboardingStep.CONNECT)
    }

    @Test
    fun `start with permission goes straight to connect`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = true))

        assertThat(state.step).isEqualTo(OnboardingStep.CONNECT)
    }

    @Test
    fun `denying shows the settings hint and stays, granting later in settings continues`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = false))
        viewModel.onAction(OnboardingAction.PermissionResult(granted = false))
        assertThat(state.step).isEqualTo(OnboardingStep.PERMISSION)
        assertThat(state.permissionDenied).isTrue()

        viewModel.onAction(OnboardingAction.PermissionChecked(granted = true))
        assertThat(state.step).isEqualTo(OnboardingStep.CONNECT)
        assertThat(state.permissionDenied).isFalse()
    }

    @Test
    fun `a resume check without permission does not mark it denied`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = false))

        viewModel.onAction(OnboardingAction.PermissionChecked(granted = false))

        assertThat(state.permissionDenied).isFalse()
    }

    @Test
    fun `permission revoked while connecting goes back to the permission step`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = true))

        viewModel.onAction(OnboardingAction.PermissionChecked(granted = false))

        assertThat(state.step).isEqualTo(OnboardingStep.PERMISSION)
    }

    @Test
    fun `back returns to the intro`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = true))

        viewModel.onAction(OnboardingAction.Back)

        assertThat(state.step).isEqualTo(OnboardingStep.INTRO)
    }

    @Test
    fun `with permission already granted the steps count 1 of 2 then 2 of 2`() {
        viewModel.onAction(OnboardingAction.PermissionChecked(granted = true))
        assertThat(state.step).isEqualTo(OnboardingStep.INTRO)
        assertThat(state.stepNumber to state.visibleSteps.size).isEqualTo(1 to 2)

        viewModel.onAction(OnboardingAction.Start(permissionGranted = true))

        assertThat(state.step).isEqualTo(OnboardingStep.CONNECT)
        assertThat(state.stepNumber to state.visibleSteps.size).isEqualTo(2 to 2)
    }

    @Test
    fun `without permission there are 3 steps, and granting midway keeps the count at 3`() {
        viewModel.onAction(OnboardingAction.Start(permissionGranted = false))
        assertThat(state.stepNumber to state.visibleSteps.size).isEqualTo(2 to 3)

        viewModel.onAction(OnboardingAction.PermissionResult(granted = true))

        assertThat(state.step).isEqualTo(OnboardingStep.CONNECT)
        assertThat(state.stepNumber to state.visibleSteps.size).isEqualTo(3 to 3)
    }
}
