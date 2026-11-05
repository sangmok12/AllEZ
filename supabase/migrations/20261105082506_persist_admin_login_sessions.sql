create table public.admin_sessions (
  token_hash text primary key check (token_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
alter table public.admin_sessions enable row level security;
revoke all on public.admin_sessions from public, anon, authenticated, service_role;
grant select, insert, delete on public.admin_sessions to service_role;
create policy server_admin_session_select on public.admin_sessions for select to service_role using(true);
create policy server_admin_session_insert on public.admin_sessions for insert to service_role with check(true);
create policy server_admin_session_delete on public.admin_sessions for delete to service_role using(true);
comment on table public.admin_sessions is 'Server-only hashed admin tokens. No passwords or raw tokens are stored.';
