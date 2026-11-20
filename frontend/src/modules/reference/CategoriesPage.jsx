import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import AssetTabs from '../assets/AssetTabs'
import { Button, Card, Notice, PageHeader } from '../../shared/ui'
import { referenceApi } from './referenceApi'

/** Categories (CIT-U purchase request categories) and the clean list of units. Administrators add to them. */
export default function CategoriesPage() {
  const { can } = useAuth()
  const canEdit = can(['ADMIN'])
  const [categories, setCategories] = useState([])
  const [units, setUnits] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([referenceApi.categories(), referenceApi.units()])
      .then(([c, u]) => {
        setCategories(c)
        setUnits(u)
      })
      .catch((e) => setError(e.message))
  }, [])

  return (
    <>
      <PageHeader title="Categories & Units" subtitle="The lists every asset is encoded against" />
      <AssetTabs />
      {error && <Notice tone="error" style={{ marginBottom: 16 }}>{error}</Notice>}
      <div className="grid-2" style={{ alignItems: 'start' }}>
        <Card title="Categories" subtitle="Defaults follow the CIT-U purchase request categories.">
          <SimpleList items={categories.map((c) => c.name)} />
          {canEdit && (
            <AddRow placeholder="New category" onAdd={async (name) => {
              const c = await referenceApi.createCategory(name)
              setCategories((l) => [...l, c].sort((a, b) => a.name.localeCompare(b.name)))
            }} />
          )}
        </Card>
        <Card title="Units" subtitle="Spellings such as pcs and pieces are saved as pc.">
          <SimpleList items={units} />
          {canEdit && (
            <AddRow placeholder="New unit, e.g. ream" onAdd={async (u) => setUnits(await referenceApi.addUnit(u))} />
          )}
        </Card>
      </div>
    </>
  )
}

function SimpleList({ items }) {
  return (
    <ul className="plain-list">
      {items.map((i) => <li key={i}>{i}</li>)}
      <style>{`
        .plain-list { list-style: none; margin: 0 0 14px; padding: 0; }
        .plain-list li { padding: 9px 2px; border-bottom: 1px solid var(--line); }
      `}</style>
    </ul>
  )
}

function AddRow({ placeholder, onAdd }) {
  const [value, setValue] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    if (!value.trim()) return
    setBusy(true)
    setError('')
    try {
      await onAdd(value.trim())
      setValue('')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="stack" style={{ gap: 8 }}>
      <div className="row" style={{ gap: 8 }}>
        <input className="field-input" placeholder={placeholder} value={value} onChange={(e) => setValue(e.target.value)} />
        <Button type="submit" variant="secondary" disabled={busy || !value.trim()}>Add</Button>
      </div>
      {error && <span className="field-error">{error}</span>}
    </form>
  )
}
