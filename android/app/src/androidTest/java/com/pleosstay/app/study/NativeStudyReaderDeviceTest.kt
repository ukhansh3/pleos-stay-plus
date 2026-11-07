package com.pleosstay.app.study

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the actual Android native renderer/codec/Keystore. Not a host-JVM success substitute. */
@RunWith(AndroidJUnit4::class)
class NativeStudyReaderDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun nativePdfReadsActualPageCountAndRendersRealPixels() = runBlocking {
        val file = File.createTempFile("study-native-", ".pdf", context.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                repeat(2) { index ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(500, 700, index + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawRect(0f, 0f, 250f, 350f, Paint().apply { color = Color.BLUE })
                    page.canvas.drawText("학습 자료 ${index + 1}", 25f, 400f, Paint().apply { color = Color.BLACK; textSize = 22f })
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            } finally { pdf.close() }
            val reader = NativeStudyReader()
            val source = reader.open(file, "application/pdf")
            assertEquals(2, source.pageCount)
            val bitmap = reader.renderPage(source, 1, 1f)
            try { assertEquals(Color.BLUE, bitmap.getPixel(10, 10)); assertTrue(bitmap.height > bitmap.width) }
            finally { bitmap.recycle() }
        } finally { file.delete() }
    }
    @Test fun docxKoreanTableAndEmbeddedPngAreDecodedNatively() = runBlocking {
        val file = File.createTempFile("study-native-", ".docx", context.cacheDir)
        val picture = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val png = ByteArrayOutputStream().also { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        picture.recycle()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun part(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                part("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""".toByteArray())
                part("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="main" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray())
                part("word/document.xml", """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><w:body><w:p><w:r><w:t>한글 학습</w:t><w:drawing><a:blip r:embed="img"/></w:drawing></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>표 안 한글</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>""".toByteArray())
                part("word/_rels/document.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="img" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image.png"/></Relationships>""".toByteArray())
                part("word/media/image.png", png)
            }
            val doc = NativeStudyReader().open(file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            assertEquals("한글 학습", (doc.blocks.first() as StudyBlock.Paragraph).runs.single().text)
            assertTrue(doc.blocks.any { it is StudyBlock.Table })
            val bytes = (doc.blocks.first { it is StudyBlock.Picture } as StudyBlock.Picture).bytes
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try { assertEquals(Color.GREEN, image.getPixel(10, 10)) } finally { image.recycle() }
        } finally { file.delete() }
    }
    @Test fun androidKeystoreRetainsEncryptedVersionRecordsAcrossRepositoryRecreation() = runBlocking {
        val key = StudyMaterialKey("instrumented", UUID.randomUUID().toString(), "file", "v1")
        val first = createStudyRepository(context)
        try {
            first.saveNote(key, null, "0", "기기 암호화 메모")
            val second = createStudyRepository(context)
            assertEquals("기기 암호화 메모", second.load(key).notes.single().body)
            assertTrue(second.load(key.copy(versionId = "v2")).notes.isEmpty())
        } finally { first.deleteMaterialData(key) }
    }
}
