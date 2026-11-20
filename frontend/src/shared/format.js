/*
 * Display formats used on the OPC forms: dates as 2026-11-16, amounts as 7,400.00 (peso sign only
 * where the form shows it).
 */

const money = new Intl.NumberFormat('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  return Number.isFinite(n) ? money.format(n) : '—'
}

/** "7,400.50" or "7400.5" → "7400.50"; returns '' if not a number. */
export function parseMoney(text) {
  const cleaned = String(text ?? '').replace(/[,\s₱]/g, '')
  if (cleaned === '') return ''
  const n = Number(cleaned)
  return Number.isFinite(n) ? n.toFixed(2) : ''
}

export function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

export function formatDateTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export const dash = (v) => (v === null || v === undefined || v === '' ? '—' : v)
