import { useId } from 'react'
import { NavLink } from 'react-router-dom'
import './ui.css'

/*
 * The small in-house component set. Every screen is built from these, so the whole app keeps
 * the look of the SDD wireframes. Keep them plain: no shadows, no gradients, no icon soup.
 */

export function PageHeader({ title, subtitle, actions }) {
  return (
    <header className="page-header row" style={{ justifyContent: 'space-between', alignItems: 'flex-end' }}>
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="row">{actions}</div>}
    </header>
  )
}

export function Card({ title, subtitle, children, className = '', ...rest }) {
  return (
    <section className={`card ${className}`} {...rest}>
      {title && <h2 className="card-title">{title}</h2>}
      {subtitle && <p className="card-sub">{subtitle}</p>}
      {children}
    </section>
  )
}

export function Button({ variant = 'primary', block = false, className = '', type = 'button', ...rest }) {
  const cls = `btn btn-${variant}${block ? ' btn-block' : ''} ${className}`
  return <button type={type} className={cls} {...rest} />
}

/** Label above the control, as in every wireframe. The control is passed as children or as an input. */
export function Field({ label, required, error, hint, children, ...inputProps }) {
  const id = useId()
  return (
    <div className={`field${error ? ' has-error' : ''}`}>
      {label && (
        <label className="field-label" htmlFor={id}>
          {label}
          {required && ' *'}
        </label>
      )}
      {children ? (
        children(id)
      ) : (
        <input id={id} className="field-input" aria-invalid={!!error} {...inputProps} />
      )}
      {error ? <span className="field-error">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </div>
  )
}

export function Notice({ tone = 'warn', children, ...rest }) {
  const cls = tone === 'error' ? 'notice notice-error' : tone === 'ok' ? 'notice notice-ok' : 'notice'
  return (
    <div className={cls} role={tone === 'error' ? 'alert' : 'status'} {...rest}>
      {children}
    </div>
  )
}

export function Badge({ muted, tone, children }) {
  const cls = `badge${muted ? ' badge-muted' : ''}${tone ? ` badge-${tone}` : ''}`
  return <span className={cls}>{children}</span>
}

/** Second-level tabs inside a module, e.g. Assets · Accountable Persons · Departments. */
export function SubNav({ items }) {
  return (
    <nav className="subnav" aria-label="Section">
      {items.map((i) => (
        <NavLink key={i.to} to={i.to} end={i.end} className="subnav-item">
          {i.label}
        </NavLink>
      ))}
    </nav>
  )
}

/** A plain native select with the field look; options are [{ value, label }]. */
export function SelectField({ label, required, error, hint, value, onChange, options, placeholder, disabled }) {
  return (
    <Field label={label} required={required} error={error} hint={hint}>
      {(id) => (
        <select id={id} className="field-input" value={value ?? ''} onChange={onChange} disabled={disabled}
                aria-invalid={!!error}>
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  )
}

export function Pager({ page, totalPages, total, size, onPage }) {
  if (!total) return null
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return (
    <div className="pager">
      <span className="muted">
        {from}–{to} of {total}
      </span>
      <div className="row" style={{ gap: 8 }}>
        <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}
