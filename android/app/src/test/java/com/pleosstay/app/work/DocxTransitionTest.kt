package com.pleosstay.app.work

import com.pleosstay.app.documents.DocumentRecordStore
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.work.docx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocxTransitionTest {
    private class Store:DocumentRecordStore {
        val memory=MemoryWorkRecords()
        var fail=false
        var gate:CompletableDeferred<Unit>?=null
        override suspend fun read(key:String)=memory.read(key)
        override suspend fun write(key:String,bytes:ByteArray) { gate?.await();check(!fail);memory.write(key,bytes) }
        override suspend fun delete(key:String)=memory.delete(key)
        override suspend fun keys()=memory.keys()
    }
    @Test fun drainingWaitsForAcceptedQueueAndRetainsActualDocx()=runTest {
        val ref=DocumentRef("owner","link","file","v1","fixture.docx",DOCX_MIME,"0".repeat(64),1,1)
        val gateway=WorkTestGateway(ref);val store=Store();val repo=WorkDraftRepository(store,gateway)
        val editor=DocxEditorController(repo,gateway,null,backgroundScope){true}
        editor.newDocument();val draft=editor.state.first { it.draft!=null && !it.busy }.draft!!
        val gate=CompletableDeferred<Unit>();store.gate=gate
        editor.setText(0,"first");editor.setText(0,"second");runCurrent()
        val flush=async { editor.flushForTransition() };runCurrent();assertFalse(flush.isCompleted)
        gate.complete(Unit);assertTrue(flush.await().success)
        val saved=repo.loadDocx(draft)
        try { assertEquals("second",OoxmlDocxEngine.open(saved.bytes).view().paragraphs.first().text) } finally { saved.bytes.fill(0) }
        editor.mask();assertNull(editor.state.value.view)
        editor.restore(draft);editor.state.first { it.view!=null && !it.busy };assertEquals("second",editor.state.value.view!!.paragraphs.first().text)
    }
    @Test fun expiredWorkCanRepairPrivateRetentionWithoutReopeningEditor()=runTest {
        val ref=DocumentRef("owner","link","file","v1","fixture.docx",DOCX_MIME,"0".repeat(64),1,1)
        val gateway=WorkTestGateway(ref);val store=Store();val repo=WorkDraftRepository(store,gateway)
        var editing=true
        val editor=DocxEditorController(repo,gateway,null,backgroundScope,retentionPermitted={true}){editing}
        editor.newDocument();val draft=editor.state.first { it.draft!=null && !it.busy }.draft!!
        store.fail=true;editor.setText(0,"retain after expiry");editor.state.first { !it.busy }
        assertFalse(editor.flushForTransition().success)
        editor.mask();editing=false;store.fail=false
        assertTrue(editor.retryRetention().success)
        assertNull(editor.state.value.view);assertTrue(editor.flushForTransition().success)
        val saved=repo.loadDocx(draft)
        try { assertEquals("retain after expiry",OoxmlDocxEngine.open(saved.bytes).view().paragraphs.first().text) } finally { saved.bytes.fill(0) }
    }
    @Test fun failedAutosaveBlocksExitAndEncryptedCandidateCanBeRetriedAfterMask()=runTest {
        val ref=DocumentRef("owner","link","file","v1","fixture.docx",DOCX_MIME,"0".repeat(64),1,1)
        val gateway=WorkTestGateway(ref);val store=Store();val repo=WorkDraftRepository(store,gateway)
        val editor=DocxEditorController(repo,gateway,null,backgroundScope){true}
        editor.newDocument();val draft=editor.state.first { it.draft!=null && !it.busy }.draft!!
        store.fail=true;editor.setText(0,"must survive");editor.state.first { !it.busy }
        assertFalse(editor.flushForTransition().success)
        editor.mask();assertNull(editor.state.value.view)
        store.fail=false
        gateway.access.value=com.pleosstay.app.documents.DocumentAccess(true,"other","other-link","READY")
        assertFalse(editor.retryRetention().success);assertNull(editor.state.value.view)
        gateway.access.value=com.pleosstay.app.documents.DocumentAccess(true,"owner","link","READY")
        assertTrue(editor.retryRetention().success)
        assertTrue(editor.flushForTransition().success)
        val saved=repo.loadDocx(draft)
        try { assertEquals("must survive",OoxmlDocxEngine.open(saved.bytes).view().paragraphs.first().text) } finally { saved.bytes.fill(0) }
    }
}
