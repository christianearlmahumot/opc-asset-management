import { useId } from 'react'
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

export function Badge({ muted, children }) {
  return <span className={`badge${muted ? ' badge-muted' : ''}`}>{children}</span>
}
