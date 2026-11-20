import { Link } from 'react-router-dom'
import { NAV } from '../../app/navigation'
import { useAuth } from '../auth/AuthContext'
import { Card, PageHeader } from '../../shared/ui'

/*
 * Dashboard. For now it only lists what the signed-in role can do. Real figures (assets in storage,
 * pending requests, recent movements) are added once those modules exist, so no number here is invented.
 */
export default function DashboardPage() {
  const { user, can } = useAuth()
  const items = NAV.filter((n) => n.to !== '/' && can(n.roles))

  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Signed in as ${user.fullName} · ${user.roleLabel}`} />
      <Card title="Your work areas" subtitle="What your role can open in this system.">
        <ul className="dash-list">
          {items.map((n) => (
            <li key={n.to}>
              <Link to={n.to}>{n.label}</Link>
            </li>
          ))}
        </ul>
      </Card>
      <style>{`
        .dash-list { list-style: none; margin: 0; padding: 0; }
        .dash-list li { border-top: 1px solid var(--line); }
        .dash-list li:first-child { border-top: 0; }
        .dash-list a { display: block; padding: 12px 2px; text-decoration: none; color: var(--text); font-weight: 500; }
        .dash-list a:hover { color: var(--maroon); }
      `}</style>
    </>
  )
}
