package mihon.reader.source
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.LARGE_INTEGER
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.W32APIOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.NonWritableChannelException
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

internal class WindowsBasicFileAttributes(
    private val identity: WindowsFileIdentity,
    basic: WinBase.FILE_BASIC_INFO,
    standard: WinBase.FILE_STANDARD_INFO,
) : BasicFileAttributes {
    private val creation = windowsFileTime(basic.CreationTime.value)
    private val access = windowsFileTime(basic.LastAccessTime.value)
    private val modified = windowsFileTime(basic.LastWriteTime.value)
    private val directory = basic.FileAttributes and WinNT.FILE_ATTRIBUTE_DIRECTORY != 0
    private val byteCount = standard.EndOfFile.value

    override fun lastModifiedTime(): FileTime = modified

    override fun lastAccessTime(): FileTime = access

    override fun creationTime(): FileTime = creation

    override fun isRegularFile(): Boolean = !directory

    override fun isDirectory(): Boolean = directory

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = byteCount

    override fun fileKey(): Any = identity
}

@Suppress("FunctionName")
internal interface WindowsFileFunctions : Library {
    fun GetFileInformationByHandle(file: HANDLE, information: ByHandleFileInformation): Boolean

    fun SetFilePointer(
        file: HANDLE,
        distanceLow: Int,
        distanceHigh: IntByReference,
        moveMethod: Int,
    ): Int

    fun GetFileSizeEx(file: HANDLE, size: LARGE_INTEGER.ByReference): Boolean

    companion object {
        val INSTANCE: WindowsFileFunctions = Native.load(
            "kernel32",
            WindowsFileFunctions::class.java,
            W32APIOptions.UNICODE_OPTIONS,
        )
    }
}

@Suppress("VariableNaming")
@com.sun.jna.Structure.FieldOrder(
    "fileAttributes",
    "creationTime",
    "lastAccessTime",
    "lastWriteTime",
    "volumeSerialNumber",
    "fileSizeHigh",
    "fileSizeLow",
    "numberOfLinks",
    "fileIndexHigh",
    "fileIndexLow",
)
internal class ByHandleFileInformation : com.sun.jna.Structure() {
    @JvmField var fileAttributes: Int = 0

    @JvmField var creationTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var lastAccessTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var lastWriteTime: WinBase.FILETIME = WinBase.FILETIME()

    @JvmField var volumeSerialNumber: Int = 0

    @JvmField var fileSizeHigh: Int = 0

    @JvmField var fileSizeLow: Int = 0

    @JvmField var numberOfLinks: Int = 0

    @JvmField var fileIndexHigh: Int = 0

    @JvmField var fileIndexLow: Int = 0
}

internal fun windowsFileTime(value: Long): FileTime {
    val millisecondsSinceUnixEpoch = value / WINDOWS_TICKS_PER_MILLISECOND - WINDOWS_EPOCH_MILLISECONDS
    return FileTime.from(millisecondsSinceUnixEpoch, TimeUnit.MILLISECONDS)
}

internal fun windowsError(operation: String): IOException {
    val code = Native.getLastError()
    return IOException("$operation failed with Win32 error $code: ${Kernel32Util.formatMessageFromLastErrorCode(code)}")
}

internal fun requireRange(size: Int, offset: Int, length: Int) {
    if (offset < 0 || length < 0 || offset > size - length) throw IndexOutOfBoundsException()
}

internal const val WINDOWS_TICKS_PER_MILLISECOND = 10_000L
internal const val WINDOWS_EPOCH_MILLISECONDS = 11_644_473_600_000L
internal const val FILE_BEGIN = 0
internal const val FILE_CURRENT = 1
internal const val INVALID_SET_FILE_POINTER = -1
internal const val MAX_READ_BYTES = 1024 * 1024
internal const val DIRECTORY_BUFFER_BYTES = 64 * 1024
internal const val DIRECTORY_NEXT_OFFSET = 0L
internal const val DIRECTORY_NAME_LENGTH = 60L
internal const val DIRECTORY_NAME_OFFSET = 104L
