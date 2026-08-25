package atropos.core.agent

import java.util.concurrent.atomic.AtomicBoolean
import atropos.core.provider.ProviderOnboardingService

data class SelfHostStartupContinuationResult(
    val attempted: Boolean,
    val ok: Boolean,
    val message: String? = null
)

/** Advances one unfinished self-host goal after process-start recovery. */
class SelfHostStartupContinuationService(
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val selfHostService: SelfHostGoalService = SelfHostGoalService(onboarding = onboarding),
    private val hasUnfinishedGoals: () -> Boolean = {
        selfHostService.loadUnfinishedGoals().isNotEmpty()
    },
    private val resolveResumable: () -> SelfHostResult = { selfHostService.resolveResumableGoal() },
    private val recoverAndContinue: (String) -> SelfHostResult = { goalId ->
        selfHostService.recoverAndContinue(goalId, "self-host automatic startup continuation")
    }
) {
    private val attemptedInProcess = AtomicBoolean(false)

    fun continueOnce(recoveryAvailable: Boolean): SelfHostStartupContinuationResult {
        if (!recoveryAvailable) {
            return SelfHostStartupContinuationResult(
                attempted = false,
                ok = false,
                message = "startup self-host continuation skipped: crash recovery unavailable"
            )
        }
        if (!attemptedInProcess.compareAndSet(false, true)) {
            return SelfHostStartupContinuationResult(false, true)
        }

        val unfinishedAvailable = try {
            hasUnfinishedGoals()
        } catch (failure: Exception) {
            attemptedInProcess.set(false)
            return SelfHostStartupContinuationResult(
                attempted = true,
                ok = false,
                message = "startup self-host continuation selection failed: ${failure.message ?: failure.javaClass.simpleName}"
            )
        }

        if (!unfinishedAvailable) {
            return SelfHostStartupContinuationResult(
                attempted = false,
                ok = true,
                message = "startup self-host continuation: no resumable goal"
            )
        }

        val selected = try {
            resolveResumable()
        } catch (failure: Exception) {
            attemptedInProcess.set(false)
            return SelfHostStartupContinuationResult(
                attempted = true,
                ok = false,
                message = "startup self-host continuation selection failed: ${failure.message ?: failure.javaClass.simpleName}"
            )
        }
        val goalId = selected.goal?.record?.id
        if (!selected.ok) {
            attemptedInProcess.set(false)
            return SelfHostStartupContinuationResult(
                attempted = true,
                ok = false,
                message = "startup self-host continuation selection refused: ${selected.message}"
            )
        }
        if (goalId.isNullOrBlank()) {
            return SelfHostStartupContinuationResult(
                attempted = false,
                ok = true,
                message = "startup self-host continuation: no resumable goal"
            )
        }
        val continued = try {
            recoverAndContinue(goalId)
        } catch (failure: Exception) {
            attemptedInProcess.set(false)
            return SelfHostStartupContinuationResult(
                attempted = true,
                ok = false,
                message = "startup self-host continuation failed: ${failure.message ?: failure.javaClass.simpleName}"
            )
        }
        if (!continued.ok) attemptedInProcess.set(false)
        return SelfHostStartupContinuationResult(
            attempted = true,
            ok = continued.ok,
            message = "startup self-host continuation goal=$goalId: ${continued.message}"
        )
    }
}
