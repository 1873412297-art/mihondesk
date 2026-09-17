package mihon.desktop

import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.db.DesktopLibraryDatabaseOpenException
import mihon.desktop.platform.PortableUpdatePendingException
import java.io.OutputStream
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/** Startup precedes Compose. Background commands must never wait for a modal dialog. */
internal fun reportStartupRecoveryFailure(
    error: Throwable,
    interactive: Boolean,
    output: OutputStream,
    strings: DesktopStrings,
    showMessage: (String, String) -> Unit = ::showDatabaseUpgradeMessage,
): Boolean {
    val (category, message) = when (error) {
        is PortableUpdatePendingException ->
            "PORTABLE_UPDATE_PENDING" to strings.text(UiText.PortableUpdatePending, error.applicationDirectory)
        is DesktopLibraryDatabaseOpenException.SnapshotFailed ->
            "DATABASE_SNAPSHOT_FAILED" to strings.text(UiText.UpgradeSnapshotFailed, error.snapshotDirectory)
        is DesktopLibraryDatabaseOpenException.MigrationFailed ->
            "DATABASE_MIGRATION_FAILED" to (
                strings.text(UiText.UpgradeMigrationFailed) +
                    (error.recoverySnapshot?.let { "\n\n" + strings.text(UiText.UpgradeRecoveryLocation, it) } ?: "")
                )
        else -> return false
    }
    DesktopCommandRunner.writeStartupFailure(output, category)
    if (interactive) {
        // Reporting must not replace the original failure when a graphical environment is unavailable.
        val title = if (error is PortableUpdatePendingException) {
            UiText.PortableUpdatePendingTitle
        } else {
            UiText.UpgradeFailedTitle
        }
        runCatching { showMessage(strings.text(title), message) }
    }
    return true
}

private fun showDatabaseUpgradeMessage(title: String, message: String) {
    val show = {
        val text = JTextArea(message, 10, 58).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            caretPosition = 0
        }
        JOptionPane.showMessageDialog(null, JScrollPane(text), title, JOptionPane.ERROR_MESSAGE)
    }
    if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait(show)
}
