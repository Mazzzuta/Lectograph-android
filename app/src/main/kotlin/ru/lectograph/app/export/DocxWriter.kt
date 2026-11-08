package ru.lectograph.app.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxWriter {
    fun write(output: OutputStream, title: String, metadata: String, text: String, notes: String = "") {
        fun escape(value: String) = value.filter { it == '\n' || it == '\t' || it >= ' ' && it != '\uFFFE' && it != '\uFFFF' }
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun paragraph(value: String, bold: Boolean = false): String = "<w:p><w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"24\"/>${if (bold) "<w:b/>" else ""}</w:rPr><w:t xml:space=\"preserve\">${escape(value)}</w:t></w:r></w:p>"
        val body = paragraph(title, true) + paragraph(metadata) + text.lines().joinToString("") { paragraph(it) } +
            if (notes.isBlank()) "" else paragraph("Мои заметки", true) + notes.lines().joinToString("") { paragraph(it) }
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, xml: String) { zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>""")
        }
    }
}
