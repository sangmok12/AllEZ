-- Run as a database administrator in the AllEZ Supabase project.
-- The named job is updated in place when this script is run again.
-- Uses the public health endpoint: no API keys or user data are transmitted.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.schedule(
  'allez-backend-keepalive',
  '*/5 * * * *',
  $job$
  select net.http_get(
    url := 'https://jobfitax-search.onrender.com/health',
    headers := '{"User-Agent":"AllEZ-HealthMonitor/1.0","Cache-Control":"no-cache"}'::jsonb,
    timeout_milliseconds := 120000
  );
  $job$
);

-- To stop this job without removing extensions or other jobs:
-- select cron.unschedule('allez-backend-keepalive');
