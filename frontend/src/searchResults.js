export function mergeSearchPage(previous, data, criteria) {
  const byId = new Map((previous?.items || []).map((item) => [`${item.source}-${item.postingId}`, item]))
  for (const item of data.items) {
    const key = `${item.source}-${item.postingId}`
    if (!byId.has(key)) byId.set(key, item)
  }
  const items = [...byId.values()]
  const sites = new Map((previous?.sites || []).map((site) => [site.source, site]))
  for (const site of data.sites) {
    const old = sites.get(site.source)
    sites.set(site.source, {
      ...site,
      totalAvailable: site.status === 'FAILED' ? (old?.totalAvailable || 0) : site.totalAvailable,
      returnedCount: items.filter((item) => item.source === site.source).length,
    })
  }
  return { items, sites: [...sites.values()], criteria }
}

export function nextSearchPages(result) {
  return {
    jobKoreaPage: result.sites.find((site) => site.source === 'JOBKOREA')?.nextPage ?? null,
    saraminPage: result.sites.find((site) => site.source === 'SARAMIN')?.nextPage ?? null,
  }
}
