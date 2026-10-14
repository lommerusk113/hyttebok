// Local YYYY-MM-DD (toISOString would shift dates across midnight in UTC+1/+2). No imports, so plain Node can run checks on it.
export const toIsoDate = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
export const parseIsoDate = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d) }
