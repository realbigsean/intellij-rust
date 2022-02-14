/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.stdext.randomLowercaseAlphabetic

class MacroBatch2(rootDirName: String) {
    private val contentRoot: String = "/$MACRO_EXPANSION_VFS_ROOT/$rootDirName"

    /**
     * Should be used if file was just created and there is no corresponding [VirtualFile] yet.
     * If we have [VirtualFile], then [VfsUtil.markDirty] should be used directly.
     */
    private val pathsToMarkDirty: MutableSet<String> = hashSetOf()

    val hasChanges: Boolean get() = pathsToMarkDirty.isNotEmpty()

    private val expansionFileSystem: MacroExpansionFileSystem = MacroExpansionFileSystem.getInstance()

    class Path(val path: String) {
        fun toVirtualFile(): VirtualFile? =
            MacroExpansionFileSystem.getInstance().findFileByPath(path)
    }

    fun createFile(content: ByteArray, crate: CratePersistentId, expansionName: String): Path {
        val path = "${crate}/${expansionNameToPath(expansionName)}"
        return createFile(content, path)
    }

    private fun createFile(content: ByteArray, relativePath: String): Path {
        val path = "$contentRoot/$relativePath"
        expansionFileSystem.createFileWithContent(path, content, mkdirs = true)
        val parent = path.substring(0, path.lastIndexOf('/'))
        pathsToMarkDirty += parent
        return Path(path)
    }

    fun deleteFile(file: FSItem.FSFile) {
        file.delete()

        val virtualFile = expansionFileSystem.findFileByPath(file.absolutePath())
        virtualFile?.let { markDirty(it) }
    }

    fun applyToVfs(async: Boolean, callback: Runnable?) {
        val root = expansionFileSystem.findFileByPath("/") ?: return

        for (path in pathsToMarkDirty) {
            markDirty(findNearestExistingFile(root, path))
        }

        RefreshQueue.getInstance().refresh(async, true, callback, root)
    }

    private fun markDirty(file: VirtualFile) {
        VfsUtil.markDirty(false, false, file)
    }
}

private fun findNearestExistingFile(root: VirtualFile, path: String): VirtualFile {
    var file = root
    for (segment in StringUtil.split(path, "/")) {
        file = file.findChild(segment) ?: break
    }
    return file
}

// todo(unite) delete
interface MacroExpansionVfsBatch {
    interface Path {
        fun toVirtualFile(): VirtualFile?
    }

    fun resolve(file: VirtualFile): Path

    fun createFileWithContent(content: ByteArray, stepNumber: Int): Path
    fun deleteFile(file: VirtualFile)
    fun writeFile(file: VirtualFile, content: ByteArray): Path

    fun applyToVfs(async: Boolean, callback: Runnable?)
}

class MacroExpansionVfsBatchImpl(rootDirName: String) : MacroExpansionVfsBatch {
    private val contentRoot = "/$MACRO_EXPANSION_VFS_ROOT/$rootDirName"
    private val batch: VfsBatch = VfsBatch()

    override fun resolve(file: VirtualFile): MacroExpansionVfsBatch.Path =
        PathImpl.VFile(file)

    override fun createFileWithContent(content: ByteArray, stepNumber: Int): MacroExpansionVfsBatch.Path =
        PathImpl.StringPath(createFileInternal(content, stepNumber))

    private fun createFileInternal(content: ByteArray, stepNumber: Int): String {
        val name = randomLowercaseAlphabetic(16)
        val parentPath = "$contentRoot/${stepNumber}/${name[0]}/${name[1]}"
        return batch.createFile(parentPath, name.substring(2) + ".rs", content)
    }

    override fun deleteFile(file: VirtualFile) {
        batch.deleteFile(file)
    }

    override fun writeFile(file: VirtualFile, content: ByteArray): MacroExpansionVfsBatch.Path {
        batch.writeFile(file, content)
        return resolve(file)
    }

    override fun applyToVfs(async: Boolean, callback: Runnable?) {
        batch.applyToVfs(async, callback)
    }

    private sealed class PathImpl : MacroExpansionVfsBatch.Path {

        class VFile(val file: VirtualFile) : PathImpl() {
            override fun toVirtualFile(): VirtualFile = file
        }

        class StringPath(val path: String) : PathImpl() {
            override fun toVirtualFile(): VirtualFile? =
                MacroExpansionFileSystem.getInstance().findFileByPath(path)
        }
    }
}

class VfsBatch {
    private val pathsToMarkDirty: MutableSet<String> = mutableSetOf()

    fun createFile(parent: String, name: String, content: String): String =
        createFile(parent, name, content.toByteArray())

    fun createFile(parent: String, name: String, content: ByteArray): String {
        val child = "$parent/$name"
        MacroExpansionFileSystem.getInstance().createFileWithContent(child, content, mkdirs = true)
        pathsToMarkDirty += parent
        return child
    }

    fun writeFile(file: VirtualFile, content: String) {
        writeFile(file, content.toByteArray())
    }

    fun writeFile(file: VirtualFile, content: ByteArray) {
        MacroExpansionFileSystem.getInstance().setFileContent(file.path, content)
        markDirty(file)
    }

    fun deleteFile(file: VirtualFile) {
        MacroExpansionFileSystem.getInstance().deleteFile(file.path)
        markDirty(file)
    }

    fun applyToVfs(async: Boolean, callback: Runnable? = null) {
        val root = MacroExpansionFileSystem.getInstance().findFileByPath("/") ?: return

        for (path in pathsToMarkDirty) {
            markDirty(findNearestExistingFile(root, path))
        }

        RefreshQueue.getInstance().refresh(async, true, callback, root)
    }

    private fun findNearestExistingFile(root: VirtualFile, path: String): VirtualFile {
        var file = root
        for (segment in StringUtil.split(path, "/")) {
            file = file.findChild(segment) ?: break
        }
        return file
    }

    private fun markDirty(file: VirtualFile) {
        VfsUtil.markDirty(false, false, file)
    }
}
