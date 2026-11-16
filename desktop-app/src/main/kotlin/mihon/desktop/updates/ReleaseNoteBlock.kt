package mihon.desktop.updates

/** One piece of the "what's new" part of a GitHub release body. Inline Markdown is kept in [text]. */
sealed interface ReleaseNoteBlock {
    val text: String

    data class Heading(override val text: String) : ReleaseNoteBlock
    data class Paragraph(override val text: String) : ReleaseNoteBlock
    data class Bullet(override val text: String) : ReleaseNoteBlock
}

private val htmlComment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

/** A section maintained by a workflow between `<!-- name:begin -->` and `<!-- name:end -->`. */
private val markedSection =
    Regex("<!--\\s*([\\w-]+):begin\\s*-->.*?<!--\\s*\\1:end\\s*-->", RegexOption.DOT_MATCHES_ALL)
private val headingLine = Regex("^#{1,6}\\s+(.*?)\\s*#*$")
private val bulletLine = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.*)$")

/** Headings where the release body stops describing changes (download / install instructions). */
private val stopHeadings = Regex("^(安装|安裝|下载|下載|install(ation)?|download(s)?)(?=\\W|$)", RegexOption.IGNORE_CASE)

/** A title that only names the release, e.g. "mihondesk 0.5.0" or "v0.5.0". */
private val releaseTitle = Regex("mihondesk|\\bv?\\d+\\.\\d+", RegexOption.IGNORE_CASE)

/**
 * The "what's new" part of a release body, for the in-app update card: everything before the
 * install/download section, without the title that only repeats the version, sections that a
 * workflow keeps between begin/end markers (the Android companion's notes), comments or code
 * blocks (checksums).
 */
fun releaseNoteBlocks(markdown: String): List<ReleaseNoteBlock> {
    val lines = markdown.replace("\r\n", "\n").replace(markedSection, "").replace(htmlComment, "").lines()
    val blocks = mutableListOf<ReleaseNoteBlock>()
    var paragraph = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += ReleaseNoteBlock.Paragraph(paragraph.toString().trim())
        paragraph = StringBuilder()
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            inCode = !inCode
            continue
        }
        if (inCode) continue
        val heading = headingLine.find(line.trim())?.groupValues?.get(1)
        val bullet = bulletLine.find(line)?.groupValues?.get(1)
        when {
            heading != null -> {
                flushParagraph()
                if (stopHeadings.containsMatchIn(heading.trim())) break
                blocks += ReleaseNoteBlock.Heading(heading)
            }
            bullet != null -> {
                flushParagraph()
                blocks += ReleaseNoteBlock.Bullet(bullet.trim())
            }
            line.isBlank() -> flushParagraph()
            // An indented line right after a bullet continues it.
            raw.startsWith("  ") && blocks.lastOrNull() is ReleaseNoteBlock.Bullet && paragraph.isEmpty() -> {
                val last = blocks.removeAt(blocks.lastIndex)
                blocks += ReleaseNoteBlock.Bullet(last.text + " " + line.trim())
            }
            else -> paragraph.append(if (paragraph.isEmpty()) "" else " ").append(line.trim())
        }
    }
    flushParagraph()
    // The release title ("mihondesk 0.5.0") only repeats what the card already says.
    val first = blocks.firstOrNull()
    if (first is ReleaseNoteBlock.Heading && releaseTitle.containsMatchIn(first.text)) blocks.removeAt(0)
    return blocks
}
