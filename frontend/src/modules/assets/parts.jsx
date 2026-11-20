import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '../../shared/ui'
import { formatDate } from '../../shared/format'
import { assetsApi } from './assetsApi'

/** Condition pill as in UI-205. Defective and damaged stand out; good is the quiet default. */
export function ConditionBadge({ condition, label }) {
  const tone = condition === 'DAMAGED' ? 'bad' : condition === 'DEFECTIVE' ? 'warn' : undefined
  return <Badge tone={tone}>{label}</Badge>
}

export function StatusBadge({ status, label }) {
  if (status === 'DRAFT') return <Badge tone="warn">Draft</Badge>
  return <Badge muted>{label}</Badge>
}

/** "2026-10-02 · Condition · Good Condition", newest first (UI-205 history panel). */
export function HistoryList({ entries, loading }) {
  if (loading) return <p className="muted">Loading history…</p>
  if (!entries?.length) return <p className="muted">No movements recorded yet.</p>
  return (
    <ul className="history">
      {entries.map((h) => (
        <li key={h.id}>
          <span className="num">{formatDate(h.at)}</span> · <strong>{h.typeLabel}</strong>
          {h.summary && h.summary !== h.typeLabel && <> · {h.summary}</>}
          {h.splitAssetId && <> · <Link to={`/assets/${h.splitAssetId}`}>open split item</Link></>}
          {h.recordedBy && <div className="history-by">recorded by {h.recordedBy}</div>}
        </li>
      ))}
    </ul>
  )
}

/** Loads the asset photo through the API (it needs the login token) and shows it. */
export function AssetPhoto({ assetId, version, alt }) {
  const [url, setUrl] = useState(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let objectUrl
    let alive = true
    setFailed(false)
    assetsApi
      .photoBlob(assetId)
      .then((blob) => {
        if (!alive) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => alive && setFailed(true))
    return () => {
      alive = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [assetId, version])

  if (failed) return <div className="photo-box">Photo unavailable</div>
  if (!url) return <div className="photo-box">Loading photo…</div>
  return <img className="photo-img" src={url} alt={alt} />
}
