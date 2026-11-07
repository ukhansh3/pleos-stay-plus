package com.pleosstay.app.study

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StudyReaderTest {
    @Test fun readsRealOoxmlKoreanRunTableAndEmbeddedPicture() {
        val file = docx("""<w:p><w:r><w:rPr><w:b/><w:sz w:val="28"/></w:rPr><w:t>한글 학습</w:t></w:r><w:r><w:drawing><a:blip r:embed="image1"/></w:drawing></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>표 내용</w:t></w:r></w:p></w:tc></w:tr></w:tbl>""")
        try {
            val read = DocxStudyReader.read(file)
            assertNull(read.pageCount)
            val first = (read.blocks[0] as StudyBlock.Paragraph).runs.first()
            assertEquals("한글 학습", first.text); assertTrue(first.bold); assertEquals(14f, first.points)
            assertArrayEquals(byteArrayOf(1, 2, 3), (read.blocks[1] as StudyBlock.Picture).bytes)
            val table = read.blocks[2] as StudyBlock.Table
            assertEquals("표 내용", (table.rows.single().single().single() as StudyBlock.Paragraph).runs.single().text)
        } finally { file.delete() }
    }
    @Test fun rejectsDtdAndArchiveTraversal() {
        listOf(docx("<w:p/>", prefix = "<!DOCTYPE bad [<!ENTITY leak SYSTEM 'file:///secret'>]>"), docx("<w:p/>", mainTarget = "../../document.xml")).forEach { file ->
            try { try { DocxStudyReader.read(file); fail("unsafe package accepted") } catch (_: IllegalArgumentException) { } } finally { file.delete() }
        }
    }
    @Test fun utf8ReaderPreservesKoreanOffsetsAndRejectsInvalidEncoding() = runTest {
        val file = Files.createTempFile("study-utf8", ".txt").toFile()
        try {
            file.writeText("가".repeat(4100))
            val doc = NativeStudyReader().open(file, "text/plain")
            assertEquals(listOf(0, 4000), doc.textOffsets)
            assertEquals(1, restorePosition(doc, StudyProgress("TXT_OFFSET", "4000", null, false, 1)))
            file.writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            try { NativeStudyReader().open(file, "text/plain"); fail("invalid UTF8 accepted") } catch (_: java.nio.charset.CharacterCodingException) { }
        } finally { file.delete() }
    }
    @Test fun deepAndExcessiveXmlAreRejectedBeforeRecursiveExtraction() {
        listOf("<w:r>".repeat(1000) + "<w:t>deep</w:t>" + "</w:r>".repeat(1000), "<w:p/>".repeat(100_001)).forEach { body ->
            val file = docx(body)
            try {
                try { DocxStudyReader.read(file); fail("resource limit ignored") }
                catch (error: org.xml.sax.SAXException) {
                    // Host JAXP may reject depth at its stricter built-in cap before our SAX handler.
                    val message = error.message.orEmpty()
                    assertTrue(message, message.contains("resource limit") || message.contains("JAXP00010006"))
                }
            } finally { file.delete() }
        }
    }
    private fun docx(body: String, prefix: String = "", mainTarget: String = "word/document.xml"): File {
        val file = Files.createTempFile("study-docx", ".docx").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(path: String, data: ByteArray) { zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry() }
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="main" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="$mainTarget"/></Relationships>""".toByteArray())
            entry("word/document.xml", (prefix + """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><w:body>$body</w:body></w:document>""").toByteArray())
            entry("word/_rels/document.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="image1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image.png"/></Relationships>""".toByteArray())
            entry("word/media/image.png", byteArrayOf(1, 2, 3))
        }
        return file
    }
}
