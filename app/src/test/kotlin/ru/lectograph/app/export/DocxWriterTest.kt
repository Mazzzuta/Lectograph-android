package ru.lectograph.app.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class DocxWriterTest {
    @Test fun documentIsValidXmlAndNotesAreExplicit() {
        fun document(notes: String): Map<String, String> {
            val bytes = ByteArrayOutputStream()
            DocxWriter.write(bytes, "Физика <1> & теория", "Предмет", "Первая строка\nВторая \"цитата\"\u0001", notes)
            val entries = linkedMapOf<String, String>()
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
            }
            entries.values.forEach { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it.byteInputStream()) }
            return entries
        }
        val without = document("")
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), without.keys)
        assertTrue(without.getValue("word/document.xml").contains("&lt;1&gt; &amp;"))
        assertFalse(without.getValue("word/document.xml").contains("Мои заметки"))
        assertTrue(document("Личное").getValue("word/document.xml").contains("Личное"))
    }
}
