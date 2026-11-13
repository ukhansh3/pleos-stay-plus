# Pickup guest web

Next.js mobile web for the driver share handoff and one Pickup participant. It uses a separate UI-less Supabase anonymous session, Fastify Pickup API, optional browser GPS, and Kakao Maps JavaScript SDK.

```powershell
Copy-Item .env.example .env.local
npm install
npm run dev
```

Set only publishable browser values in `.env.local`. `SUPABASE_SECRET_KEY` must never be present in this directory or a `NEXT_PUBLIC_*` variable. `PICKUP_BACKEND_URL` is server-only and defaults to the loopback Fastify server in development. Set it explicitly when running a production build, including when Next.js and Fastify share one host. Non-loopback targets must use HTTPS. Browser requests stay on the Next.js origin and the proxy only forwards fixed Pickup paths and bearer/idempotency headers. Kakao Map API activation and the exact localhost/demo domain must be registered in Kakao Developers before real map validation.

`/pickup/session` restores the participant binding after refresh, refreshes the Supabase access token near expiry, and rejects a revoked or closed Pickup. Optional GPS sharing uses `watchPosition` with an approximately five-second upload target; browsers may throttle or stop callbacks in the background, so snapshot/reconnect recovery remains enabled and manual status controls remain available without GPS.

`/dev/pickup-simulator` returns 404 unless `ENABLE_PICKUP_SIMULATOR=true`, and it is always blocked when `NODE_ENV=production`. The matching backend must also set `PICKUP_SIMULATOR_ENABLED=true` and the same random `PICKUP_SIMULATOR_TOKEN` (at least 32 characters). Both the page and write proxy require loopback; the token stays server-side. The simulator labels all coordinates `SIMULATED`, supports draggable markers/direct coordinates, signal loss/reconnection, path replay and an accelerated simulated clock, and calls the same Pickup domain state transitions through isolated QA routes.

The separate /connect route explains how to connect from the Windows Stay+ app. It can be opened or shared from a phone and does not accept credentials or grant access to files.

Validation:

```powershell
npm test
npm run typecheck
npm run build
```

Continuous browser GPS is best-effort and not a background-location guarantee. Closing the page, revoking consent, invite reissue, or closing the Pickup stops updates; user-initiated status controls continue to work when GPS is denied.

## Stay+ mobile Pickup (2026-11-13)

The participant UI follows the existing Stay+ navy/purple brand; the Android home and its assets are unchanged. `public/stay-brand-logo.png` is a byte-for-byte copy of the existing approved Android logo, with no new image service or license dependency.

- Driver QR: `/share/{token}`. Share/copy only; does not reveal vehicle coordinates or join automatically.
- Participant: `/pickup/{token}`. Explicit consent, optional nickname (blank is omitted from the request), then authenticated vehicle map/status view.
- Refresh recovery: `/pickup/session`. The binding is scoped to the authenticated guest and invitation fingerprint; a different invitation never silently opens an older Pickup.
- Supabase changes trigger snapshots, with a five-second visible-page fallback and re-query on network/visibility recovery. The UI separately identifies last-observed vehicle data and stale positions; no ETA or route distance is fabricated.
- Kakao Map instance stays mounted while vehicle/participant markers move. Dragging pauses vehicle following; `차량 보기` restores it. The SDK needs the **exact** web origin registered in Kakao Developers. An expiring Quick Tunnel origin must be re-registered if it changes. A successful localhost SDK response does not prove the temporary origin is authorized.
- GPS is opt-in. Switching off, permission loss, leaving the page or session closure clears the watcher and aborts pending client sends; late callbacks are ignored. Already accepted server coordinates remain subject to the existing session-end deletion policy. The UI never claims that turning off the switch deletes a previously received sample.
- Manual completion/cancellation requires confirmation. Closed/revoked sessions immediately lose the map and local binding. Browser history restoration rechecks authorization before redisplaying positions.

Responsibilities: `PickupPanel` renders; `usePickupSession` owns session/UI actions; `lib/api` owns the existing HTTP contract; `pickup-gps` owns the cancellable browser watcher; `KakaoPickupMap` owns the SDK adapter. No new backend endpoints, permissions or dependencies were introduced.