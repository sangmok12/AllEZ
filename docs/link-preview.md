# Link previews

The public entry HTML contains Open Graph and Twitter Card metadata so link
scrapers can read it without executing React. The canonical public URL is
https://allez-search.vercel.app/ and the share image is
https://allez-search.vercel.app/og-allez-v1.png (1200×630 PNG).

The editable image design is `ops/og/allez-og.html`. Render it in Chromium with
Noto Sans CJK KR installed at a 1200×630 viewport and device scale factor 1.
Commit the resulting PNG in `frontend/public`; no runtime image service is needed.
When replacing the design, use a new image filename and update the metadata.

Verify the deployed HTML and image return HTTP 200 without authentication,
including with a Kakao scraper user agent. Kakao may retain the preview of an
already shared URL. If the preview remains stale, use Kakao Developers' URL
metadata/cache tool and share the link again. An existing chat message may retain
its previous preview. Do not treat an HTTP check as proof of an actual Kakao chat
preview; confirm that final appearance in KakaoTalk.

Official guide: https://developers.kakao.com/docs/ko/message-template/faq
