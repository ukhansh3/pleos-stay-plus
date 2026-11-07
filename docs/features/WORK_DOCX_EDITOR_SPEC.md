# Work DOCX editor — implemented scope, 2026-11-07

Latest user instruction supersedes the PDF/upload portions of WORK_DOCUMENT_MVP_SPEC for this task. PDF editing and Realtime uploads remain excluded. No server/DB/ModeStartManager/Study changes.

## Decisions

- User approved the custom Kotlin/Java OOXML engine after both Apache POI5.4.1 and the custom engine passed isolated API34 probes. No third-party editor library is added to the production app.
- Local/new documents use the existing authenticated, PIN-unlocked Work profile. No independent anonymous local identity or new authentication policy.
- Private encrypted drafts remain locked and retained on revocation/switch; reads require the current exact profile. Imported Realtime drafts additionally require authorization for the exact original file/version.
- This Pleos image resolves ACTION_CREATE_DOCUMENT to `com.android.car.frameworkpackagestubs/.Stubs$DocumentsUIStubWithResult`. The real SAF UI is unavailable. User explicitly approved a fallback: **only after an in-app confirmation**, create a new public DOCX under `Download/StayPlus`. The confirmation explains access outside the app and persistence after lock/uninstall. No automatic public export.

## Responsibilities

`WorkScreen → WorkViewModel → DocxEditorController → DocxDocumentEngine/OoxmlDocxEngine`

`WorkDraftRepository` reuses the existing Android Keystore/AtomicFile record store. `DocxFileGateway` owns ContentResolver, SAF-result files, private temporary exports and the approved MediaStore Downloads fallback. Compose contains presentation and ActivityResult registration, not ZIP mutation or authentication.

`RealtimeEntry` constructs a managed workspace's state but **invokes its returned content only inside the original RealtimeLockedScreen PIN gate**. Foreground loss masks/releases document state; picker request metadata is retained so that a result is consumed only after fresh profile validation and PIN re-entry. Process death during a picker requires retrying from the retained encrypted draft.

## Functions

| ID | Implemented behavior | Bounds |
| --- | --- | --- |
| DOCX-01 | Blank document; Korean title/body as paragraphs; add/delete body paragraphs; export/reopen | Work session starts only on shared ModeStartManager Ready |
| DOCX-02 | Replace selected text ranges using cloned runs/properties; preserve untouched package entries | Does not flatten a source to extracted text; mixed inline-object/link/bookmark paragraphs have text editing disabled |
| DOCX-03 | Font name, half-point font size, bold/italic/underline, RGB colour, left/centre/right alignment | Direct OOXML properties; missing device fonts use preview fallback; no font embedding or Word pagination claim |
| DOCX-04 | Simple tables, append rows/columns, cell text through paragraph editor, selected-cell light-blue shading | Merged/nested/irregular tables are read only; a missing grid in a verified rectangular source is completed without inventing column widths; advanced cell layout is not supported |
| DOCX-05 | PNG/JPEG insert, copy-on-write image replacement, resize with correct relationships/content types | 4MiB source image, decoded pixel cap; UI dimensions use 96dpi EMU conversion; no SVG/WMF editing |
| DOCX-06 | Undo/redo, serialized atomic encrypted autosave, restart restore, last-work list | History is in memory, 20 states/32MiB; document snapshot persists, undo history does not |

The editor uses a reflow workspace with a paragraph selector, selected-paragraph text field, table cells and images. It is not an exact Microsoft Word page-layout renderer. New paragraphs use the paragraph-add control; line breaks inside a text field remain paragraph-local line breaks.

## File and safety semantics

- Save commits an encrypted private DOCX draft. Save As exports a real DOCX: SAF when supported; otherwise explicit public Downloads confirmation or a private temporary DOCX option.
- New public downloads use MediaStore IS_PENDING while writing. Export verifies size/hash and reparses OOXML before publishing; original files are not overwritten. A crash may leave a new unpublished pending entry for provider cleanup, while the previous valid file/draft remains.
- Private temporary files use a non-exported FileProvider with only `cache/work-docx-exports/` exposed. No URI grant is automatically issued. Lock/route exit removes these files and revokes grants; encrypted drafts remain. Public copies survive lock as explicitly approved.
- UNKNOWN/DRIVING, foreground loss, PIN/access denial: hide editor and clear UI buffers/history; cancel publication/read operations. Already accepted encrypted retention can finish atomically without exposing content.
- ZIP/XML bounds, path/duplicate checks, no DTD/entities or external fetch, validation before retained mutation. Unsupported protected/signed/tracked/content-control/math/merged/embedded structures are restricted/read only rather than removed. Headers/footers/styles/custom parts not being edited stay in the package.
- Public export does not call Realtime uploads, change pairing, create server APIs, modify permissions/RLS or install another file manager. The resulting public file can be selected for a future **manual** transfer; this task performs no upload.

See the dated verification report for actual PASS / BLOCKED / NOT RUN results and remaining compatibility limits.
