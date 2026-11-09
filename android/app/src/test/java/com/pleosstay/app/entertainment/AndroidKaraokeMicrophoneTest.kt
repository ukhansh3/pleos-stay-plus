package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidKaraokeMicrophoneTest {
    private class Recorder : MicrophoneRecorder {
        override var initialized = true
        override var recording = false
        var starts = 0
        var stops = 0
        var releases = 0
        var startError: Exception? = null
        var readError: Exception? = null
        var stopError: Exception? = null
        var releaseError: Exception? = null
        var afterRead: () -> Unit = {}
        var readCode: Int? = null
        override fun start() { startError?.let { throw it }; starts++; recording = true }
        override fun read(buffer: ShortArray, offset: Int, size: Int): Int {
            readError?.let { throw it }
            readCode?.let { return it }
            for (i in offset until offset + size) buffer[i] = 16384
            afterRead()
            return size
        }
        override fun frameEndNanos(framesRead: Long) = 1_000_000_000L
        override fun stop() { stops++; recording = false; stopError?.let { throw it } }
        override fun release() { releases++; releaseError?.let { throw it } }
    }
    private class Platform : MicrophonePlatform {
        override val sampleRate = 16_000
        var allowed = true
        var creates = 0
        var createError: Exception? = null
        var supportError: Exception? = null
        var afterSupport: () -> Unit = {}
        var afterCreate: () -> Unit = {}
        var recorder = Recorder()
        override fun hasPermission() = allowed
        override fun support(): MicrophoneSupport {
            supportError?.let { throw it }
            afterSupport()
            return MicrophoneSupport(allowed, "test input", sampleRate, 1)
        }
        override fun createRecorder(): MicrophoneRecorder {
            creates++; createError?.let { throw it }; afterCreate(); return recorder
        }
    }
    private suspend fun permissionFailure(block: suspend () -> Unit): MicrophonePermissionException {
        try { block() } catch (error: MicrophonePermissionException) { return error }
        throw AssertionError("Expected explicit microphone permission failure")
    }
    @Test fun deniedCollectionCanRetryAfterExplicitPermissionGrant() = runTest {
        val platform = Platform().apply { allowed = false }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        permissionFailure { mic.frames().first() }
        assertEquals(0, platform.creates)
        platform.allowed = true
        assertNotNull(mic.frames().buffer(0).first())
        assertEquals(1, platform.creates)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun deniedPermissionNeverAllocatesRecorder() = runTest {
        val platform = Platform().apply { allowed = false }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        permissionFailure { mic.frames().first() }
        assertEquals(0, platform.creates)
    }
    @Test fun permissionRevokedAfterSupportDoesNotAllocate() = runTest {
        val platform = Platform().apply { afterSupport = { allowed = false } }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        // This fake reports the previous successful support result while permission is revoked.
        val stale = object : MicrophonePlatform by platform {
            override fun support(): MicrophoneSupport { platform.allowed = false; return MicrophoneSupport(true, "stale") }
        }
        permissionFailure { AndroidKaraokeMicrophone(stale, StandardTestDispatcher(testScheduler)).frames().first() }
        assertEquals(0, platform.creates)
    }
    @Test fun revocationAfterAllocationReleasesWithoutStarting() = runTest {
        val platform = Platform().apply { afterCreate = { allowed = false } }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        permissionFailure { mic.frames().first() }
        assertEquals(0, platform.recorder.starts)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun creationSecurityExceptionIsTypedAndRetryPossible() = runTest {
        val platform = Platform().apply { createError = SecurityException("permission revoked") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        val failure = permissionFailure { mic.frames().first() }
        assertTrue(failure.cause is SecurityException)
        platform.createError = null
        assertEquals(800, mic.frames().buffer(0).first().samples.size)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun startSecurityExceptionStillReleasesRecorder() = runTest {
        val platform = Platform().apply { recorder.startError = SecurityException("start denied") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        permissionFailure { mic.frames().first() }
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun readSecurityExceptionDoesNotEmitPcm() = runTest {
        val platform = Platform().apply { recorder.readError = SecurityException("read denied") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        var emitted = 0
        permissionFailure { mic.frames().collect { emitted++ } }
        assertEquals(0, emitted)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun revocationDuringReadDropsFrameAndReleases() = runTest {
        val platform = Platform().apply { recorder.afterRead = { allowed = false } }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        var emitted = 0
        permissionFailure { mic.frames().collect { emitted++ } }
        assertEquals(0, emitted)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun stopFailureCannotSkipReleaseOrPoisonNextCollection() = runTest {
        val platform = Platform().apply { recorder.stopError = SecurityException("stop denied") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        val frame = mic.frames().buffer(0).first()
        assertEquals(16_000, frame.sampleRate)
        assertTrue(frame.samples.all { it == 0.5f })
        assertEquals(950_000_000L, frame.capturedAtNanos)
        assertEquals(1, platform.recorder.releases)
        platform.recorder = Recorder()
        assertEquals(800, mic.frames().buffer(0).first().samples.size)
        assertEquals(1, platform.recorder.releases)
    }
    @Test fun releaseSecurityExceptionIsTypedAndStateResets() = runTest {
        val platform = Platform().apply { recorder.initialized = false; recorder.releaseError = SecurityException("release denied") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        permissionFailure { mic.frames().first() }
        platform.recorder = Recorder()
        assertNotNull(mic.frames().buffer(0).first())
    }
    @Test fun supportSecurityExceptionIsUnavailableOrTypedAtCapture() = runTest {
        val platform = Platform().apply { supportError = SecurityException("device access denied") }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        assertFalse(mic.support().available)
        permissionFailure { mic.frames().first() }
        assertEquals(0, platform.creates)
    }
    @Test fun negativeReadCodeStillCleansUpAndIsNotPermissionSuccess() = runTest {
        val platform = Platform().apply { recorder.readCode = -3 }
        val mic = AndroidKaraokeMicrophone(platform, StandardTestDispatcher(testScheduler))
        try { mic.frames().first(); fail("Expected read failure") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("-3")) }
        assertEquals(1, platform.recorder.releases)
    }
}
