package com.pleosstay.app.work

import com.pleosstay.app.documents.DocumentRef
import java.io.*

data class DocxDraftInfo(val id:String,val owner:String,val connection:String,val name:String,val updatedAt:Long,val source:DocumentRef?=null)
class DocxDraft(val info:DocxDraftInfo,val bytes:ByteArray)
internal object DocxDraftCodec {
 fun encode(d:DocxDraft):ByteArray=ByteArrayOutputStream().also{b->DataOutputStream(b).use{o->
  o.writeInt(1);with(d.info){listOf(id,owner,connection,name).forEach(o::writeUTF);o.writeLong(updatedAt);o.writeBoolean(source!=null);source?.let{r->listOf(r.userScope,r.connectionId,r.fileId,r.versionId,r.name,r.mimeType,r.sha256).forEach(o::writeUTF);o.writeLong(r.sizeBytes);o.writeInt(r.versionNo)}}
  require(d.bytes.size in 1..WorkDraftRepository.MAX_SIZE.toInt());o.writeInt(d.bytes.size);o.write(d.bytes)
 }}.toByteArray()
 fun decode(bytes:ByteArray):DocxDraft=DataInputStream(ByteArrayInputStream(bytes)).use{i->
  require(i.readInt()==1);val id=i.readUTF();val owner=i.readUTF();val connection=i.readUTF();val name=i.readUTF();val date=i.readLong()
  val source=if(i.readBoolean())DocumentRef(i.readUTF(),i.readUTF(),i.readUTF(),i.readUTF(),i.readUTF(),i.readUTF(),i.readUTF(),i.readLong(),i.readInt())else null
  val size=i.readInt();require(size in 1..WorkDraftRepository.MAX_SIZE.toInt()&&i.available()==size);DocxDraft(DocxDraftInfo(id,owner,connection,name,date,source),ByteArray(size).also(i::readFully))
 }
}
