package mihon.desktop.ui

import kotlin.time.Duration.Companion.minutes

/**
 * Overall budget for a single `runComposeUiTest` call.
 *
 * Compose Multiplatform defaults `testTimeout` to 60 seconds and spends that single value on two
 * different deadlines: the real-time budget of the underlying `kotlinx.coroutines.test.runTest`
 * (exhausting it reports an opaque `UncompletedCoroutinesError`) and the deadline of the
 * framework's own idle waits, `isIdle`/`awaitIdle` (which report `ComposeTimeoutException`).
 *
 * The desktop UI tests drive a real frame clock and poll with their own `waitUntil`, so a test body
 * can legitimately take longer than a minute while the machine is busy: Gradle runs other projects'
 * test tasks in parallel (`org.gradle.parallel=true`) and the polls have to wait on repository and
 * source work running on real background dispatchers. Every one of these tests passes in isolation
 * and the whole class normally finishes in a couple of seconds, so the short budget was never
 * measuring the code under test -- it was measuring how loaded the machine happened to be.
 *
 * Raising the overall budget keeps the per-step `waitUntil` budgets authoritative, so a genuine
 * regression still fails with the step's own descriptive `ComposeTimeoutException` instead of the
 * opaque coroutine budget error.
 */
internal val UI_TEST_TIMEOUT = 5.minutes
