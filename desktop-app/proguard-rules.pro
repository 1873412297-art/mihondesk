# ProGuard rules for the release desktop distribution (compose packageRelease*).
#
# mihondesk is a plugin host: converted Tachiyomi/Mihon extensions are loaded at runtime and
# link by name against the shims and libraries on our classpath, and child JVMs (extension
# host, webview helper) start from other main classes on the same jars. Whole-program
# shrinking rooted at mihon.desktop.MainKt cannot see any of that, so keep those surfaces
# intact; obfuscation and optimization are disabled in build.gradle.kts for the same reason.

# --- Our code, Android/Tachiyomi shims and child-process entry points ---
-keep class mihon.** { *; }
-keep class eu.kanade.** { *; }
-keep class tachiyomi.** { *; }
-keep class android.** { *; }
-keep class androidx.preference.** { *; }
-keep class rx.** { *; }
-keep class app.cash.** { *; }

# --- Libraries extensions link against directly ---
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.mozilla.javascript.** { *; }

# --- Extension conversion and signature verification (reflection in apksig ASN.1 parser) ---
-keep class com.android.apksig.** { *; }
-keep class com.googlecode.d2j.** { *; }
-keep class org.objectweb.asm.** { *; }

# --- ServiceLoader / reflection / JNI based libraries ---
-keep class com.twelvemonkeys.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.jetbrains.** { *; }
-keep class org.sqlite.** { *; }
-keep class io.ktor.** { *; }
-keep class org.brotli.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisible*

# --- Optional dependencies that are never present on desktop (scoped dontwarn) ---
# R8 bundled inside dex2jar references builder classes stripped from its own jar.
-dontwarn com.android.tools.r8.**
# dex2jar's APK re-signer uses JDK-internal PKCS7 API we never call.
-dontwarn com.googlecode.d2j.signapk.**
# commons-compress optional zstd codec.
-dontwarn com.github.luben.zstd.**
# antlr3 runtime optional StringTemplate debug support.
-dontwarn org.antlr.stringtemplate.**
# OkHttp optional TLS providers and GraalVM native-image support.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn org.graalvm.nativeimage.**
# jsoup optional re2j regex engine.
-dontwarn com.google.re2j.**
# Ktor ships a stale EnclosingMethod attribute for this lambda (library bug, harmless).
-dontwarn io.ktor.network.sockets.SocketBase$attachFor$1
