# Activity transition internal implementation — 2026-11-08

## Scope and Git

- Start dev HEAD: 51eccaaa1d1a36ced3e98d090cc81cfd9ad12e7d.
- Fetched origin; clean worktree and no local-only commits. Fast-forwarded five commits to 79fbcc4d0e39de52119d4c3c529b6717c8803772.
- End HEAD: 79fbcc4d0e39de52119d4c3c529b6717c8803772. No new commit or push. Changes remain uncommitted.
- MainActivity, Premium layouts/theme/home/route/back bar/preview, Manifest, Gradle, Waiting, ModeStartManager, Realtime, Pickup, Backend and OOXML engine are unchanged from that HEAD.
- StudyScreen has only input-state bindings and Back lifecycle changes. It has no visual layout/button redesign.

## Required Work re-investigation before editing

1. Before this task, rememberProtectedWorkContent created WorkViewModel with remember(gateway).
2. Disposal closed that model and gateway, cancelling its timer scope.
3. Its foreground flag retained the timer only while the same model existed; route disposal still lost the live session.
4. Home/navigation confirmation existed, but Activity-owned timer ownership and awaitable activity exit did not.
5. Each successful DOCX edit cloned/serialized the candidate and retained the encrypted draft before publishing it. This behavior is preserved.
6. A synchronous exit/mask could invalidate pending editor jobs; there was no drain result for the caller.
7. New flushForTransition and stopForTransition wait for accepted jobs and report retention failures. Read-only network operations are cancelled separately.
8. dirty means unexported changes as well as edited state; encrypted private retention is distinct from public export. No transition performs public export.
9. 309681d changed Work presentation. The newer 79fbcc4 changed only shared back UI/preview/docs, not Work engine or storage. These UI changes are preserved.
10. Actual OOXML editing, encryption, undo/redo, export and existing navigation protection were already implemented; none was rebuilt or replaced.

## Internal contracts

- ActivityTransitionCoordinator: request, confirm, end, state, currentState. Pure preflight, one active adapter, single-use expiring confirmations bound to the source session, duplicate rejection, stop-before-start, post-stop safety recheck, restoration readiness and retryable failed-stop occupancy.
- NEEDS_INPUT preserves the existing Waiting choice or Rest foreground-alarm consent. The host must obtain explicit input and request the same target again through its adapter; it must not call a model start/resolve behind the coordinator.
- RestActivityAdapter uses existing presets/start/consent/resolve. stopForTransition stops background sound, silences ringing, cancels scheduled alerts, commits manual end and stops EV observation; it never ends Waiting.
- StudyActivityAdapter uses existing ModeStartManager. The owner retains the timer/session across route/configuration changes. Note input is encrypted in memory and serialized to the existing private encrypted repository. Format v2 reads v1; old applications do not understand v2. Normal note commit clears the matching draft atomically. Failed retention keeps the activity and input for retry.
- WorkActivityAdapter drains DocxEditorController before ending its timer. Session ID and startedAt survive extension/re-entry. The existing engine, retention, undo/redo and exports remain in place. A failed private-retention candidate is encrypted in memory, blocks exit, and requires retryRetention under the same freshly authorized profile. Timer expiry does not release the logical activity while retention recovery remains unresolved. Private repair remains available after timer expiry, without enabling editing or exposing an editor. No implicit external save is performed.
- DocumentSessionOwner is an Application-only AndroidViewModel obtained from the Activity ViewModelStore. Route disposal hides sensitive surfaces and access; it does not close the retained timers. Owner destruction closes children/gateway. No plaintext document is rendered on Home.
- EntertainmentActivityHost owns one Runtime for its ViewModel lifetime. CoordinatedEntertainmentAdmission requires the coordinator execution context and a matching private lease; direct controller/adapter start cannot bypass it. Internal Home/foreground loss pause content while retaining admission; return never starts the microphone/backing track automatically.

## Restoration and authorization

- Activity recreation reuses Activity ViewModelStore owners and session IDs. After process death, Study/Work live timers are not automatically restarted. Saved notes, draft input and DOCX files must be reopened under the existing fresh authorization/PIN/file-version checks. This is deliberately distinct from an uninterrupted in-memory timer.
- Rest retains its existing persisted-session recovery. All adapters must report restored before any new start, so a delayed Rest restore cannot overlap a newly started mode.
- Entertainment restores a paused content marker and persisted game/quiz state, then requires fresh coordinator admission and explicit content resume. Mic/audio are never restored automatically.
- A hidden Study with uncommitted input can require PIN/auth re-entry before promotion to a note. Failure blocks switching; the private draft remains encrypted. Retained bytes never grant authorization.
- Sudden process termination before an asynchronous atomic write completes is not a guarantee of retaining the latest keystroke. Device process/configuration restoration is NOT RUN.

## Validation in this task

- Android JVM: 235 tests, 0 failures, 0 errors, 0 skipped. Baseline was 204; added coverage includes coordinator races/expiry/cancellation/recovery/real Waiting preservation, Rest cleanup/failure, Study drafts/retention/legacy codec/restart, Work queue/failure/retry/profile isolation and real Entertainment admission/lifecycle.
- Debug APK and AndroidTest APK: PASS in the final execution log D:/tmp/stay-transition-acceptance.log. AndroidTest APK compilation is not a device test. The combined command exits with failure due only to the existing Lint errors, not the test/build tasks.
- Backend local regression: 28 passed, zero failed/skipped, via node --import tsx --test 'test/**/*.test.ts'. No Cloud test or deployment.
- Final Lint: 3 errors / 46 warnings. Existing error locations are AndroidKaraokeMicrophone.kt:142 (Manifest RECORD_AUDIO absent), debug PreviewSamples.kt:77 (ResourceType), DocxFileGateway.kt:39 (NewApi/API29 vs min28). These files are unchanged by this task. No suppression or permission/Gradle change was made.
- The initial test runner in the Korean Gradle cache failed to load GradleWorkerMain. The established English cache D:/tmp/pleos-waiting-gradle-20261103 and Android Studio JBR with file.encoding=COMPAT were used. Intermediate compile/test failures were fixed before final validation. Obsolete source analysis was stopped and final checks were run sequentially.
- No emulator, microphone, actual PLEOS, touch/visual QA, Cloud or deployment validation. Existing Lint library compatibility warnings can skip lifecycle checks; see the complete Lint report.

## Remaining UI integration

- A shared Activity-owned coordinator must receive the same four real adapters/retained model instances. Do not construct a second coordinator per screen.
- A profile/endpoint change must not replace the adapter for a running retained owner. Protect/stop that same owner before rebinding. Model adapters are called from the host Main dispatcher. Use currentState for a live query after natural expiry and state for transition progress.
- Bind mode start buttons, explicit end, same-mode resume and cross-mode confirmation to request/confirm/end. Existing direct ViewModel/controller start paths are not globally governed yet.
- Bind Waiting choices/Rest consent through adapter callbacks and NEEDS_INPUT. Preserve existing Premium design and Work discard/retention confirmation policy.
- EntertainmentScreen's current controller.start button must call the shared coordinator through the host. Add its route/home action later; no permissive admission placeholder.
- Bind foreground changes independently from internal navigation. Restore protected document access only after the existing PIN gate.
- Expose actionable save/retry errors, including Work retryRetention, without discarding failed candidates. Coordinate WorkViewModel/DocxEditorController and Workspace/StudyScreen changes with the UI owner before shared integration.
- RECORD_AUDIO declaration/request UI, karaoke UI, external video targets and real PLEOS microphone/audio/launch tests remain separate.

## Changed files

- android/app/src/main/java/com/pleosstay/app/rest/RestController.kt
- android/app/src/main/java/com/pleosstay/app/study/StudyRepository.kt
- android/app/src/main/java/com/pleosstay/app/study/StudyScreen.kt
- android/app/src/main/java/com/pleosstay/app/study/StudyViewModel.kt
- android/app/src/main/java/com/pleosstay/app/work/WorkModels.kt
- android/app/src/main/java/com/pleosstay/app/work/WorkViewModel.kt
- android/app/src/main/java/com/pleosstay/app/work/docx/DocxEditorController.kt
- android/app/src/main/java/com/pleosstay/app/workspace/DocumentWorkspaceEntry.kt
- android/app/src/main/java/com/pleosstay/app/workspace/ProtectedWorkWorkspace.kt
- android/app/src/test/java/com/pleosstay/app/rest/RestControllerTest.kt
- android/app/src/test/java/com/pleosstay/app/study/StudyViewModelTest.kt
- android/app/src/test/java/com/pleosstay/app/work/WorkViewModelTest.kt
- android/app/src/main/java/com/pleosstay/app/entertainment/EntertainmentActivityHost.kt
- android/app/src/main/java/com/pleosstay/app/modes/ActivityTransitionCoordinator.kt
- android/app/src/main/java/com/pleosstay/app/modes/DocumentActivityAdapters.kt
- android/app/src/main/java/com/pleosstay/app/rest/RestActivityAdapter.kt
- android/app/src/main/java/com/pleosstay/app/study/StudyNoteDrafts.kt
- android/app/src/main/java/com/pleosstay/app/work/docx/EncryptedEditRecovery.kt
- android/app/src/main/java/com/pleosstay/app/workspace/DocumentSessionOwner.kt
- android/app/src/main/java/com/pleosstay/app/workspace/RememberDocumentSession.kt
- android/app/src/test/java/com/pleosstay/app/entertainment/CoordinatedAdmissionTest.kt
- android/app/src/test/java/com/pleosstay/app/modes/ActivityTransitionCoordinatorTest.kt
- android/app/src/test/java/com/pleosstay/app/study/StudyDraftPersistenceTest.kt
- android/app/src/test/java/com/pleosstay/app/work/DocxTransitionTest.kt
- docs/verification/2026-11-08-activity-transition.md
