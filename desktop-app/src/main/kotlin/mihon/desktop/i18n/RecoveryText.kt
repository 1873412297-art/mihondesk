package mihon.desktop.i18n

import androidx.compose.runtime.Composable

@Composable
fun recoveryText(english: String, simplified: String, traditional: String = simplified): String =
    when (LocalStrings.current) {
        SimplifiedChineseStrings -> simplified
        TraditionalChineseStrings -> traditional
        else -> english
    }

@Composable
fun coverRepairActionText(count: Int): String =
    recoveryText(
        english = "Repair broken covers ($count)",
        simplified = "修复失效封面 ($count)",
        traditional = "修復失效封面 ($count)",
    )

@Composable
fun coverRepairRunningText(): String =
    recoveryText(
        english = "Repairing covers...",
        simplified = "正在修复封面...",
        traditional = "正在修復封面...",
    )
