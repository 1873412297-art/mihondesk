package mihon.desktop.ui.browse.migration

import mihon.extension.source.model.SManga
import java.util.Locale

object MigrationMatcher {
    const val HIGH_CONFIDENCE_THRESHOLD = 0.85
    const val ELIGIBLE_THRESHOLD = 0.45

    data class ScoredCandidate(
        val manga: SManga,
        val score: Double,
        val isExactMatch: Boolean,
        val isHighConfidence: Boolean,
    )

    data class MatchEvaluation(
        val candidates: List<ScoredCandidate>,
        val bestMatch: ScoredCandidate?,
        val isUniqueHighConfidence: Boolean,
    )

    /**
     * Normalizes a title: lowercase, keeps letters (including CJK) and digits.
     */
    fun normalizeTitle(title: String): String = title
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    /**
     * Strips text in brackets (parentheses, square brackets, braces, angle brackets),
     * removes non-alphanumeric punctuation, and collapses whitespace.
     */
    fun cleanTitle(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        var cleaned = removeBrackets(lower)
        cleaned = cleaned.replace(PUNCTUATION_REGEX, " ")
        return cleaned.trim().replace(SPACES_REGEX, " ")
    }

    private fun removeBrackets(text: String): String {
        val sb = StringBuilder()
        var depth = 0
        for (ch in text) {
            when (ch) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> if (depth > 0) depth--
                else -> if (depth == 0) sb.append(ch)
            }
        }
        val result = sb.toString().trim()
        return if (result.length >= 2) result else text
    }

    /**
     * Scores title similarity between 0.0 and 1.0.
     */
    fun scoreMatch(sourceTitle: String, candidateTitle: String): Double {
        val s1 = sourceTitle.trim()
        val s2 = candidateTitle.trim()
        if (s1.equals(s2, ignoreCase = true)) return 1.0

        val norm1 = normalizeTitle(s1)
        val norm2 = normalizeTitle(s2)
        if (norm1.isNotEmpty() && norm1 == norm2) return 0.98

        val clean1 = cleanTitle(s1)
        val clean2 = cleanTitle(s2)
        if (clean1.isNotEmpty() && clean2.isNotEmpty()) {
            if (clean1 == clean2) return 0.95
            val cleanNorm1 = normalizeTitle(clean1)
            val cleanNorm2 = normalizeTitle(clean2)
            if (cleanNorm1.isNotEmpty() && cleanNorm1 == cleanNorm2) return 0.93
        }

        // Substring / prefix check
        if (norm1.isNotEmpty() && norm2.isNotEmpty()) {
            if (norm1.startsWith(norm2) || norm2.startsWith(norm1)) {
                val ratio = minOf(norm1.length, norm2.length).toDouble() / maxOf(norm1.length, norm2.length)
                if (ratio >= 0.75) {
                    return 0.82 + 0.1 * ratio
                }
            }
        }

        // Normalized Levenshtein similarity
        val normSim = similarity(norm1, norm2)
        val cleanSim = if (clean1.isNotEmpty() && clean2.isNotEmpty()) {
            similarity(clean1, clean2)
        } else {
            0.0
        }
        return maxOf(normSim, cleanSim)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else minOf(prev, dp[j - 1], dp[j]) + 1
                prev = temp
            }
        }
        return dp[s2.length]
    }

    fun similarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return (1.0 - (dist.toDouble() / maxLen)).coerceIn(0.0, 1.0)
    }

    fun evaluateCandidates(sourceTitle: String, candidates: List<SManga>): MatchEvaluation {
        if (candidates.isEmpty()) {
            return MatchEvaluation(emptyList(), null, false)
        }

        val scored = candidates.map { candidate ->
            val score = scoreMatch(sourceTitle, candidate.title)
            val isExact = sourceTitle.trim().equals(candidate.title.trim(), ignoreCase = true) ||
                (
                    normalizeTitle(sourceTitle).isNotEmpty() &&
                        normalizeTitle(sourceTitle) == normalizeTitle(candidate.title)
                    )
            ScoredCandidate(
                manga = candidate,
                score = score,
                isExactMatch = isExact,
                isHighConfidence = score >= HIGH_CONFIDENCE_THRESHOLD,
            )
        }.sortedByDescending { it.score }

        val best = scored.firstOrNull()
        val second = scored.getOrNull(1)

        val isUniqueHighConfidence = when {
            best == null -> false
            best.score < HIGH_CONFIDENCE_THRESHOLD -> false
            second == null -> true
            // If best is exact match (or score >= 0.95) and second is noticeably lower
            best.score >= 0.95 && second.score < 0.90 -> true
            // If best has high confidence and second candidate is significantly lower
            (best.score - second.score) >= 0.15 -> true
            else -> false
        }

        return MatchEvaluation(
            candidates = scored,
            bestMatch = best,
            isUniqueHighConfidence = isUniqueHighConfidence,
        )
    }

    private val PUNCTUATION_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES_REGEX = Regex("\\s+")
}
