# AllEZ usage analytics

Private tables in the existing Supabase project: `usage_events` and `search_events`.
Only the backend secret may INSERT. No public analytics read API is provided.
Dashboard: https://supabase.com/dashboard/project/oxeyzygjhsuabhmcwbih/editor

## Definitions

- category: jobs (채용), part_time (알바), homes (부동산). Only jobs is wired today.
- visitor_id: random UUID stored in localStorage; identifies a browser profile/origin, not a person.
- session_id: shared across tabs and reloads; renewed after 30 minutes without pointer/keyboard activity or tracked actions.
- page_view: emitted once per document/category/session; a new session is also announced on the next tracked activity.
- Unique visitors: count DISTINCT visitor_id in the selected period.
- Visits: count DISTINCT session_id. Reloading records another page_view with the same session and does not inflate visits.
- search_click: valid new form submission, by button or Enter. Invalid input, retries and load-more are excluded.
- posting_click: title activation including middle-click. Repeated openings are counted. Opening via the browser context menu is not reliably captured.
- search_id joins the initial submission, source responses/load-more in search_events, and subsequent posting clicks.
- search_events rows created before this release have NULL visitor_id/session_id; do not backfill fabricated identities.
- Overall unique visitors/visits must be DISTINCT across categories; do not add category totals.
- GET /health returns status only, does not query the DB and never generates analytics.
- No IP address, user agent, full page/referrer URL, posting title, or contact details are collected by this feature. Search keywords undergo email/phone masking on the server; this is not comprehensive PII detection.
- Browser data deletion, different devices/profiles and domains create different visitors.
- No login/admin dashboard is added in this release. Inspect tables or run the private SQL below.
- Browser delivery retries up to twice with the same event_id. INSERT ignores duplicate IDs.
- Delivery remains best effort: blockers, offline browsing, browser close, server restarts, a full writer queue, or DB outages can drop records.
- Client-submitted events are not audited identities and can include bots or spoofed data. Counts are estimates.
- Automatic retention has not been configured.

## Period summary by section (Korea time)

Adjust the date bounds as needed. End is exclusive.
```sql
select category,
  count(distinct visitor_id) filter (where event_type = 'page_view') as unique_visitors,
  count(distinct session_id) filter (where event_type = 'page_view') as visits,
  count(*) filter (where event_type = 'page_view') as page_views,
  count(*) filter (where event_type = 'search_click') as search_clicks,
  count(*) filter (where event_type = 'posting_click') as posting_clicks
from public.usage_events
where occurred_at >= '2026-11-05 00:00:00+09'::timestamptz
  and occurred_at <  '2026-11-06 00:00:00+09'::timestamptz
group by category
order by category;
```

## Search history (includes submitted searches even if result fetching failed)

```sql
select occurred_at at time zone 'Asia/Seoul' as searched_at,
       category, visitor_id, session_id, search_id, search_criteria
from public.usage_events
where event_type = 'search_click'
order by occurred_at desc
limit 100;
```

## Result requests for one search

Copy a search_id from the history and filter search_events by it.
Each row is one completed backend request, not a button click.
Existing per-source count, outcome and duration columns remain available.

## Repeated visits from the same browser

```sql
select visitor_id, count(distinct session_id) as visits,
       min(occurred_at) as first_seen, max(occurred_at) as last_seen
from public.usage_events
where event_type = 'page_view'
group by visitor_id
order by visits desc;
```
