# Pickup guest web

Next.js mobile web for the driver share handoff and one Pickup participant. It uses a separate UI-less Supabase anonymous session, Fastify Pickup API, optional browser GPS, and Kakao Maps JavaScript SDK.

```powershell
Copy-Item .env.example .env.local
npm install
npm run dev
```

Set only publishable browser values in `.env.local`. `SUPABASE_SECRET_KEY` must never be present in this directory or a `NEXT_PUBLIC_*` variable. Kakao Map API activation and the exact localhost/demo domain must be registered in Kakao Developers before real map validation.

`/dev/pickup-simulator` returns 404 unless `ENABLE_PICKUP_SIMULATOR=true`, and it is always blocked by the server component when `NODE_ENV=production`. It labels all coordinates `SIMULATED` and has no production coordinate-write path.

Validation:

```powershell
npm test
npm run typecheck
npm run build
```

Planned: Next.js + TypeScript mobile web for invitation landing, consent and participant status updates. Do not assume continuous background GPS; use user-initiated state changes unless approved otherwise.

No Next.js project generated yet.
