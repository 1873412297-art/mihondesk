package mihon.extension.host

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get

/** First caller wins; a concurrent host initialization registering the same type is not an error. */
internal inline fun <reified T : Any> registerInjektSingleton(factory: () -> T) {
    try {
        Injekt.get<T>()
    } catch (_: Exception) {
        try {
            Injekt.addSingleton(factory())
        } catch (error: Exception) {
            // Usually a concurrent initialization won the race, so only a lookup that still fails
            // is a real problem worth reporting.
            if (runCatching { Injekt.get<T>() }.isFailure) {
                System.err.println("Unable to register ${T::class.java.name}: ${error.message}")
            }
        }
    }
}
