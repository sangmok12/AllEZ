# Backend availability

Production API: `https://jobfitax-search.onrender.com` (Render Free).

Render Free spins down after 15 minutes without incoming requests. Starting the Java service again adds roughly a minute before a search can run. `/health` returns `{"status":"ok"}` without database access or analytics writes; defining this route alone does not schedule requests.

The primary keepalive is the named Supabase Cron job `allez-backend-keepalive` in the existing `jobfitax-analytics` project. It uses `pg_cron` and asynchronous `pg_net` to GET `/health` every five minutes (`*/5 * * * *`, UTC), with a 120-second timeout to allow a sleeping server to start. It runs in the database independently of a browser, this chat, or GitHub Actions. No API keys, user data, or paid service were added. Setup and restoration SQL is in `ops/backend-keepalive.sql`; re-running it updates the named job rather than adding a duplicate. Changing this file does not itself apply database configuration: run it against the intended Supabase project as a database administrator.

Verify both scheduling and HTTP completion in the Supabase SQL editor:

```sql
select jobid, jobname, schedule, active
from cron.job where jobname = 'allez-backend-keepalive';

select start_time, end_time, status, return_message
from cron.job_run_details
where jobid = (select jobid from cron.job where jobname = 'allez-backend-keepalive')
order by start_time desc limit 10;

-- pg_net is asynchronous: a successful cron run only confirms enqueueing.
-- Check the HTTP response as well. This project currently uses pg_net only
-- for this health job; if more HTTP jobs are added, correlate their request IDs.
select id, created, status_code, timed_out, error_msg, content
from net._http_response order by created desc limit 10;
```

Expected HTTP response: `200` and `{"status":"ok"}` with no timeout/error. HTTP responses are retained for six hours by pg_net. To disable only this job, run `select cron.unschedule('allez-backend-keepalive');`.

`.github/workflows/backend-health.yml` remains a best-effort backup JSON health check with scheduled and manual triggers. Actual GitHub run intervals were several hours despite the five-minute configuration; do not rely on this workflow for idle prevention. Scheduled GitHub Actions can be delayed or dropped, or disabled after prolonged repository inactivity.

This setup reduces idle spin-down while Supabase and Render are available; it is not an uptime guarantee. A paused/unavailable Supabase project cannot run cron, and Render Free can restart independently. Avoiding idle spin-down without keepalive requests requires an always-on compute plan.

The two part-time sources are currently fetched concurrently, but the combined response waits for both. Each source request has a 35-second timeout, so a slow source can delay the entire response even when the API server is already running. Neighborhood validation uses the same bundled original code list as the frontend and makes no external code API request. Part-time search collects all source pages only when the combined total is at most 1000; career search uses a combined limit of 2000. Both sort the complete collection and show local pages of 25 items. The collection has a 180-second work limit (plus an in-flight source request) and the frontend allows 240 seconds overall. The NDJSON progress frames include JSON whitespace padding and no-transform headers so small updates can pass through buffering intermediaries before the final result.

References: https://render.com/docs/free, https://supabase.com/docs/guides/cron, https://supabase.com/docs/guides/database/extensions/pg_net and https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule
