# Admin login preferences

- ID saving stores only the username in localStorage.
- Keep login stores the random token and expiry in localStorage for up to 30 days.
- Without keep login, the token stays in sessionStorage and expires after 8 hours.
- Passwords are not saved by this feature.
- Logout clears tokens from both browser stores and deletes the matching server session.
- Only SHA-256 token hashes and expiration timestamps are stored in private admin_sessions.
- The runtime uses Supabase for sessions, so Render restarts do not invalidate valid saved sessions.
- Expired sessions are rejected and removed when used.
- Browser data deletion or a different browser/domain requires login again.
- Administrator environment credentials remain unchanged.
- Mobile date controls use zero-minimum grid tracks and bounded input widths, including WebKit date rendering.
