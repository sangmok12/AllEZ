create table public.usage_events (
  event_id uuid primary key,
  recorded_at timestamptz not null default now(),
  occurred_at timestamptz not null,
  visitor_id uuid not null,
  session_id uuid not null,
  category text not null check (category in ('jobs','part_time','homes')),
  event_type text not null check (event_type in ('page_view','search_click','posting_click')),
  search_id uuid,
  source text check (source in ('JOBKOREA','SARAMIN','ALBAMON','ALBAHEAVEN','ZIGBANG','DABANG','NAVER')),
  posting_id text check (length(posting_id) <= 100),
  search_criteria jsonb,
  check (event_type <> 'search_click' or (search_id is not null and search_criteria is not null)),
  check (event_type <> 'posting_click' or (search_id is not null and source is not null and posting_id is not null))
);
alter table public.usage_events enable row level security;
revoke all on public.usage_events from public, anon, authenticated, service_role;
grant insert on public.usage_events to service_role;
create policy server_only_usage_insert on public.usage_events for insert to service_role with check (true);
create index usage_events_category_time_idx on public.usage_events (category, occurred_at desc);
create index usage_events_time_idx on public.usage_events (occurred_at desc);
create index usage_events_search_idx on public.usage_events (search_id) where search_id is not null;
alter table public.search_events add column visitor_id uuid, add column session_id uuid;
comment on table public.usage_events is 'Private browser usage events; counts are estimates, not authenticated people. /health never writes events.';
