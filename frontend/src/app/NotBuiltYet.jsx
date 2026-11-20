import { Card, PageHeader } from '../shared/ui'

/** Stands in for modules whose build step has not landed yet. */
export default function NotBuiltYet({ label, step }) {
  return (
    <>
      <PageHeader title={label} />
      <Card>
        <p style={{ margin: 0, color: 'var(--text-muted)' }}>
          This module is scheduled for build step {step}. See docs/BUILD_PLAN.md.
        </p>
      </Card>
    </>
  )
}
