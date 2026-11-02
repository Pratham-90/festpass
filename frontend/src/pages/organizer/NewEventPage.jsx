import { useNavigate } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/client.js'
import { keys } from '../../api/queries.js'
import { Card, PageTitle } from '../../components/ui.jsx'
import { useToast } from '../../components/Toasts.jsx'
import EventForm from './EventForm.jsx'

export default function NewEventPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const create = useMutation({
    mutationFn: (body) => api('/organizer/events', { method: 'POST', body }),
    onSuccess: (event) => {
      queryClient.invalidateQueries({ queryKey: keys.orgEvents })
      toast.success('Draft created', 'Now add ticket tiers and a seat layout.')
      navigate(`/organizer/events/${event.id}`)
    },
    onError: (error) => error.code !== 'VALIDATION_FAILED' && toast.error(error),
  })

  return (
    <div className="mx-auto max-w-3xl">
      <PageTitle eyebrow="Organizer" title="New event" />
      <Card>
        <EventForm onSubmit={create.mutate} submitting={create.isPending} error={create.error} submitLabel="Create draft" />
      </Card>
    </div>
  )
}
