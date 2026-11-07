package com.pleosstay.docxprobe
import android.app.Instrumentation
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import com.pleosstay.app.work.docx.*
import java.io.*
import java.util.zip.*

class OoxmlProbe:Instrumentation(){
 override fun onCreate(args:Bundle?){super.onCreate(args);start()}
 override fun onStart(){val result=Bundle();try{
  val start=android.os.SystemClock.elapsedRealtime();val heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()
  fun image(color:Int):ByteArray {val b=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);b.eraseColor(color);return ByteArrayOutputStream().also{b.compress(Bitmap.CompressFormat.PNG,100,it);b.recycle()}.toByteArray()}
  val e=OoxmlDocxEngine.blank();e.replaceText(0,0,0,"한글 원본 문서");e.format(0,0,2,DocxStyle(font="Noto Sans CJK KR",size=18f,bold=true));e.addParagraph(null,"보존 문단");e.addTable(2,2);e.addImage(image(Color.RED),"image/png",160,100)
  val original=e.export();val originalFile=File(targetContext.filesDir,"original.docx");originalFile.writeBytes(original)
  val edit=OoxmlDocxEngine.open(original);edit.replaceText(0,0,2,"한국어");edit.format(0,0,3,DocxStyle(font="Noto Serif CJK KR",size=22f,color="1234AB",italic=true,underline=true));edit.align(0,"center")
  val cell=edit.view().tables.first().rows.first().first().first();edit.replaceText(cell,0,0,"셀 수정");edit.addRow(0);edit.addColumn(0);edit.shadeCell(0,0,0,"DDEEFF");edit.replaceImage(0,image(Color.BLUE),"image/png");edit.resizeImage(0,240,140);edit.addImage(image(Color.GREEN),"image/png",120,80)
  val bytes=edit.export();val final=OoxmlDocxEngine.open(bytes);val v=final.view();check(v.readOnlyReason==null);check(v.paragraphs.first().text=="한국어 원본 문서");check(v.paragraphs.any{it.text=="보존 문단"});check(v.paragraphs.first().runs.first{it.text.isNotEmpty()}.font=="Noto Serif CJK KR");check(v.tables.first().rows.size==3&&v.tables.first().rows.first().size==3);check(v.paragraphs.any{it.text=="셀 수정"});check(v.images.size==2);check(v.images.first().width==240)
  fun entries(data:ByteArray):Map<String,ByteArray>{val out=linkedMapOf<String,ByteArray>();ZipInputStream(ByteArrayInputStream(data)).use{z->while(true){val p=z.nextEntry?:break;out[p.name]=z.readBytes()}};return out}
  val before=entries(original);val after=entries(bytes);check(before["_rels/.rels"]!!.contentEquals(after["_rels/.rels"]!!));before.filterKeys{it.startsWith("word/media/")}.forEach{(k,b)->check(b.contentEquals(after[k]!!))}
  File(targetContext.filesDir,"edited.docx").writeBytes(bytes)
  result.putString("ooxml","PASS_CREATE_EDIT_KOREAN_STYLE_TABLE_IMAGE_PRESERVE_REOPEN");result.putLong("elapsedMillis",android.os.SystemClock.elapsedRealtime()-start);result.putLong("heapDeltaBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()-heap)
 }catch(t:Throwable){result.putString("ooxml","FAIL");result.putString("error",t.toString())};finish(0,result)}
}
