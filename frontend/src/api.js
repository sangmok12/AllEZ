const apiBaseUrl = (
  import.meta.env.VITE_SEARCH_API_BASE_URL ||
  (import.meta.env.DEV ? '' : 'https://jobfitax-search.onrender.com')
).replace(/\/$/, '')

export function apiUrl(path) {
  return `${apiBaseUrl}${path}`
}
