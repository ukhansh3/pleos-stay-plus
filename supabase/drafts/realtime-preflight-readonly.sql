-- READ ONLY. Execute only through a verified project-scoped read-only connection.
-- No user records, file contents, tokens or auth.users rows are selected.
select current_database() as database_name,current_user,current_setting('transaction_read_only') as transaction_read_only;
select n.nspname as schema_name,c.relname as table_name,c.relrowsecurity as rls_enabled
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname in('public','storage') and c.relkind in('r','p') order by 1,2;
select table_schema,table_name,column_name,data_type,is_nullable,column_default
from information_schema.columns where table_schema='public' and (table_name like 'pickup_%' or table_name like 'realtime_%' or table_name='waiting_sessions') order by table_name,ordinal_position;
select schemaname,tablename,policyname,permissive,roles,cmd,qual,with_check from pg_policies
where schemaname in('public','storage') order by schemaname,tablename,policyname;
select table_schema,table_name,grantee,privilege_type from information_schema.role_table_grants
where table_schema in('public','storage') and grantee in('anon','authenticated','service_role','PUBLIC') order by 1,2,3,4;
select n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) as arguments,p.prosecdef,p.proconfig,p.proacl
from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and (p.proname like '%pickup%' or p.proname like 'realtime_%');
select pubname,schemaname,tablename from pg_publication_tables order by 1,2,3;
select id,name,public,file_size_limit,allowed_mime_types from storage.buckets order by id;
-- Run separately if the migration-history table exists and is readable:
select version,name from supabase_migrations.schema_migrations order by version;
-- Anonymous Auth enablement/CAPTCHA/rate limits require Dashboard settings inspection.
