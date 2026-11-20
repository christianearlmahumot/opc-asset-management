import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { NAV } from '../../app/navigation'
import { useAuth } from '../../modules/auth/AuthContext'
import './AppShell.css'

/** Top bar + sidebar shared by every screen (all SDD UI figures). */
export default function AppShell() {
  const { user, can } = useAuth()
  const items = NAV.filter((n) => can(n.roles))

  return (
    <div className="shell">
      <header className="topbar">
        <span className="topbar-title">OPC Fixed-Asset Management System</span>
        <UserMenu name={shortName(user.fullName)} role={user.roleLabel} />
      </header>
      <nav className="sidebar" aria-label="Main">
        {items.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="nav-item">
            {n.label}
          </NavLink>
        ))}
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}

function UserMenu({ name, role }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  return (
    <div className="user-menu" ref={ref}>
      <button className="user-menu-btn" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        {name} · {role} <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="user-menu-pop" role="menu">
          <button role="menuitem" onClick={() => { setOpen(false); navigate('/change-password') }}>
            Change password
          </button>
          <button role="menuitem" onClick={logout}>
            Log out
          </button>
        </div>
      )}
    </div>
  )
}

/** "Lorna Cabahug" → "L. Cabahug", as in the wireframes. */
function shortName(full = '') {
  const parts = full.trim().split(/\s+/)
  if (parts.length < 2) return full
  return `${parts[0][0]}. ${parts[parts.length - 1]}`
}
