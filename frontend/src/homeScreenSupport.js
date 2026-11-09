export const MOBILE_SITE_URL = 'https://allez-search.vercel.app/'
export const HOME_HINT_KEY = 'allez:home-hint-dismissed:v1'

export function mobilePlatform(browser) {
  const userAgent = browser.userAgent || ''
  if (/iPhone|iPad|iPod/i.test(userAgent) || (/Macintosh/i.test(userAgent) && browser.maxTouchPoints > 1)) return 'ios'
  if (/Android/i.test(userAgent)) return 'android'
  return browser.userAgentData?.mobile ? 'mobile' : null
}

export function isStandalone(browser, standaloneDisplay) {
  return browser.standalone === true || standaloneDisplay === true
}
