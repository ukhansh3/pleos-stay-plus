-- APPROVAL REQUIRED. Only this reviewed Realtime batch; NEVER run the existing migrations directory.
-- Intended project: iadqoesbqamweseplvkr (pleos-stay-plus).
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

-- REVIEW_REQUIRED: apply only AFTER realtime-file-transfer.sql in an approved isolated database.
-- Existing Pickup and migration history are not modified. No SQL was executed by this task.
create or replace function public.realtime_version_json(v public.realtime_file_versions) returns jsonb
language sql stable set search_path=public as $$ select jsonb_build_object(
'id',v.id,'fileId',v.file_id,'connectionId',f.connection_id,'baseVersionId',v.base_version_id,'versionNo',v.version_no,
'kind',v.kind,'uploadedByDeviceId',v.uploaded_by_device_id,'fileName',v.original_filename,'mimeType',v.mime_type,
'size',v.size_bytes,'sha256',v.sha256,'state',v.state,'createdAt',v.created_at,'expiresAt',extract(epoch from v.expires_at)*1000,'committedAt',v.committed_at)
from public.realtime_files f where f.id=v.file_id $$;
revoke all on function public.realtime_version_json(public.realtime_file_versions) from public,anon,authenticated;
grant execute on function public.realtime_version_json(public.realtime_file_versions) to service_role;

create or replace function public.realtime_command(actor_uid uuid, operation text, args jsonb default '{}'::jsonb)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare
 d realtime_devices; peer realtime_devices; p realtime_pairing_requests; c realtime_connections;
 f realtime_files; v realtime_file_versions; original realtime_file_versions;
 target uuid; requested_digest text; count_value integer; result jsonb; window_start timestamptz;
begin
 -- Serialize metadata transitions only; no Storage/network operation is performed under this lock.
 perform pg_advisory_xact_lock(734912204);
 if operation='cleanup' then
  update realtime_file_versions set state='EXPIRED' where expires_at<=now() and state not in('READY','CANCELLED','EXPIRED');
  return coalesce((select jsonb_agg(realtime_version_json(x)) from realtime_file_versions x join realtime_files y on y.id=x.file_id
    where (x.expires_at<=now() and (x.state in('CANCELLED','EXPIRED') or y.deleted_at is not null)) or (x.state='READY' and y.deleted_at is not null)),'[]'::jsonb);
 end if;
 if actor_uid is null then raise exception 'UNAUTHENTICATED'; end if;
 select * into d from realtime_devices where auth_uid=actor_uid and revoked_at is null;
 if operation='register' then
  if args->>'kind' not in ('WINDOWS','VEHICLE') or length(trim(args->>'name')) not between 1 and 80 then raise exception 'INVALID_DEVICE'; end if;
  if d.id is null then
   insert into realtime_devices(auth_uid,kind,display_name) values(actor_uid,args->>'kind',trim(args->>'name')) returning * into d;
  elsif d.kind<>args->>'kind' then raise exception 'DEVICE_KIND_CONFLICT'; end if;
  return jsonb_build_object('id',d.id,'uid',d.auth_uid,'kind',d.kind,'name',d.display_name);
 end if;
 if d.id is null then raise exception 'DEVICE_NOT_REGISTERED'; end if;
 if operation='device' then return jsonb_build_object('id',d.id,'uid',d.auth_uid,'kind',d.kind,'name',d.display_name);end if;
 select * into c from realtime_connections where revoked_at is null and d.id in(pc_device_id,vehicle_device_id);
 if operation='current' then
  if c.id is null then return 'null'::jsonb;end if;
  return jsonb_build_object('id',c.id,'pcDeviceId',c.pc_device_id,'vehicleDeviceId',c.vehicle_device_id,'revokedAt',null);
 end if;
 if operation='limit' then
  window_start:=date_trunc('hour',now())+floor(extract(minute from now())/10)*interval '10 minutes';
  insert into realtime_rate_limits(scope,subject_digest,window_started_at,request_count)
  values(args->>'scope',args->>'subject',window_start,1)
  on conflict(scope,subject_digest,window_started_at) do update set request_count=realtime_rate_limits.request_count+1
  returning request_count into count_value;
  -- Return, rather than raise, so failed attempts persist in this independent RPC transaction.
  return jsonb_build_object('allowed',count_value<=coalesce((args->>'limit')::integer,5));
 end if;
 if operation='issue' then
  if c.id is not null then raise exception 'ALREADY_CONNECTED';end if;
  update realtime_pairing_requests set state='CANCELLED' where issuer_device_id=d.id and state in('OPEN','CLAIMED');
  insert into realtime_pairing_requests(issuer_device_id,code_digest,state,expires_at)
  values(d.id,args->>'digest','OPEN',now()+make_interval(secs=least(greatest((args->>'ttlSeconds')::integer,60),900))) returning * into p;
  return jsonb_build_object('id',p.id,'expiresAt',extract(epoch from p.expires_at)*1000);
 end if;
 if operation='claim' then
  select * into p from realtime_pairing_requests where code_digest=args->>'digest' and state='OPEN' and expires_at>now();
  if p.id is null then raise exception 'INVALID_PAIRING_CODE';end if;
  select * into peer from realtime_devices where id=p.issuer_device_id and revoked_at is null;
  if c.id is not null or peer.id is null or peer.kind=d.kind then raise exception 'INCOMPATIBLE_DEVICE';end if;
  update realtime_pairing_requests set claimant_device_id=d.id,state='CLAIMED' where id=p.id returning * into p;
 elsif operation in('pairing','approve','cancelPairing') then
  select * into p from realtime_pairing_requests where id=(args->>'id')::uuid and d.id in(issuer_device_id,claimant_device_id);
  if p.id is null then raise exception 'PAIRING_NOT_FOUND';end if;
 end if;
 if operation in('claim','pairing','approve','cancelPairing') then
  if operation='cancelPairing' then
   if p.state='CONNECTED' then raise exception 'ALREADY_CONNECTED';end if;
   update realtime_pairing_requests set state='CANCELLED' where id=p.id;return 'null'::jsonb;
  end if;
  if operation='approve' and p.state<>'CONNECTED' then
   if p.state<>'CLAIMED' or p.expires_at<=now() then raise exception 'PAIRING_NOT_ACTIVE';end if;
   if exists(select 1 from realtime_connections where revoked_at is null and (pc_device_id in(p.issuer_device_id,p.claimant_device_id) or vehicle_device_id in(p.issuer_device_id,p.claimant_device_id))) then raise exception 'ALREADY_CONNECTED';end if;
   update realtime_pairing_requests set issuer_approved_at=case when d.id=issuer_device_id then now() else issuer_approved_at end,
   claimant_approved_at=case when d.id=claimant_device_id then now() else claimant_approved_at end where id=p.id returning * into p;
   if p.issuer_approved_at is not null and p.claimant_approved_at is not null then
    select * into peer from realtime_devices where id=p.issuer_device_id;
    insert into realtime_connections(pc_device_id,vehicle_device_id,pairing_request_id)
    values(case when peer.kind='WINDOWS' then p.issuer_device_id else p.claimant_device_id end,
    case when peer.kind='VEHICLE' then p.issuer_device_id else p.claimant_device_id end,p.id);
    update realtime_pairing_requests set state='CONNECTED',consumed_at=now() where id=p.id returning * into p;
   end if;
  end if;
  select jsonb_build_object('id',p.id,'state',case when p.state in('OPEN','CLAIMED') and p.expires_at<=now() then 'EXPIRED' else p.state end,
  'issuer',jsonb_build_object('id',a.id,'kind',a.kind,'name',a.display_name),
  'claimant',case when b.id is null then null else jsonb_build_object('id',b.id,'kind',b.kind,'name',b.display_name) end,
  'expiresAt',extract(epoch from p.expires_at)*1000,'approvedDeviceIds',to_jsonb(array_remove(array[case when p.issuer_approved_at is not null then p.issuer_device_id end,case when p.claimant_approved_at is not null then p.claimant_device_id end],null)),
  'connectionId',(select id from realtime_connections where pairing_request_id=p.id)) into result
  from realtime_devices a left join realtime_devices b on b.id=p.claimant_device_id where a.id=p.issuer_device_id;
  return result;
 end if;
 if operation='revoke' then
  update realtime_connections set revoked_at=coalesce(revoked_at,now()) where id=(args->>'id')::uuid and d.id in(pc_device_id,vehicle_device_id);
  if not found then raise exception 'CONNECTION_UNAVAILABLE';end if;return 'null'::jsonb;
 end if;
 if operation='list' then
  return coalesce((select jsonb_agg(jsonb_build_object('id',x.id,'connectionId',x.connection_id,'name',x.display_name,'deleted',false,'createdAt',x.created_at)) from realtime_files x where x.connection_id=c.id and x.deleted_at is null and exists(select 1 from realtime_file_versions z where z.file_id=x.id and z.state='READY')),'[]'::jsonb);
 end if;
 if c.id is null then raise exception 'CONNECTION_UNAVAILABLE';end if;
 if operation='authorize' then
  if c.id<>(args->>'id')::uuid then raise exception 'CONNECTION_UNAVAILABLE';end if;
  return jsonb_build_object('id',c.id,'pcDeviceId',c.pc_device_id,'vehicleDeviceId',c.vehicle_device_id,'revokedAt',null);
 end if;
 if operation='prepare' then
  if c.id<>(args->>'connectionId')::uuid then raise exception 'CONNECTION_UNAVAILABLE';end if;
  requested_digest:=args->>'requestDigest';
  select * into v from realtime_file_versions where uploaded_by_device_id=d.id and idempotency_key=(args->>'idempotencyKey')::uuid;
  if v.id is not null then
   if v.request_digest<>requested_digest then raise exception 'IDEMPOTENCY_CONFLICT';end if;
   if exists(select 1 from realtime_files where id=v.file_id and deleted_at is not null) then raise exception 'FILE_UNAVAILABLE';end if;
   return realtime_version_json(v);
  end if;
  if args->>'fileId' is null then
   if args->>'baseVersionId' is not null then raise exception 'BASE_VERSION_INVALID';end if;
   insert into realtime_files(connection_id,created_by_device_id,display_name) values(c.id,d.id,args->>'fileName') returning * into f;
  else
   select * into f from realtime_files where id=(args->>'fileId')::uuid and connection_id=c.id and deleted_at is null;
   select * into original from realtime_file_versions where id=(args->>'baseVersionId')::uuid and file_id=f.id and state='READY';
   if f.id is null or original.id is null then raise exception 'BASE_VERSION_INVALID';end if;
  end if;
  target:=gen_random_uuid();
  insert into realtime_file_versions(id,file_id,base_version_id,kind,uploaded_by_device_id,original_filename,mime_type,size_bytes,sha256,storage_path,state,idempotency_key,request_digest,expires_at)
  values(target,f.id,original.id,case when original.id is null then 'ORIGINAL' else 'REVISION' end,d.id,args->>'fileName',args->>'mimeType',(args->>'size')::bigint,args->>'sha256',c.id||'/'||f.id||'/'||target||'/content','PENDING',(args->>'idempotencyKey')::uuid,requested_digest,now()+interval '1 hour') returning * into v;
  return realtime_version_json(v);
 end if;
 if operation in('versions','deleteFile') then
  select * into f from realtime_files where id=(args->>'id')::uuid and connection_id=c.id and deleted_at is null;
  if f.id is null then raise exception 'FILE_UNAVAILABLE';end if;
  if operation='deleteFile' then update realtime_files set deleted_at=now() where id=f.id;return 'null'::jsonb;end if;
  return coalesce((select jsonb_agg(realtime_version_json(x) order by x.version_no) from realtime_file_versions x where x.file_id=f.id and x.state='READY'),'[]'::jsonb);
 end if;
 select x.* into v from realtime_file_versions x join realtime_files y on y.id=x.file_id
 where x.id=(args->>'id')::uuid and y.connection_id=c.id and y.deleted_at is null;
 if v.id is null then raise exception 'UPLOAD_UNAVAILABLE';end if;
 if operation='download' then
  if v.state<>'READY' then raise exception 'FILE_UNAVAILABLE';end if;return realtime_version_json(v);
 end if;
 if v.uploaded_by_device_id<>d.id then raise exception 'UPLOAD_UNAVAILABLE';end if;
 if v.expires_at<=now() and v.state not in('READY','CANCELLED') then
  update realtime_file_versions set state='EXPIRED' where id=v.id returning * into v;
 end if;
 if operation='begin' then
  if v.state not in('PENDING','FAILED') then raise exception 'UPLOAD_STATE_CONFLICT';end if;
  update realtime_file_versions set state='UPLOADING' where id=v.id returning * into v;
 elsif operation='verified' then
  if v.state<>'UPLOADING' then raise exception 'UPLOAD_STATE_CONFLICT';end if;
  if v.sha256<>args->>'sha256' or v.size_bytes<>(args->>'size')::bigint then raise exception 'INTEGRITY_MISMATCH';end if;
  update realtime_file_versions set state='VERIFYING' where id=v.id returning * into v;
 elsif operation='fail' then
  if v.state='UPLOADING' then update realtime_file_versions set state='FAILED' where id=v.id returning * into v;end if;
 elsif operation='cancel' then
  if v.state='READY' then raise exception 'ALREADY_COMPLETE';end if;
  update realtime_file_versions set state='CANCELLED' where id=v.id returning * into v;
 elsif operation='complete' then
  if v.state='READY' then return realtime_version_json(v);end if;
  if v.state<>'VERIFYING' then raise exception 'UPLOAD_NOT_VERIFIED';end if;
  select coalesce(max(version_no),0)+1 into count_value from realtime_file_versions where file_id=v.file_id and state='READY';
  update realtime_file_versions set state='READY',version_no=count_value,committed_at=now() where id=v.id returning * into v;
 elsif operation<>'status' then raise exception 'INVALID_OPERATION';
 end if;
 return realtime_version_json(v);
end $$;
revoke all on function public.realtime_command(uuid,text,jsonb) from public,anon,authenticated;
grant execute on function public.realtime_command(uuid,text,jsonb) to service_role;
