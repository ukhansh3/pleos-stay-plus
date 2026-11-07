package com.pleosstay.app.study

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

enum class StudyFormat { PDF, DOCX, TXT }
data class StudyTextRun(val text:String,val bold:Boolean=false,val italic:Boolean=false,val underline:Boolean=false,val color:String?=null,val points:Float?=null)
sealed interface StudyBlock {
    data class Paragraph(val runs:List<StudyTextRun>):StudyBlock
    data class Table(val rows:List<List<List<StudyBlock>>>):StudyBlock
    data class Picture(val bytes:ByteArray,val description:String):StudyBlock
}
data class StudyDocument(val format:StudyFormat,val file:File,val pageCount:Int?=null,val blocks:List<StudyBlock> = emptyList(),val textOffsets:List<Int> = emptyList(),val warning:String?=null)
interface StudyReader {
    suspend fun open(file:File,mimeType:String):StudyDocument
    suspend fun renderPage(document:StudyDocument,pageIndex:Int,zoom:Float):Bitmap
}
class NativeStudyReader : StudyReader {
    override suspend fun open(file:File,mimeType:String):StudyDocument=withContext(Dispatchers.IO) {
        require(file.isFile && file.length() in 1..20L*1024*1024) { "자료 파일을 확인해 주세요 (최대 20MiB)." }
        when(mimeType) {
            "application/pdf" -> ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> require(renderer.pageCount>0);StudyDocument(StudyFormat.PDF,file,pageCount=renderer.pageCount) }
            }
            "text/plain" -> {
                val text=decode(file.readBytes()).removePrefix("\uFEFF")
                val blocks=mutableListOf<StudyBlock>();val offsets=mutableListOf<Int>()
                var offset=0
                while(offset<text.length) {
                    val end=(offset+4000).coerceAtMost(text.length)
                    offsets+=offset;blocks+=StudyBlock.Paragraph(listOf(StudyTextRun(text.substring(offset,end))))
                    offset=end
                }
                if(blocks.isEmpty()){offsets+=0;blocks+=StudyBlock.Paragraph(listOf(StudyTextRun("")))}
                StudyDocument(StudyFormat.TXT,file,blocks=blocks,textOffsets=offsets)
            }
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocxStudyReader.read(file)
            else -> error("지원하지 않는 자료 형식입니다.")
        }
    }
    override suspend fun renderPage(document:StudyDocument,pageIndex:Int,zoom:Float):Bitmap=withContext(Dispatchers.IO) {
        require(document.format==StudyFormat.PDF && pageIndex in 0 until checkNotNull(document.pageCount))
        ParcelFileDescriptor.open(document.file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> renderer.openPage(pageIndex).use { page ->
                var width=(900*zoom.coerceIn(.75f,3f)).toInt()
                var height=(width.toDouble()*page.height/page.width).toInt().coerceAtLeast(1)
                if(width.toLong()*height>6_000_000) { val scale=sqrt(6_000_000.0/(width.toLong()*height));width=(width*scale).toInt();height=(height*scale).toInt() }
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);bitmap }
                catch(error:Throwable) { bitmap.recycle();throw error }
            } }
        }
    }
}
private fun decode(bytes:ByteArray):String=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()

/** Native OOXML reflow viewer: real paragraphs/direct run formatting/tables/embedded PNG/JPEG, not Word pagination. */
internal object DocxStudyReader {
    private const val W="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    fun read(file:File):StudyDocument=ZipFile(file).use { zip ->
        require(zip.size()<=1500) { "DOCX 구성 항목이 너무 많습니다." }
        val root=xml(part(zip,"_rels/.rels",1024*1024))
        val main=root.descendants("Relationship").firstOrNull { it.getAttribute("Type").endsWith("/officeDocument") }
            ?: error("DOCX 본문 관계 정보가 없습니다.")
        require(main.getAttribute("TargetMode")!="External") { "외부 DOCX 본문은 열 수 없습니다." }
        val mainPath=resolve("",main.getAttribute("Target"))
        val document=xml(part(zip,mainPath,8*1024*1024))
        require(document.namespaceURI==W && document.localName=="document") { "이 OOXML 문서 형식은 지원하지 않습니다." }
        val body=document.children().firstOrNull { it.namespaceURI==W && it.localName=="body" } ?: error("DOCX 본문이 없습니다.")
        val parent=mainPath.substringBeforeLast('/',"")
        val relPath=(if(parent.isEmpty()) "" else "$parent/")+"_rels/"+mainPath.substringAfterLast('/')+".rels"
        val relationships=if(zip.getEntry(relPath)!=null) xml(part(zip,relPath,1024*1024)).descendants("Relationship").associateBy { it.getAttribute("Id") } else emptyMap()
        var imageBudget=0L
        fun picture(id:String):StudyBlock? {
            val rel=relationships[id] ?: return null
            if(rel.getAttribute("TargetMode")=="External")return StudyBlock.Paragraph(listOf(StudyTextRun("[외부 연결 이미지는 표시하지 않습니다]")))
            if(!rel.getAttribute("Type").endsWith("/image"))return null
            val path=resolve(parent,rel.getAttribute("Target"))
            if(!(path.endsWith(".png",true)||path.endsWith(".jpg",true)||path.endsWith(".jpeg",true)))return StudyBlock.Paragraph(listOf(StudyTextRun("[PNG/JPEG 외 이미지 형식은 미지원]")))
            val bytes=part(zip,path,4*1024*1024);imageBudget+=bytes.size
            require(imageBudget<=16L*1024*1024) { "문서 이미지가 메모리 보호 한도를 넘었습니다." }
            return StudyBlock.Picture(bytes,"문서에 포함된 이미지")
        }
        fun blocks(container:Element,depth:Int):List<StudyBlock> {
            require(depth<=8) { "중첩 표가 너무 깊습니다." }
            val result=mutableListOf<StudyBlock>()
            container.children().filter { it.namespaceURI==W }.forEach { block ->
                when(block.localName) {
                    "p" -> {
                        val runs=block.descendants("r").filter { it.namespaceURI==W }.map { run ->
                            val props=run.children().firstOrNull { it.localName=="rPr" }
                            fun property(name:String)=props?.children()?.firstOrNull { it.localName==name }?.getAttributeNS(W,"val")
                            fun active(name:String):Boolean=props?.children()?.any { it.localName==name && it.getAttributeNS(W,"val") !in listOf("false","0","off","none") }==true
                            val text=StringBuilder()
                            fun collect(node:Node) {
                                if(node is Element && node.namespaceURI==W && node.localName=="t")text.append(node.textContent)
                                else if(node is Element && node.namespaceURI==W && node.localName=="tab")text.append('\t')
                                else if(node is Element && node.namespaceURI==W && node.localName in listOf("br","cr"))text.append('\n')
                                else { val children=node.childNodes;for(i in 0 until children.length)collect(children.item(i)) }
                            }
                            collect(run)
                            StudyTextRun(text.toString(),active("b"),active("i"),active("u"),property("color")?.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) },property("sz")?.toFloatOrNull()?.div(2))
                        }
                        result+=StudyBlock.Paragraph(runs)
                        block.descendants("blip").forEach { blip -> picture(blip.getAttributeNS(R,"embed"))?.let(result::add) }
                    }
                    "tbl" -> result+=StudyBlock.Table(block.children().filter { it.localName=="tr" }.map { row ->
                        row.children().filter { it.localName=="tc" }.map { cell -> blocks(cell,depth+1) }
                    })
                    "sdt" -> block.children().firstOrNull { it.localName=="sdtContent" }?.let { result+=blocks(it,depth+1) }
                    "altChunk" -> result+=StudyBlock.Paragraph(listOf(StudyTextRun("[외부 삽입 콘텐츠는 미지원]")))
                }
                require(result.size<=10_000) { "문서 블록이 너무 많습니다." }
            }
            return result
        }
        StudyDocument(StudyFormat.DOCX,file,blocks=blocks(body,0).ifEmpty { listOf(StudyBlock.Paragraph(listOf(StudyTextRun("")))) },warning="DOCX 재배치 열람: 본문·직접 글자 서식·표·내장 PNG/JPEG 표시. Word의 고정 페이지·머리말·수식·복잡한 배치/스타일은 재현하지 않습니다. 이미지가 문단 뒤에 표시될 수 있습니다.")
    }
    private fun resolve(parent:String,target:String):String {
        require(target.isNotBlank() && !target.contains('\\') && !target.contains(':') && !target.contains('%')) { "지원하지 않는 DOCX 관계 경로입니다." }
        val parts=mutableListOf<String>()
        ((if(target.startsWith('/')) "" else if(parent.isEmpty()) "" else "$parent/")+target.removePrefix("/")).split('/').forEach { segment ->
            when(segment) { "","." -> Unit;".." -> { require(parts.isNotEmpty());parts.removeAt(parts.lastIndex) };else -> parts+=segment }
        }
        return parts.joinToString("/")
    }
    private fun part(zip:ZipFile,path:String,limit:Int):ByteArray {
        val entry=zip.getEntry(path) ?: error("DOCX 구성 파일이 없습니다.")
        require(!entry.isDirectory && entry.size in 0..limit.toLong()) { "DOCX 구성 파일 크기를 확인해 주세요." }
        return zip.getInputStream(entry).use { it.readBytesLimited(limit) }
    }
    private fun xml(bytes:ByteArray):Element {
        val text=decode(bytes)
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(text)) { "외부 엔터티/DTD XML은 지원하지 않습니다." }
        // Streaming preflight bounds nesting before allocating a DOM or calling recursive extractors.
        // Android officially does not support JAXP FEATURE_SECURE_PROCESSING; do not assume it does.
        val stream = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
        stream.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External XML forbidden") }
        stream.contentHandler = object : DefaultHandler() {
            var depth = 0
            var elements = 0
            override fun startElement(uri:String?, localName:String?, qName:String?, attributes:org.xml.sax.Attributes?) {
                depth++; elements++
                if (depth > 128 || elements > 100_000) throw SAXException("DOCX XML resource limit")
            }
            override fun endElement(uri:String?, localName:String?, qName:String?) { depth-- }
        }
        stream.parse(InputSource(StringReader(text)))
        val factory=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true;isExpandEntityReferences=false }
        val builder=factory.newDocumentBuilder()
        builder.setEntityResolver { _,_ -> throw SAXException("External XML forbidden") }
        val root = builder.parse(InputSource(StringReader(text))).documentElement
        val pending = java.util.ArrayDeque<Pair<Element,Int>>()
        pending.add(root to 1)
        var elements = 0
        while (!pending.isEmpty()) {
            val (element, depth) = pending.removeLast()
            require(depth <= 128 && ++elements <= 100_000) { "DOCX XML resource limit" }
            val children = element.childNodes
            for (index in 0 until children.length) (children.item(index) as? Element)?.let { pending.add(it to depth + 1) }
        }
        return root
    }
}
private fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) { val count=read(buffer);if(count<0)break;require(out.size()+count<=limit) { "DOCX 압축 해제 한도 초과" };out.write(buffer,0,count) }
    return out.toByteArray()
}
private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
private fun Element.descendants(name:String):List<Element> { val nodes=getElementsByTagNameNS("*",name);return (0 until nodes.length).map { nodes.item(it) as Element } }
