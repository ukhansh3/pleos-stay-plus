-- REVIEW_REQUIRED. Independent draft only. NOT a migration; never applied by this implementation.
-- Existing Cloud objects, default grants and storage policies must be inspected first.
create table public.realtime_devices (
 id uuid primary key default gen_random_uuid(), auth_uid uuid not null unique references auth.users(id),
 kind text not null check(kind in ('WINDOWS','VEHICLE')), display_name text not null,
 created_at timestamptz not null default now(), revoked_at timestamptz
);
create table public.realtime_pairing_requests (
 id uuid primary key default gen_random_uuid(), issuer_device_id uuid not null references public.realtime_devices,
 claimant_device_id uuid references public.realtime_devices, code_digest text not null unique,
 state text not null check(state in ('OPEN','CLAIMED','CONNECTED','CANCELLED','EXPIRED')),
 expires_at timestamptz not null, issuer_approved_at timestamptz, claimant_approved_at timestamptz,
 consumed_at timestamptz, created_at timestamptz not null default now(),
 check(issuer_device_id <> claimant_device_id)
);
create table public.realtime_connections (
 id uuid primary key default gen_random_uuid(), pc_device_id uuid not null references public.realtime_devices,
 vehicle_device_id uuid not null references public.realtime_devices,
 pairing_request_id uuid not null unique references public.realtime_pairing_requests,
 created_at timestamptz not null default now(), revoked_at timestamptz,
 check(pc_device_id <> vehicle_device_id)
);
create unique index realtime_active_pc on public.realtime_connections(pc_device_id) where revoked_at is null;
create unique index realtime_active_vehicle on public.realtime_connections(vehicle_device_id) where revoked_at is null;
create table public.realtime_files (
 id uuid primary key default gen_random_uuid(), connection_id uuid not null references public.realtime_connections,
 created_by_device_id uuid not null references public.realtime_devices, display_name text not null,
 created_at timestamptz not null default now(), deleted_at timestamptz
);
create table public.realtime_file_versions (
 id uuid primary key default gen_random_uuid(), file_id uuid not null references public.realtime_files,
 base_version_id uuid, version_no integer check(version_no > 0),
 kind text not null check(kind in ('ORIGINAL','REVISION')),
 uploaded_by_device_id uuid not null references public.realtime_devices,
 original_filename text not null, mime_type text not null, size_bytes bigint not null check(size_bytes > 0),
 sha256 text not null check(sha256 ~ '^[0-9a-f]{64}$'), storage_path text not null unique,
 state text not null check(state in ('PENDING','UPLOADING','VERIFYING','READY','FAILED','CANCELLED','EXPIRED')),
 idempotency_key uuid not null, request_digest text not null,
 created_at timestamptz not null default now(), expires_at timestamptz not null, committed_at timestamptz,
 unique(file_id,version_no), unique(file_id,id), unique(uploaded_by_device_id,idempotency_key),
 foreign key(file_id,base_version_id) references public.realtime_file_versions(file_id,id),
 check((kind='ORIGINAL' and base_version_id is null) or (kind='REVISION' and base_version_id is not null)),
 check((state='READY' and version_no is not null and committed_at is not null) or state<>'READY')
);
create table public.realtime_rate_limits (
 scope text not null, subject_digest text not null, window_started_at timestamptz not null,
 request_count integer not null check(request_count>=0), blocked_until timestamptz,
 primary key(scope,subject_digest,window_started_at)
);
create index realtime_files_connection_idx on public.realtime_files(connection_id) where deleted_at is null;
create index realtime_versions_pending_expiry_idx on public.realtime_file_versions(expires_at) where state <> 'READY';
create index realtime_pairings_issuer_idx on public.realtime_pairing_requests(issuer_device_id);
-- Server-mediated MVP: fail closed; no direct client grants or policies.
alter table public.realtime_devices enable row level security;
alter table public.realtime_pairing_requests enable row level security;
alter table public.realtime_connections enable row level security;
alter table public.realtime_files enable row level security;
alter table public.realtime_file_versions enable row level security;
alter table public.realtime_rate_limits enable row level security;
revoke all on public.realtime_devices,public.realtime_pairing_requests,public.realtime_connections,
 public.realtime_files,public.realtime_file_versions,public.realtime_rate_limits from public,anon,authenticated;
grant select,insert,update,delete on public.realtime_devices,public.realtime_pairing_requests,public.realtime_connections,
 public.realtime_files,public.realtime_file_versions,public.realtime_rate_limits to service_role;
-- Bucket creation deliberately separate from migrations. Existing global Storage policies require audit.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('stay-realtime-files','stay-realtime-files',false,20971520,array['application/octet-stream']);
-- Then review realtime-rpc.sql. Neither draft has been executed against PostgreSQL.
