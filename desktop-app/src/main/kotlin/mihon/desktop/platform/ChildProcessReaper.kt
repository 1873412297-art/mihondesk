package mihon.desktop.platform

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Kills helper processes (extension hosts, the JCEF web view host) when this JVM shuts down.
 *
 * Owners still close their children normally; this covers the paths where that never runs
 * (`exitProcess` from a command, an uncaught startup failure, Ctrl+C). Children additionally exit
 * on their own once their stdin pipe closes, which handles a hard crash of this process. On
 * Windows the job object of the extension host already provides kill-on-close.
 */
object ChildProcessReaper {
    private val children = ConcurrentHashMap.newKeySet<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::reapAll, "mihon-child-reaper"))
    }

    fun track(process: Process): Process {
        children += process
        process.onExit().thenAccept { children -= it }
        return process
    }

    private fun reapAll() {
        val alive = children.filter { it.isAlive }
        alive.forEach { process ->
            process.descendants().forEach { it.destroy() }
            process.destroy()
        }
        alive.forEach { process ->
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }
}
