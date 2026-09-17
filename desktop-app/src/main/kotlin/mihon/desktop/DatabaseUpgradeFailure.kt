package mihon.desktop

import mihon.desktop.cli.DesktopCommandRunner
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.library.db.DesktopLibraryDatabaseOpenException
import java.io.OutputStream
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/** Startup precedes Compose. Background commands must never wait for a modal dialog. */
internal fun reportDatabaseUpgradeFailure(
    error: Throwable,
    interactive: Boolean,
    output: OutputStream,
    strings: DesktopStrings,
    showMessage: (String, String) -> Unit = ::showDatabaseUpgradeMessage,
): Boolean {
    val (category, message) = when (error) {
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
        runCatching { showMessage(strings.text(UiText.UpgradeFailedTitle), message) }
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
