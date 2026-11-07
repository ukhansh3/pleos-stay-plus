package com.pleosstay.app.work

import com.pleosstay.app.work.docx.*
import java.io.*
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest

class DocxEngineTest {
 @Test fun missingRectangularTableGridIsCompletedWithoutInventingColumnWidths(){val e=OoxmlDocxEngine.blank();e.addTable(2,2);val parts=entries(e.export()).toMutableMap();parts["word/document.xml"]=parts.getValue("word/document.xml").toString(Charsets.UTF_8).replace(Regex("<w:tblGrid[^>]*>.*?</w:tblGrid>"),"").toByteArray();val imported=OoxmlDocxEngine.open(zip(parts));assertNull(imported.view().readOnlyReason);imported.replaceText(0,0,0,"수정");val xml=javax.xml.parsers.DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(ByteArrayInputStream(entries(imported.export()).getValue("word/document.xml")));val columns=xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","gridCol");assertEquals(2,columns.length);repeat(columns.length){assertFalse((columns.item(it) as org.w3c.dom.Element).hasAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","w"))};val withColumn=OoxmlDocxEngine.open(zip(parts));withColumn.addColumn(0);assertEquals(3,OoxmlDocxEngine.open(withColumn.export()).view().tables.first().rows.first().size)}
 @Test fun cellPropertiesPrecedeCellContent(){val e=OoxmlDocxEngine.blank();e.addTable(1,1);e.shadeCell(0,0,0,"AAFFAA");val xml=javax.xml.parsers.DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true}.newDocumentBuilder().parse(ByteArrayInputStream(entries(e.export()).getValue("word/document.xml")));val cell=xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","tc").item(0);val elements=(0 until cell.childNodes.length).map{cell.childNodes.item(it)}.filterIsInstance<org.w3c.dom.Element>();assertEquals("tcPr",elements.first().localName);assertEquals("p",elements.last().localName)}
 @Test fun emptyParagraphFormattingIsInheritedByTypedText(){val e=OoxmlDocxEngine.blank();e.format(0,0,0,DocxStyle(bold=true,font="Noto Sans CJK KR",size=18f));e.replaceText(0,0,0,"한글");val run=OoxmlDocxEngine.open(e.export()).view().paragraphs.first().runs.first{it.text.isNotEmpty()};assertTrue(run.bold);assertEquals(18f,run.size)}
 @Test fun invalidXmlTextCannotReplaceLastValidDocument(){val e=OoxmlDocxEngine.blank();e.replaceText(0,0,0,"한글 😀");val before=e.export();assertThrows(Exception::class.java){e.replaceText(0,0,1,"\u0001")};assertArrayEquals(entries(before)["word/document.xml"],entries(e.export())["word/document.xml"]);assertEquals("한글 😀",OoxmlDocxEngine.open(e.export()).view().paragraphs.first().text)}
 private fun entries(bytes:ByteArray):Map<String,ByteArray> {val map=linkedMapOf<String,ByteArray>();ZipInputStream(ByteArrayInputStream(bytes)).use{z->while(true){val e=z.nextEntry?:break;map[e.name]=z.readBytes()}};return map}
 private fun zip(parts:Map<String,ByteArray>)=ByteArrayOutputStream().also{b->ZipOutputStream(b).use{z->parts.forEach{(k,v)->z.putNextEntry(ZipEntry(k));z.write(v);z.closeEntry()}}}.toByteArray()
 @Test fun rangeEditPreservesNeighborFormattingAndUntouchedPackageParts(){
  val e=OoxmlDocxEngine.blank();e.replaceText(0,0,0,"가나다라마");e.format(0,0,2,DocxStyle(bold=true,font="Noto Sans CJK KR",size=18f));e.addParagraph(null,"보존")
  val original=entries(e.export()).toMutableMap();original["word/styles.xml"]="<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:style w:styleId=\"Keep\"/></w:styles>".toByteArray();original["customXml/preserve.xml"]="<keep attr=\"same\">data</keep>".toByteArray()
  val edit=OoxmlDocxEngine.open(zip(original));edit.replaceText(0,2,4,"한국어");edit.format(0,2,5,DocxStyle(color="112233",italic=true,underline=true));edit.align(0,"right")
  val bytes=edit.export();val reopened=OoxmlDocxEngine.open(bytes).view();assertEquals("가나한국어마",reopened.paragraphs[0].text);assertTrue(reopened.paragraphs[0].runs.first{it.text.isNotEmpty()}.bold);assertEquals("right",reopened.paragraphs[0].alignment);assertEquals("보존",reopened.paragraphs[1].text)
  val after=entries(bytes);assertArrayEquals(original["word/styles.xml"],after["word/styles.xml"]);assertArrayEquals(original["customXml/preserve.xml"],after["customXml/preserve.xml"])
 }
 @Test fun tableCellRowsColumnsAndCellStyleSurviveReopen(){val e=OoxmlDocxEngine.blank();e.addTable(2,2);val cell=e.view().tables[0].rows[0][0][0];e.replaceText(cell,0,0,"한글 셀");e.addRow(0);e.addColumn(0);e.shadeCell(0,0,0,"AAFFAA");val reopened=OoxmlDocxEngine.open(e.export());assertEquals(3,reopened.view().tables[0].rows.size);assertEquals(3,reopened.view().tables[0].rows[0].size);assertEquals("한글 셀",reopened.view().paragraphs[cell].text);assertTrue(entries(e.export()).getValue("word/document.xml").toString(Charsets.UTF_8).contains("AAFFAA"))}
 @Test fun protectedDocumentIsReadOnlyWithoutDroppingContent(){
  val original=entries(OoxmlDocxEngine.blank().export()).toMutableMap();original["word/settings.xml"]="<w:settings xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:documentProtection w:enforcement=\"1\"/></w:settings>".toByteArray();val source=zip(original);val e=OoxmlDocxEngine.open(source);assertNotNull(e.view().readOnlyReason);assertThrows(Exception::class.java){e.addParagraph(null,"no")};assertArrayEquals(original["word/document.xml"],entries(e.export())["word/document.xml"])
 }
 @Test fun rejectsDtdDuplicatePartsAndPathTraversal(){val original=entries(OoxmlDocxEngine.blank().export()).toMutableMap();original["word/document.xml"]="<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///secret'>]><x>&y;</x>".toByteArray();assertThrows(Exception::class.java){OoxmlDocxEngine.open(zip(original))};original["../escape"]="x".toByteArray();assertThrows(Exception::class.java){OoxmlDocxEngine.open(zip(original))}}
 @Test fun localDraftsShareEncryptionPortWithoutBreakingRemoteLibraryOrUserIsolation()=runTest{
  val ref=com.pleosstay.app.documents.DocumentRef("owner","link","file","v1","source.docx",DOCX_MIME,"0".repeat(64),1,1);val gate=WorkTestGateway(ref);val store=MemoryWorkRecords();val repo=WorkDraftRepository(store,gate)
  val d=repo.createDocx("한글",OoxmlDocxEngine.blank().export());assertTrue(d.info.name.endsWith(".docx"));assertEquals(1,repo.listDocx().size);assertTrue(repo.summaries().isEmpty())
  val e=OoxmlDocxEngine.open(d.bytes);e.replaceText(0,0,0,"복구");repo.retainDocx(DocxDraft(d.info,e.export()));assertEquals("복구",OoxmlDocxEngine.open(WorkDraftRepository(store,gate).loadDocx(d.info).bytes).view().paragraphs.first().text)
  gate.access.value=gate.access.value.copy(userScope="other");assertTrue(repo.listDocx().isEmpty());try{repo.loadDocx(d.info);fail()}catch(_:IllegalStateException){}
  gate.access.value=gate.access.value.copy(userScope="owner");repo.deleteDocx(d.info);assertTrue(repo.listDocx().isEmpty())
 }
}
