import { SubNav } from '../../shared/ui'

export default function IssueOrderTabs() {
  return (
    <SubNav items={[
      { to: '/issue-orders', label: 'Pending Requests', end: true },
      { to: '/issue-orders/history', label: 'Issue Order History' },
    ]} />
  )
}
