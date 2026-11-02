import { ButtonLink, EmptyState } from '../components/ui.jsx'

export default function NotFoundPage() {
  return (
    <EmptyState
      title="This page wandered off"
      description="The link may be old, or the event was removed."
      action={<ButtonLink to="/">Find events</ButtonLink>}
    />
  )
}
