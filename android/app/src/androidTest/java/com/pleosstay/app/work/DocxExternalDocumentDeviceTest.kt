package com.pleosstay.app.work
import com.pleosstay.app.work.docx.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.util.zip.*

class DocxExternalDocumentDeviceTest {
 @Test fun poiAuthoredExistingDocumentRetainsAllUntouchedParts(){
  val inst=InstrumentationRegistry.getInstrumentation();val original=inst.context.assets.open("work/poi-generated-fixture.docx").use{it.readBytes()}
  val engine=OoxmlDocxEngine.open(original);assertNull(engine.view().readOnlyReason)
  engine.replaceText(0,0,2,"한국어");engine.format(0,0,3,DocxStyle(font="Noto Sans CJK KR",size=20f,color="AB1234",bold=true,italic=true,underline=true));engine.align(0,"center")
  val cell=engine.view().tables.first().rows.first().first().first();engine.replaceText(cell,0,engine.view().paragraphs.first{it.id==cell}.text.length,"기존 표 수정")
  engine.resizeImage(0,260,180)
  val bytes=engine.export();val reopened=OoxmlDocxEngine.open(bytes).view()
  assertEquals("한국어 원본",reopened.paragraphs.first().text);assertTrue(reopened.paragraphs.any{it.text=="보존 문단"});assertEquals("기존 표 수정",reopened.paragraphs.first{it.id==cell}.text);assertEquals(260,reopened.images.first().width)
  fun entries(b:ByteArray):Map<String,ByteArray>{val map=mutableMapOf<String,ByteArray>();ZipInputStream(ByteArrayInputStream(b)).use{z->while(true){val e=z.nextEntry?:break;map[e.name]=z.readBytes()}};return map}
  val before=entries(original);val after=entries(bytes);before.filterKeys{it!="word/document.xml"}.forEach{(name,data)->assertArrayEquals("Untouched part: $name",data,after[name])}
  File(inst.targetContext.cacheDir,"docx-verification").apply{mkdirs()}.resolve("external-edited.docx").writeBytes(bytes)
 }
}
