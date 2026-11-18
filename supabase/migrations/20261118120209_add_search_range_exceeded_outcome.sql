-- Preserve existing outcomes and distinguish an intentional collection limit from errors.
alter table public.search_events
  drop constraint search_events_outcome_check,
  add constraint search_events_outcome_check
    check (outcome in ('success', 'partial_failure', 'failure', 'range_exceeded'));
