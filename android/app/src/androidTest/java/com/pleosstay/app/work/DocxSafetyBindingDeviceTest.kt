package com.pleosstay.app.work
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.work.docx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/** Production WorkViewModel + actual Room/Keystore, test-only safety/auth adapters. */
class DocxSafetyBindingDeviceTest {
 @Test fun drivingUnknownPinAndForegroundLossMaskTheRealEditorBinding()=runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext
  val safety=object:WaitingSafetyGateway{override val state=MutableStateFlow(WaitingSafety(VehicleMotion.PARKED,false));override val drivingEvents=DrivingEventBuffer();override fun start(){};override fun stop(){};override fun close(){}}
  val gate=object:SharedDocumentGateway{
   override val access=MutableStateFlow(DocumentAccess(true,"binding-owner","binding-connection","READY"));override suspend fun refreshAccess()=access.value
   override suspend fun listFiles()=emptyList<DocumentFile>();override suspend fun listVersions(fileId:String)=emptyList<DocumentRef>();override suspend fun validate(ref:DocumentRef)=false
   override suspend fun downloadVerified(ref:DocumentRef)=DocumentOpenResult.Failed("test")
   override suspend fun uploadRevision(ref:DocumentRef,contentUri:Uri,idempotencyKey:String,progress:(Long,Long)->Unit):DocumentUploadResult=error("excluded")
   override fun clearTemporaryFiles(){};override fun close(){}
  }
  val db=Room.inMemoryDatabaseBuilder(app,WaitingDatabase::class.java).build()
  val journal=object:DrivingEndJournal{override fun read():PendingDrivingEnd?=null;override fun write(command:PendingDrivingEnd){};override fun clear(){}}
  val waiting=LocalWaitingRepository(RoomWaitingStore(db),journal,{safety.state.value})
  val repo=WorkDraftRepository(AndroidEncryptedDocumentStore(app,"docx-binding-${UUID.randomUUID()}"),gate)
  lateinit var model:WorkViewModel;var created=false;var draft:DocxDraftInfo?=null
  fun main(block:()->Unit)=inst.runOnMainSync(block)
  suspend fun until(block:()->Boolean){withTimeout(20000){while(!block())delay(20)}}
  try{
   waiting.restore();main{model=WorkViewModel(gate,repo,ModeStartManager(waiting,safety),safety,docxFiles=AndroidDocxFileGateway(app));created=true;model.setForeground(true)}
   until{model.state.value.allowed};main{model.start()};until{model.state.value.session!=null}
   main{model.docx.newDocument()};until{model.docx.state.value.view!=null&&!model.docx.state.value.busy};draft=model.docx.state.value.draft!!
   for(motion in listOf(VehicleMotion.DRIVING,VehicleMotion.UNKNOWN)){
    main{safety.state.value=WaitingSafety(motion,true)};until{model.docx.state.value.view==null};assertFalse(model.state.value.allowed)
    main{safety.state.value=WaitingSafety(VehicleMotion.PARKED,false)};until{model.state.value.allowed};main{model.docx.restore(draft!!)};until{model.docx.state.value.view!=null&&!model.docx.state.value.busy}
   }
   main{gate.access.value=DocumentAccess(false,reason="PIN_LOCKED")};until{model.docx.state.value.view==null}
   main{gate.access.value=DocumentAccess(true,"binding-owner","binding-connection","READY")};until{model.state.value.allowed};main{model.docx.restore(draft!!)};until{model.docx.state.value.view!=null&&!model.docx.state.value.busy}
   main{model.setForeground(false)};until{model.docx.state.value.view==null};assertFalse(model.state.value.allowed)
   assertEquals(draft!!.id,repo.loadDocx(draft!!).info.id)
  }finally{if(created)main{model.close()};draft?.let{repo.deleteDocx(it)};db.close()}
 }
}
