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
