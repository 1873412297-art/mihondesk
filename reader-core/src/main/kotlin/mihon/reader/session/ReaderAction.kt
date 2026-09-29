package mihon.reader.session

import mihon.reader.model.PageId
import mihon.reader.model.ReaderPan
import mihon.reader.model.ReaderPosition
import mihon.reader.model.ReaderViewport
import mihon.reader.model.ReadingMode
import mihon.reader.model.ScaleMode

sealed interface ReaderAction {
    data object Next : ReaderAction
    data object Previous : ReaderAction
    data class SelectPage(val index: Int) : ReaderAction
    data class SetViewportAnchor(val position: ReaderPosition) : ReaderAction
    data class ChangeMode(val mode: ReadingMode) : ReaderAction
    data class SetCoverOffset(val enabled: Boolean) : ReaderAction
    data class SetScaleMode(val scaleMode: ScaleMode) : ReaderAction
    data class SetZoom(val zoom: Float) : ReaderAction
    data class SetPan(val pan: ReaderPan) : ReaderAction
    data class SetViewport(val viewport: ReaderViewport) : ReaderAction
    data class SetVisiblePages(val pageIds: List<PageId>) : ReaderAction
    data class SetPageIntrinsicSize(val pageId: PageId, val width: Int, val height: Int) : ReaderAction
    data class SetForeground(val foreground: Boolean) : ReaderAction
    data class SetContentVisible(val visible: Boolean) : ReaderAction
    data class SetDualPageSplit(val split: mihon.reader.layout.DualPageSplit) : ReaderAction
    data class SetDualPageRotateToFit(val enabled: Boolean) : ReaderAction
}
