-- Required to use ON CONFLICT(event_id) without granting access to usage details.
grant select (event_id) on public.usage_events to service_role;
