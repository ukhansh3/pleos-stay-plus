import { randomUUID } from "node:crypto";

const required = (name) => {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
};

const supabaseUrl = required("SUPABASE_URL");
const publishableKey = required("SUPABASE_PUBLISHABLE_KEY");
const secretKey = required("SUPABASE_SECRET_KEY");
const apiBase = process.env.PICKUP_E2E_API_BASE_URL?.trim() || "http://127.0.0.1:3100";
const serverAgent = "pleos-stay-plus-e2e/0.1";

async function request(url, init, expected = [200]) {
  const response = await fetch(url, init);
  if (!expected.includes(response.status)) throw new Error(`${init.method || "GET"} ${new URL(url).pathname} returned ${response.status}`);
  if (response.status === 204) return null;
  return response.json();
}

async function anonymousUser() {
  const result = await request(new URL("/auth/v1/signup", supabaseUrl), {
    method: "POST",
    headers: { apikey: publishableKey, "User-Agent": serverAgent, "Content-Type": "application/json" },
    body: "{}",
  }, [200]);
  if (!result.access_token || !result.user?.id) throw new Error("Anonymous Auth did not return a session");
  return { id: result.user.id, token: result.access_token };
}

const api = (path, token, method = "GET", body, idempotencyKey) => request(new URL(path, apiBase), {
  method,
  headers: {
    Authorization: `Bearer ${token}`,
    ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
  },
  ...(body === undefined ? {} : { body: JSON.stringify(body) }),
}, method === "POST" && path === "/api/v1/pickup/sessions" ? [201] : method === "POST" && path.endsWith("/invitations") ? [201] : [200]);

async function deleteUser(id) {
  await request(new URL(`/auth/v1/admin/users/${id}`, supabaseUrl), {
    method: "DELETE",
    headers: { apikey: secretKey, "User-Agent": serverAgent },
  }, [200, 204]);
}

async function rlsRows(table, query, user) {
  return request(new URL(`/rest/v1/${table}?${query}`, supabaseUrl), {
    headers: { apikey: publishableKey, Authorization: `Bearer ${user.token}`, "User-Agent": serverAgent },
  }, [200]);
}

let driver;
let guest;
let outsider;
try {
  [driver, guest, outsider] = await Promise.all([anonymousUser(), anonymousUser(), anonymousUser()]);
  console.log("PASS anonymous driver, guest, and isolation-user authentication");

  const session = await api("/api/v1/pickup/sessions", driver.token, "POST", { locationSharingConsent: true }, randomUUID());
  const invitation = await api(`/api/v1/pickup/sessions/${session.id}/invitations`, driver.token, "POST", undefined, randomUUID());
  const token = decodeURIComponent(new URL(invitation.shareUrl).pathname.split("/").pop());
  const participant = await api("/api/v1/pickup/invitations/redeem", guest.token, "POST", { token, consent: true, displayName: "E2E Pickup Guest" });
  console.log("PASS independent session, invitation, and single guest binding");

  const observedAt = new Date().toISOString();
  await api(`/api/v1/pickup/sessions/${session.id}/vehicle-location`, driver.token, "PUT", { latitude: 37.401, longitude: 127.108, accuracyMeters: 5, observedAt, source: "REAL" });
  await api(`/api/v1/pickup/participants/${participant.participantId}/location`, guest.token, "PUT", { latitude: 37.40105, longitude: 127.10805, accuracyMeters: 5, observedAt, source: "REAL" });
  await api(`/api/v1/pickup/participants/${participant.participantId}/status`, guest.token, "POST", { status: "DEPARTED", clientEventId: randomUUID() });
  const driverSnapshot = await api(`/api/v1/pickup/sessions/${session.id}/snapshot`, driver.token);
  const guestSnapshot = await api(`/api/v1/pickup/sessions/${session.id}/snapshot`, guest.token);
  if (!driverSnapshot.vehicleLocation || !guestSnapshot.participantLocation) throw new Error("Snapshot did not contain both latest locations");
  console.log("PASS real locations, participant status, and two-user snapshots");

  const [driverRows, guestRows, outsiderRows, guestLocations, outsiderLocations] = await Promise.all([
    rlsRows("pickup_sessions", `id=eq.${session.id}&select=id`, driver),
    rlsRows("pickup_sessions", `id=eq.${session.id}&select=id`, guest),
    rlsRows("pickup_sessions", `id=eq.${session.id}&select=id`, outsider),
    rlsRows("pickup_current_locations", `session_id=eq.${session.id}&select=actor_type`, guest),
    rlsRows("pickup_current_locations", `session_id=eq.${session.id}&select=actor_type`, outsider),
  ]);
  if (driverRows.length !== 1 || guestRows.length !== 1 || outsiderRows.length !== 0 || guestLocations.length !== 2 || outsiderLocations.length !== 0) throw new Error("RLS ownership/participant isolation failed");
  console.log("PASS authenticated RLS owner, participant, and outsider isolation");

  await api(`/api/v1/pickup/sessions/${session.id}/complete`, driver.token, "POST", undefined, randomUUID());
  await request(new URL(`/api/v1/pickup/sessions/${session.id}/snapshot`, apiBase), { headers: { Authorization: `Bearer ${driver.token}` } }, [410]);
  const locations = await request(new URL(`/rest/v1/pickup_current_locations?session_id=eq.${session.id}&select=session_id`, supabaseUrl), { headers: { apikey: secretKey, "User-Agent": serverAgent } }, [200]);
  if (locations.length !== 0) throw new Error("Closed Pickup retained current locations");
  const guestAfterClose = await rlsRows("pickup_sessions", `id=eq.${session.id}&select=id`, guest);
  if (guestAfterClose.length !== 0) throw new Error("Closed Pickup still granted participant access");
  console.log("PASS completion, closed-session rejection, and location cleanup");
} finally {
  if (outsider?.id) await deleteUser(outsider.id);
  if (guest?.id) await deleteUser(guest.id);
  if (driver?.id) await deleteUser(driver.id);
  console.log("PASS E2E test users and cascaded data cleanup");
}
