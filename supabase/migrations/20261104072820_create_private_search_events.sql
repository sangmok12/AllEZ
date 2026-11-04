-- Apply with a privileged migration connection, never from the browser.
-- Search IDs identify a search operation, not a person or browser.
create table public.search_events (
    event_id uuid primary key,
    search_id uuid not null,
    recorded_at timestamptz not null default now(),
    category text not null default 'jobs' check (category in ('jobs', 'part_time', 'homes')),
    event_type text not null check (event_type in ('search', 'load_more')),
    keyword text not null check (char_length(keyword) <= 100),
    region_ids text[] not null default '{}',
    career_type text not null default 'ANY',
    minimum_career_years integer check (minimum_career_years between 0 and 30),
    maximum_career_years integer check (maximum_career_years between 0 and 30),
    minimum_education text not null default 'ANY',
    employment_types text[] not null default '{}',
    jobkorea_page integer check (jobkorea_page >= 0),
    saramin_page integer check (saramin_page >= 0),
    returned_count integer not null check (returned_count >= 0),
    jobkorea_count integer not null default 0 check (jobkorea_count >= 0),
    saramin_count integer not null default 0 check (saramin_count >= 0),
    outcome text not null check (outcome in ('success', 'partial_failure', 'failure')),
    duration_ms integer not null check (duration_ms >= 0),
    check (minimum_career_years is null or maximum_career_years is null
           or minimum_career_years <= maximum_career_years)
);

create index search_events_recorded_at_idx on public.search_events (recorded_at);
create index search_events_search_id_idx on public.search_events (search_id);

alter table public.search_events enable row level security;
revoke all on table public.search_events from public, anon, authenticated;
revoke all on table public.search_events from service_role;
grant insert on table public.search_events to service_role;

comment on table public.search_events is
'Server-written search statistics. No IP, account ID, browser ID, email or phone fields. Keyword must be redacted by the server before inserting.';
