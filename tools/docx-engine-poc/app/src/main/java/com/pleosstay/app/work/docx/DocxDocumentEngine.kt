package com.pleosstay.app.work.docx

import java.io.*
import java.util.UUID
import java.util.zip.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.*
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler

const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
data class DocxRun(val text:String,val font:String?,val size:Float?,val bold:Boolean,val italic:Boolean,val underline:Boolean,val color:String?)
data class DocxParagraph(val id:Int,val runs:List<DocxRun>,val alignment:String,val editable:Boolean) { val text get()=runs.joinToString(""){it.text} }
data class DocxTable(val id:Int,val rows:List<List<List<Int>>>)
data class DocxImage(val id:Int,val bytes:ByteArray,val width:Int,val height:Int)
data class DocxView(val paragraphs:List<DocxParagraph>,val tables:List<DocxTable>,val images:List<DocxImage>,val readOnlyReason:String?)
data class DocxStyle(val font:String?=null,val size:Float?=null,val bold:Boolean?=null,val italic:Boolean?=null,val underline:Boolean?=null,val color:String?=null)
interface DocxDocumentEngine {
 fun view():DocxView
 fun replaceText(paragraph:Int,start:Int,end:Int,text:String)
 fun format(paragraph:Int,start:Int,end:Int,style:DocxStyle)
 fun align(paragraph:Int,value:String)
 fun addParagraph(after:Int?,text:String="")
 fun deleteParagraph(id:Int)
 fun addTable(rows:Int,columns:Int)
 fun addRow(table:Int)
 fun addColumn(table:Int)
 fun shadeCell(table:Int,row:Int,column:Int,color:String)
 fun addImage(bytes:ByteArray,mime:String,width:Int,height:Int)
 fun replaceImage(id:Int,bytes:ByteArray,mime:String)
 fun resizeImage(id:Int,width:Int,height:Int)
 fun export():ByteArray
}

/** Edits a preserved OPC package, never reconstructs a source document from extracted text. */
class OoxmlDocxEngine private constructor(private val parts:LinkedHashMap<String,ByteArray>) : DocxDocumentEngine {
 private val document=xml(parts.getValue("word/document.xml"))
 private val rels=xml(parts["word/_rels/document.xml.rels"] ?: "<Relationships xmlns=\"$REL\"/>".toByteArray())
 private val types=xml(parts.getValue("[Content_Types].xml"))
 private var mainDirty=false;private var relDirty=false;private var typesDirty=false
 private val body=document.documentElement.children().first{it.namespaceURI==W&&it.localName=="body"}
 private fun paragraphs()=body.all(W,"p")
 private fun tables()=body.all(W,"tbl")
 private fun images()=body.all(A,"blip")
 private val reason:String? = when {
  parts.keys.any{it.startsWith("_xmlsignatures/")||it.endsWith("vbaProject.bin")} -> "서명·매크로 문서는 편집하지 않습니다."
  parts["word/settings.xml"]?.let{xml(it).documentElement.all(W,"documentProtection").any{p->p.getAttributeNS(W,"enforcement") !in listOf("0","false","off")}}==true -> "보호된 문서입니다."
  body.all(W,"ins").isNotEmpty()||body.all(W,"del").isNotEmpty()||body.all(W,"sdt").isNotEmpty()||body.all(W,"fldChar").isNotEmpty()||body.all(W,"fldSimple").isNotEmpty()||body.all("http://schemas.openxmlformats.org/officeDocument/2006/math","oMath").isNotEmpty() -> "변경 추적·콘텐츠 컨트롤·필드·수식은 읽기 전용입니다."
  body.all(W,"gridSpan").isNotEmpty()||body.all(W,"vMerge").isNotEmpty() -> "병합 셀을 포함한 문서는 읽기 전용입니다."
  body.children().any{it.namespaceURI!=W||it.localName !in setOf("p","tbl","sectPr")} -> "지원하지 않는 본문 구조입니다."
  else -> null
 }
 private fun writable(){check(reason==null){reason ?: "READ_ONLY"}}
 private fun p(id:Int)=paragraphs().getOrElse(id){error("PARAGRAPH_MISSING")}
 private fun t(id:Int)=tables().getOrElse(id){error("TABLE_MISSING")}
 private fun editable(p:Element)=p.children().all{it.namespaceURI==W&&it.localName in setOf("pPr","r")}&&p.children().filter{it.localName=="r"}.all{r->r.children().all{it.namespaceURI==W&&it.localName in setOf("rPr","t","tab","br","cr")}}
 private fun text(run:Element)=run.children().joinToString(""){when(it.localName){"t"->it.textContent;"tab"->"\t";"br","cr"->"\n";else->""}}
 private fun property(run:Element,name:String)=run.children().firstOrNull{it.localName=="rPr"}?.children()?.firstOrNull{it.localName==name}
 override fun view():DocxView {
  val ps=paragraphs()
  return DocxView(ps.mapIndexed{i,e->DocxParagraph(i,e.children().filter{it.localName=="r"}.map{r->
   fun on(n:String)=property(r,n)?.let{it.getAttributeNS(W,"val") !in listOf("false","0","off","none")} ?: false
   DocxRun(text(r),property(r,"rFonts")?.getAttributeNS(W,"eastAsia")?.ifBlank{property(r,"rFonts")?.getAttributeNS(W,"ascii")},property(r,"sz")?.getAttributeNS(W,"val")?.toFloatOrNull()?.div(2),on("b"),on("i"),on("u"),property(r,"color")?.getAttributeNS(W,"val"))
  },e.children().firstOrNull{it.localName=="pPr"}?.children()?.firstOrNull{it.localName=="jc"}?.getAttributeNS(W,"val") ?: "left",editable(e))},
  tables().mapIndexed{i,table->DocxTable(i,table.children().filter{it.localName=="tr"}.map{row->row.children().filter{it.localName=="tc"}.map{cell->cell.all(W,"p").map{ps.indexOf(it)}}})},
  images().mapIndexedNotNull{i,blip->
   val rel=rels.documentElement.children().firstOrNull{it.getAttribute("Id")==blip.getAttributeNS(R,"embed")} ?: return@mapIndexedNotNull null
   if(rel.getAttribute("TargetMode")=="External")return@mapIndexedNotNull null
   val path=resolve("word",rel.getAttribute("Target"));val bytes=parts[path] ?: return@mapIndexedNotNull null
   if(!(path.endsWith(".png",true)||path.endsWith(".jpg",true)||path.endsWith(".jpeg",true)))return@mapIndexedNotNull null
   val drawing=ancestor(blip,"drawing");val extent=drawing?.all(WP,"extent")?.firstOrNull()
   DocxImage(i,bytes,(extent?.getAttribute("cx")?.toLongOrNull()?.div(9525) ?: 240).toInt(),(extent?.getAttribute("cy")?.toLongOrNull()?.div(9525) ?: 160).toInt())
  },reason)
 }
 private fun run(template:Element?,value:String):Element {
  val result=template?.cloneNode(true) as? Element ?: document.createElementNS(W,"w:r")
  result.children().filter{it.localName!="rPr"}.forEach{result.removeChild(it)}
  val chunks=value.split('\n');chunks.forEachIndexed{i,line->if(i>0)result.appendChild(document.createElementNS(W,"w:br"));line.split('\t').forEachIndexed{j,word->
   if(j>0)result.appendChild(document.createElementNS(W,"w:tab"))
   val text=document.createElementNS(W,"w:t");text.setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");text.textContent=word;result.appendChild(text)
  }}
  return result
 }
 private fun rewrite(id:Int,start:Int,end:Int,replacement:String?,style:DocxStyle?) {
  writable();val para=p(id);check(editable(para)){"이 문단은 인라인 개체·링크·책갈피를 포함해 직접 편집할 수 없습니다."}
  val runs=para.children().filter{it.localName=="r"};val length=runs.sumOf{text(it).length};require(start in 0..length&&end in start..length)
  val output=mutableListOf<Element>();var offset=0;var inserted=false
  for(r in runs){val value=text(r);val until=offset+value.length
   if(replacement!=null){
    if(until<=start){output+=r.cloneNode(true) as Element}
    else if(offset>=end){if(!inserted){output+=run(r,replacement);inserted=true};output+=r.cloneNode(true) as Element}
    else {if(start>offset)output+=run(r,value.substring(0,start-offset));if(!inserted){output+=run(r,replacement);inserted=true};if(end<until)output+=run(r,value.substring(end-offset))}
   }else{
    if(until<=start||offset>=end)output+=r.cloneNode(true) as Element
    else {val a=(start-offset).coerceAtLeast(0);val b=(end-offset).coerceAtMost(value.length)
     if(a>0)output+=run(r,value.substring(0,a));val mid=run(r,value.substring(a,b));applyStyle(mid,checkNotNull(style));output+=mid;if(b<value.length)output+=run(r,value.substring(b))}
   };offset=until
  }
  if(replacement!=null&&!inserted)output+=run(runs.lastOrNull(),replacement)
  runs.forEach{para.removeChild(it)};output.forEach(para::appendChild);mainDirty=true
 }
 override fun replaceText(paragraph:Int,start:Int,end:Int,text:String){require(text.length<=100000);rewrite(paragraph,start,end,text,null)}
 override fun format(paragraph:Int,start:Int,end:Int,style:DocxStyle){rewrite(paragraph,start,end,null,style)}
 private fun prop(parent:Element,name:String):Element=parent.children().firstOrNull{it.namespaceURI==W&&it.localName==name} ?: document.createElementNS(W,"w:$name").also{parent.appendChild(it)}
 private fun applyStyle(run:Element,s:DocxStyle){val props=prop(run,"rPr");run.insertBefore(props,run.firstChild)
  fun value(name:String,v:String){prop(props,name).setAttributeNS(W,"w:val",v)}
  s.font?.let{require(it.length in 1..100);val f=prop(props,"rFonts");listOf("ascii","hAnsi","eastAsia","cs").forEach{n->f.setAttributeNS(W,"w:$n",it)}}
  s.size?.let{require(it.isFinite()&&it in 8f..72f);value("sz",(it*2).toInt().toString());value("szCs",(it*2).toInt().toString())}
  s.bold?.let{value("b",if(it)"1" else "0")};s.italic?.let{value("i",if(it)"1" else "0")};s.underline?.let{value("u",if(it)"single" else "none")}
  s.color?.let{require(it.matches(Regex("[0-9A-Fa-f]{6}")));value("color",it)}
 }
 override fun align(paragraph:Int,value:String){writable();require(value in setOf("left","center","right"));val para=p(paragraph);val props=prop(para,"pPr");para.insertBefore(props,para.firstChild);prop(props,"jc").setAttributeNS(W,"w:val",value);mainDirty=true}
 override fun addParagraph(after:Int?,text:String){writable();val para=document.createElementNS(W,"w:p");para.appendChild(run(null,text));if(after!=null){val old=p(after);old.parentNode.insertBefore(para,old.nextSibling)}else body.insertBefore(para,body.children().firstOrNull{it.localName=="sectPr"});mainDirty=true}
 override fun deleteParagraph(id:Int){writable();val para=p(id);check(para.parentNode==body){"셀 안의 마지막 문단은 삭제할 수 없습니다."};check(body.children().count{it.localName=="p"}>1);para.parentNode.removeChild(para);mainDirty=true}
 private fun cell():Element=document.createElementNS(W,"w:tc").also{it.appendChild(document.createElementNS(W,"w:p").also{p->p.appendChild(run(null,""))})}
 override fun addTable(rows:Int,columns:Int){writable();require(rows in 1..20&&columns in 1..10);val table=document.createElementNS(W,"w:tbl");val props=prop(table,"tblPr");val borders=prop(props,"tblBorders");listOf("top","left","bottom","right","insideH","insideV").forEach{prop(borders,it).apply{setAttributeNS(W,"w:val","single");setAttributeNS(W,"w:sz","4")}}
  val grid=prop(table,"tblGrid");repeat(columns){propNew(grid,"gridCol").setAttributeNS(W,"w:w","1800")};repeat(rows){val row=propNew(table,"tr");repeat(columns){row.appendChild(cell())}};body.insertBefore(table,body.children().firstOrNull{it.localName=="sectPr"});mainDirty=true}
 private fun propNew(e:Element,n:String)=document.createElementNS(W,"w:$n").also(e::appendChild)
 override fun addRow(table:Int){writable();val target=t(table);val rows=target.children().filter{it.localName=="tr"};require(rows.size<100);val row=propNew(target,"tr");repeat(rows.first().children().count{it.localName=="tc"}){row.appendChild(cell())};mainDirty=true}
 override fun addColumn(table:Int){writable();val target=t(table);val rows=target.children().filter{it.localName=="tr"};require(rows.first().children().count{it.localName=="tc"}<20);rows.forEach{it.appendChild(cell())};propNew(prop(target,"tblGrid"),"gridCol").setAttributeNS(W,"w:w","1800");mainDirty=true}
 override fun shadeCell(table:Int,row:Int,column:Int,color:String){writable();require(color.matches(Regex("[0-9A-Fa-f]{6}")));val c=t(table).children().filter{it.localName=="tr"}[row].children().filter{it.localName=="tc"}[column];prop(prop(c,"tcPr"),"shd").setAttributeNS(W,"w:fill",color);mainDirty=true}
 private fun media(bytes:ByteArray,mime:String):String {
  require(bytes.size in 1..4*1024*1024);val ext=when(mime){"image/png"->{require(bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)));"png"};"image/jpeg"->{require(bytes.size>3&&bytes[0]==(-1).toByte()&&bytes[1]==(-40).toByte());"jpg"};else->error("PNG/JPEG만 지원합니다.")}
  val name="stay-${UUID.randomUUID()}.$ext";parts["word/media/$name"]=bytes.copyOf();val id="rStay${UUID.randomUUID().toString().replace("-","")}"
  rels.documentElement.appendChild(rels.createElementNS(REL,"Relationship").apply{setAttribute("Id",id);setAttribute("Type","$R/image");setAttribute("Target","media/$name")});relDirty=true
  if(types.documentElement.children().none{it.localName=="Default"&&it.getAttribute("Extension")==ext}){types.documentElement.appendChild(types.createElementNS(types.documentElement.namespaceURI,"Default").apply{setAttribute("Extension",ext);setAttribute("ContentType",mime)});typesDirty=true};return id
 }
 override fun addImage(bytes:ByteArray,mime:String,width:Int,height:Int){writable();require(width in 16..1600&&height in 16..1600);val id=media(bytes,mime);val n=(body.all(WP,"docPr").mapNotNull{it.getAttribute("id").toLongOrNull()}.maxOrNull() ?: 0)+1
  val fragment=xml(("<w:p xmlns:w=\"$W\" xmlns:r=\"$R\" xmlns:a=\"$A\" xmlns:wp=\"$WP\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:r><w:drawing><wp:inline><wp:extent cx=\"${width*9525L}\" cy=\"${height*9525L}\"/><wp:docPr id=\"$n\" name=\"Image$n\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"$n\" name=\"Image$n\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"$id\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"${width*9525L}\" cy=\"${height*9525L}\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>").toByteArray())
  body.insertBefore(document.importNode(fragment.documentElement,true),body.children().firstOrNull{it.localName=="sectPr"});mainDirty=true
 }
 override fun replaceImage(id:Int,bytes:ByteArray,mime:String){writable();images()[id].setAttributeNS(R,"r:embed",media(bytes,mime));mainDirty=true}
 override fun resizeImage(id:Int,width:Int,height:Int){writable();require(width in 16..1600&&height in 16..1600);val drawing=checkNotNull(ancestor(images()[id],"drawing"));(drawing.all(WP,"extent")+drawing.all(A,"ext")).forEach{it.setAttribute("cx",(width*9525L).toString());it.setAttribute("cy",(height*9525L).toString())};mainDirty=true}
 override fun export():ByteArray {
  val out=LinkedHashMap(parts);if(mainDirty)out["word/document.xml"]=encode(document);if(relDirty)out["word/_rels/document.xml.rels"]=encode(rels);if(typesDirty)out["[Content_Types].xml"]=encode(types)
  val bytes=ByteArrayOutputStream();ZipOutputStream(bytes).use{zip->out.forEach{(name,data)->zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry()}};require(bytes.size()<=20*1024*1024);return bytes.toByteArray()
 }
 companion object {
  fun open(bytes:ByteArray):OoxmlDocxEngine {require(bytes.size in 1..20*1024*1024);val entries=linkedMapOf<String,ByteArray>();var total=0L
   ZipInputStream(ByteArrayInputStream(bytes)).use{zip->while(true){val e=zip.nextEntry ?: break;require(entries.size<1500);require(!e.isDirectory&&!e.name.startsWith('/')&&!e.name.contains('\\')&&e.name.split('/').none{it==".."}&&!entries.containsKey(e.name));val data=ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=zip.read(buf);if(n<0)break;total+=n;require(total<=48L*1024*1024&&data.size()+n<=20*1024*1024);data.write(buf,0,n)};entries[e.name]=data.toByteArray()}}
   require(entries.keys.containsAll(listOf("[Content_Types].xml","_rels/.rels","word/document.xml")))
   entries.filterKeys{it.endsWith(".xml")||it.endsWith(".rels")}.values.forEach{xml(it)}
   val root=xml(entries.getValue("_rels/.rels")).documentElement;require(root.children().any{it.getAttribute("Type")=="$R/officeDocument"&&resolve("",it.getAttribute("Target"))=="word/document.xml"&&it.getAttribute("TargetMode")!="External"})
   return OoxmlDocxEngine(entries)
  }
  fun blank():OoxmlDocxEngine=OoxmlDocxEngine(linkedMapOf("[Content_Types].xml" to "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>".toByteArray(),"_rels/.rels" to "<Relationships xmlns=\"$REL\"><Relationship Id=\"rId1\" Type=\"$R/officeDocument\" Target=\"word/document.xml\"/></Relationships>".toByteArray(),"word/document.xml" to "<w:document xmlns:w=\"$W\" xmlns:r=\"$R\"><w:body><w:p><w:r><w:t/></w:r></w:p><w:sectPr/></w:body></w:document>".toByteArray()))
 }
}
private fun Element.children()=(0 until childNodes.length).mapNotNull{childNodes.item(it) as? Element}
private fun Element.all(ns:String,name:String):List<Element> =getElementsByTagNameNS(ns,name).let{nodes->(0 until nodes.length).map{nodes.item(it) as Element}}
private fun ancestor(node:Node,name:String):Element? {var p=node.parentNode;while(p!=null){if(p is Element&&p.namespaceURI==W&&p.localName==name)return p;p=p.parentNode};return null}
private fun resolve(parent:String,target:String):String {require(target.isNotBlank()&&!target.contains(':')&&!target.contains('%')&&!target.contains('\\'));val parts=mutableListOf<String>();(if(target.startsWith('/'))target.drop(1) else if(parent.isBlank())target else "$parent/$target").split('/').forEach{when(it){"","."->Unit;".."->{require(parts.isNotEmpty());parts.removeAt(parts.lastIndex)};else->parts+=it}};return parts.joinToString("/")}
private fun xml(bytes:ByteArray):Document {require(bytes.size<=8*1024*1024);val text=bytes.toString(Charsets.UTF_8);require(!text.contains('\u0000')&&!Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(text));val input=InputSource(StringReader(text));val reader=SAXParserFactory.newInstance().apply{isNamespaceAware=true}.newSAXParser().xmlReader
 reader.entityResolver=EntityResolver{_,_->throw SAXException("External XML forbidden")};reader.contentHandler=object:DefaultHandler(){var depth=0;var count=0;override fun startElement(u:String?,l:String?,q:String?,a:Attributes?){if(++depth>128||++count>100000)throw SAXException("XML resource limit")};override fun endElement(u:String?,l:String?,q:String?){depth--}};reader.parse(input)
 val builder=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;isExpandEntityReferences=false}.newDocumentBuilder();builder.setEntityResolver{_,_->throw SAXException("External XML forbidden")};return builder.parse(InputSource(StringReader(text)))
}
private fun encode(doc:Document)=ByteArrayOutputStream().also{TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc),StreamResult(it))}.toByteArray()
