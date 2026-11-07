# AllEZ administrator dashboard

URL: https://allez-search.vercel.app/admin
No link is displayed in the public navigation. AdminPage never mounts JobSearch
and does not emit visitor/search/posting events.

Set ADMIN_USERNAME and ADMIN_PASSWORD only in the Render service environment.
No password or default password is committed to source.
POST /api/admin/login returns a random session token: 8 hours normally or 30 days\nwhen keep login is selected. The browser uses sessionStorage or localStorage\nrespectively. Token hashes persist in private admin_sessions, surviving server\nrestart. Logout revokes the session; a 401 returns the UI to login.
Login attempts are limited to 20 per minute per server instance.
Admin responses use Cache-Control: no-store. /admin adds noindex,nofollow.

GET /api/admin/report requires the session token and calls the Supabase RPC
admin_usage_report using the existing server-only secret. The RPC is SECURITY
INVOKER, with EXECUTE revoked from PUBLIC/anon/authenticated and granted to
service_role. Public table roles remain unable to read usage/search events.

Reports use inclusive Korea-time dates, up to 366 days, with 25 search records per
page. Overall visitors/visits deduplicate browsers/sessions across categories.
Daily totals deduplicate within each day. Category rows deduplicate within each
category; their sum is not necessarily the overall total.

The four metric cards use usage_events. History also includes earlier completed
search_events that have no matching search_click, so history count can differ
from the search-execution metric. Result count/outcome is the latest initial
batch response for that search ID, not cumulative load-more results.
Missing result responses are displayed as '결과 미수신'.

No administrator ID/password is sent to Supabase or embedded in the browser
bundle. The backend environment contains the credential supplied by the owner.
This release does not create Supabase Auth users or public signup.


Part-time search history now receives server result rows in search_events with
category=part_time, correlated with the browser search_click through search_id.
Only the initial batch supplies the report count/outcome; load-more rows do not
replace it. A successful zero-result search is recorded as 0/success, and source
failures retain failure/partial_failure. No jobkorea/saramin columns are reused
for part-time source counts. The database schema and private access remain unchanged.

History stores neighborhood_name alongside neighborhood_id in search_criteria.
The UI resolves legacy codes through the public neighborhood endpoint when a name
snapshot is absent. Two existing code-only records were enriched with names
verified from the native code lists: B0700130=인창동, I2400380=신당동. Their missing
past result counts were not fabricated. Part-time rows without stored responses
are labeled 결과 기록 없음.
