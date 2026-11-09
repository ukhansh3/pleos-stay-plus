package com.pleosstay.app.work.docx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pleosstay.app.realtime.WorkExportCatalog
import com.pleosstay.app.realtime.WorkExportProfile
import com.pleosstay.app.work.DocxDraftInfo
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface DocxFileGateway {
 suspend fun read(uri:Uri,maxBytes:Int=20*1024*1024):ByteArray
 suspend fun name(uri:Uri):String
 suspend fun writeNew(uri:Uri,bytes:ByteArray,permitted:()->Boolean)
 suspend fun writePrivate(bytes:ByteArray,permitted:()->Boolean):Uri
 suspend fun writeDownload(name:String,bytes:ByteArray,permitted:()->Boolean):Uri
 suspend fun rememberDownload(uri: Uri, bytes: ByteArray, info: DocxDraftInfo) {}
 fun clearPrivateExports()
 fun hasSystemPicker():Boolean
}
class AndroidDocxFileGateway(context:Context):DocxFileGateway {
 private val app=context.applicationContext
 private val resolver=app.contentResolver
 private val directory=java.io.File(app.cacheDir,"work-docx-exports")
 private val generation=java.util.concurrent.atomic.AtomicLong()
 override suspend fun writeDownload(name:String,bytes:ByteArray,permitted:()->Boolean):Uri {
  check(android.os.Build.VERSION.SDK_INT>=29){"Downloads export requires API29"}
  var created:Uri?=null
  return try{withContext(Dispatchers.IO){
   // Keep the API guard in the coroutine containing the MediaStore access.
   if(android.os.Build.VERSION.SDK_INT<29) error("Downloads export requires API29")
   check(permitted());OoxmlDocxEngine.open(bytes)
   val leaf=name.substringAfterLast('/').substringAfterLast('\\').filterNot{it.isISOControl()}.take(80).trim().ifBlank{"문서.docx"}
   val filename=if(leaf.endsWith(".docx",true))leaf else "$leaf.docx"
   val values=android.content.ContentValues().apply{
    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,filename);put(android.provider.MediaStore.MediaColumns.MIME_TYPE,DOCX_MIME)
    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS+"/StayPlus")
    put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
   }
   val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("DOWNLOAD_CREATE_FAILED");created=uri
   writeNew(uri,bytes,permitted);check(permitted());currentCoroutineContext().ensureActive()
   check(resolver.update(uri,android.content.ContentValues().apply{put(android.provider.MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1)
   uri
  }}catch(t:Throwable){created?.let{try{resolver.delete(it,null,null)}catch(_:Exception){}};throw t}
 }
 override suspend fun rememberDownload(uri: Uri, bytes: ByteArray, info: DocxDraftInfo) {
  WorkExportCatalog(app).record(uri, bytes, WorkExportProfile(info.owner, info.connection), info.source)
 }
 override fun hasSystemPicker():Boolean {
  val intent=android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType(DOCX_MIME)
  val resolved=intent.resolveActivity(app.packageManager) ?: return false
  return !resolved.className.contains("DocumentsUIStub")
 }
 override fun clearPrivateExports(){generation.incrementAndGet();directory.listFiles()?.filter{it.isFile&&it.name.startsWith("stay-docx-")}?.forEach{file->
  try{val uri=androidx.core.content.FileProvider.getUriForFile(app,app.packageName+".work.docx.exports",file);app.revokeUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};file.delete()
 }}
 override suspend fun writePrivate(bytes:ByteArray,permitted:()->Boolean):Uri {
  val ticket=generation.get();var created:java.io.File?=null
  return try{withContext(Dispatchers.IO){
   check(permitted());OoxmlDocxEngine.open(bytes);directory.mkdirs()
   val file=java.io.File(directory,"stay-docx-${java.util.UUID.randomUUID()}.docx");created=file
   val atomic=android.util.AtomicFile(file);val out=atomic.startWrite()
   try{out.write(bytes);out.flush();check(ticket==generation.get()&&permitted());atomic.finishWrite(out)}catch(t:Throwable){atomic.failWrite(out);throw t}
   val verified=file.readBytes();try{OoxmlDocxEngine.open(verified);check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes),MessageDigest.getInstance("SHA-256").digest(verified)))}finally{verified.fill(0)}
   check(ticket==generation.get()&&permitted());androidx.core.content.FileProvider.getUriForFile(app,app.packageName+".work.docx.exports",file)
  }}catch(t:Throwable){created?.delete();throw t}
 }
 override suspend fun name(uri:Uri)=withContext(Dispatchers.IO){require(uri.scheme=="content");resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null} ?: "문서.docx"}
 override suspend fun read(uri:Uri,maxBytes:Int):ByteArray=withContext(Dispatchers.IO){
  require(uri.scheme=="content"&&maxBytes in 1..20*1024*1024)
  resolver.openInputStream(uri)!!.use{input->ByteArrayOutputStream().use{out->val b=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;require(out.size()+n<=maxBytes);out.write(b,0,n)};out.toByteArray().also{bytes->
   if(maxBytes==4*1024*1024){val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds);require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=16000000);val image=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply{inSampleSize=4});require(image!=null);image.recycle()}
  }}}
 }
 override suspend fun writeNew(uri:Uri,bytes:ByteArray,permitted:()->Boolean)=withContext(Dispatchers.IO){
  require(uri.scheme=="content");check(permitted());OoxmlDocxEngine.open(bytes)
  try{
   resolver.openOutputStream(uri,"w")!!.use{out->var offset=0;while(offset<bytes.size){currentCoroutineContext().ensureActive();check(permitted());val n=minOf(65536,bytes.size-offset);out.write(bytes,offset,n);offset+=n};out.flush()}
   check(permitted());val reopened=read(uri);try{check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes),MessageDigest.getInstance("SHA-256").digest(reopened)));OoxmlDocxEngine.open(reopened)}finally{reopened.fill(0)}
  }catch(t:Throwable){try{resolver.delete(uri,null,null)}catch(_:Exception){};throw t}
  Unit
 }
}
