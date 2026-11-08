package ru.lectograph.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import ru.lectograph.app.ui.settings.storageBytes

class StorageTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun countsNestedFilesAndMissingFolders() {
        assertEquals(0L, storageBytes(java.io.File(temporary.root, "missing")))
        temporary.newFile("audio").writeBytes(ByteArray(15))
        java.io.File(temporary.newFolder("nested"), "model").writeBytes(ByteArray(27))
        assertEquals(42L, storageBytes(temporary.root))
    }
}
