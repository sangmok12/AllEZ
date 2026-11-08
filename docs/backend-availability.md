# Backend availability

Production API: `https://jobfitax-search.onrender.com` (Render Free).

Render Free spins down after 15 minutes without incoming requests. Starting the Java service again adds roughly a minute before a search can run. `/health` returns `{"status":"ok"}` without database access or analytics writes; defining this route alone does not schedule requests.

`.github/workflows/backend-health.yml` requests `/health` every five minutes from GitHub Actions and checks the JSON response, with a manual `workflow_dispatch` trigger. It needs no repository checkout, secrets, or repository permissions. Check **Actions → Backend health monitor** for actual run times, response duration, and failures. Changes to this workflow must be on the default branch before scheduled execution starts.

Scheduled GitHub Actions can be delayed or disabled, including after 60 days without repository activity in public repositories. This is a best-effort monitor and idle-prevention measure, not an uptime guarantee. Render Free can also restart independently. For guaranteed avoidance of idle spin-down, use a paid Render compute plan.

The two part-time sources are currently fetched concurrently, but the combined response waits for both. Each source request has a 35-second timeout, so a slow source can delay the entire response even when the API server is already running. Neighborhood validation uses the same bundled original code list as the frontend and makes no external code API request. The frontend allows up to 150 seconds for the overall request, including server startup.

References: https://render.com/docs/free and https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule
