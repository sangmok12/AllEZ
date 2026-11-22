-- Add diagnostics while preserving historical results and existing table privileges.
alter table public.search_events
  add column source_total_count integer check(source_total_count >= 0),
  add column error_message text check(char_length(error_message) <= 500);
alter table public.usage_events
  drop constraint usage_events_event_type_check,
  add constraint usage_events_event_type_check check(event_type in ('page_view','search_click','search_cancel','posting_click'));
create or replace function public.admin_usage_report(p_start date, p_end date, p_category text default 'all', p_page integer default 1)
returns jsonb language plpgsql stable security invoker set search_path = '' as $$
declare result jsonb;
begin
  if p_start is null or p_end is null or p_end < p_start or p_end - p_start > 365
     or p_category not in ('all','jobs','part_time','homes') or p_page < 1 or p_page > 10000 then
    raise exception 'Invalid report range';
  end if;
  with filtered as materialized (
    select * from public.usage_events
    where occurred_at >= p_start::timestamp at time zone 'Asia/Seoul'
      and occurred_at < (p_end + 1)::timestamp at time zone 'Asia/Seoul'
      and (p_category = 'all' or category = p_category)
  ), searches as (
    select event_id, search_id, occurred_at, category, search_criteria from filtered where event_type='search_click'
    union all
    (select distinct on (s.search_id) s.event_id,s.search_id,s.recorded_at,s.category,
       jsonb_build_object('keyword',s.keyword,'region_ids',s.region_ids,'career_type',s.career_type,
         'minimum_career_years',s.minimum_career_years,'maximum_career_years',s.maximum_career_years,
         'minimum_education',s.minimum_education,'employment_types',s.employment_types)
     from public.search_events s
     where s.event_type='search'
       and s.recorded_at >= p_start::timestamp at time zone 'Asia/Seoul'
       and s.recorded_at < (p_end + 1)::timestamp at time zone 'Asia/Seoul'
       and (p_category='all' or s.category=p_category)
       and not exists (select 1 from public.usage_events u where u.search_id=s.search_id and u.event_type='search_click')
     order by s.search_id,s.recorded_at desc)
  ), totals as (
    select count(distinct visitor_id) filter(where event_type='page_view') as visitors,
      count(distinct session_id) filter(where event_type='page_view') as visits,
      count(*) filter(where event_type='search_click') as searches,
      count(*) filter(where event_type='posting_click') as clicks from filtered
  ), by_category as (
    select c.category,count(distinct f.visitor_id) filter(where f.event_type='page_view') as visitors,
      count(distinct f.session_id) filter(where f.event_type='page_view') as visits,
      count(*) filter(where f.event_type='search_click') as searches,
      count(*) filter(where f.event_type='posting_click') as clicks
    from (values ('jobs'),('part_time'),('homes')) as c(category)
    left join filtered f on f.category=c.category
    group by c.category
  ), days as (
    select generate_series(p_start::timestamp,p_end::timestamp,interval '1 day')::date as day
  ), daily as (
    select d.day,count(distinct f.visitor_id) filter(where f.event_type='page_view') as visitors,
      count(distinct f.session_id) filter(where f.event_type='page_view') as visits,
      count(*) filter(where f.event_type='search_click') as searches,
      count(*) filter(where f.event_type='posting_click') as clicks
    from days d left join filtered f on (f.occurred_at at time zone 'Asia/Seoul')::date=d.day group by d.day
  ), history as (
    select s.event_id,s.search_id,s.occurred_at,s.category,s.search_criteria,
      case when exists(select 1 from public.usage_events c where c.search_id=s.search_id and c.event_type='search_cancel') then null else r.returned_count end as returned_count,
      r.source_total_count,r.error_message,
      case when exists(select 1 from public.usage_events c where c.search_id=s.search_id and c.event_type='search_cancel') then 'cancelled'
        when r.outcome='success' and r.returned_count=0 then 'empty'
        else r.outcome end as outcome
    from (select * from searches order by occurred_at desc,event_id desc limit 25 offset ((p_page-1)*25)) s
    left join lateral (
      select returned_count,outcome,source_total_count,error_message from public.search_events r
      where r.search_id=s.search_id and r.event_type='search'
      order by recorded_at desc limit 1
    ) r on true
  )
  select jsonb_build_object(
    'totals',(select to_jsonb(t) from totals t),
    'categories',(select jsonb_agg(to_jsonb(c) order by c.category) from by_category c),
    'daily',(select jsonb_agg(to_jsonb(d) order by d.day) from daily d),
    'history',coalesce((select jsonb_agg(to_jsonb(h) order by h.occurred_at desc,h.event_id desc) from history h),'[]'::jsonb),
    'historyTotal',(select count(*) from searches),'page',p_page,'pageSize',25)
  into result;
  return result;
end $$;

revoke all on function public.admin_usage_report(date,date,text,integer) from public,anon,authenticated;
grant execute on function public.admin_usage_report(date,date,text,integer) to service_role;
