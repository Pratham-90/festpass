import { useState } from 'react'
import { Button, Field, inputClass } from '../../components/ui.jsx'
import { CATEGORIES, fromLocalInput, titleCase, toLocalInput } from '../../lib/format.js'

const EMPTY = {
  title: '',
  description: '',
  category: 'MUSIC',
  bannerUrl: '',
  venueName: '',
  startsAt: '',
  endsAt: '',
  saleOpensAt: '',
  saleClosesAt: '',
  seatingMode: 'SEATED',
  holdMinutes: 5,
  maxTicketsPerUser: 4,
  flashDrop: false,
}

function fromEvent(event) {
  if (!event) return EMPTY
  return {
    ...EMPTY,
    ...event,
    description: event.description ?? '',
    bannerUrl: event.bannerUrl ?? '',
    startsAt: toLocalInput(event.startsAt),
    endsAt: toLocalInput(event.endsAt),
    saleOpensAt: toLocalInput(event.saleOpensAt),
    saleClosesAt: toLocalInput(event.saleClosesAt),
  }
}

/** Create/edit form for an event's details. Field errors come from the API's VALIDATION_FAILED response. */
export default function EventForm({ event, onSubmit, submitting, error, submitLabel, seatingLocked }) {
  const [form, setForm] = useState(() => fromEvent(event))
  const errors = error?.fieldErrors ?? {}
  const set = (field) => (e) =>
    setForm({ ...form, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = (e) => {
    e.preventDefault()
    const fields = Object.fromEntries(Object.keys(EMPTY).map((key) => [key, form[key]]))
    onSubmit({
      ...fields,
      description: form.description || null,
      bannerUrl: form.bannerUrl || null,
      startsAt: fromLocalInput(form.startsAt),
      endsAt: fromLocalInput(form.endsAt),
      saleOpensAt: fromLocalInput(form.saleOpensAt),
      saleClosesAt: fromLocalInput(form.saleClosesAt),
      holdMinutes: Number(form.holdMinutes),
      maxTicketsPerUser: Number(form.maxTicketsPerUser),
    })
  }

  return (
    <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">
        <Field label="Title" error={errors.title}>
          <input required maxLength={150} className={inputClass} value={form.title} onChange={set('title')} />
        </Field>
      </div>
      <Field label="Category" error={errors.category}>
        <select className={inputClass} value={form.category} onChange={set('category')}>
          {CATEGORIES.map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}
        </select>
      </Field>
      <Field label="Venue" error={errors.venueName}>
        <input required maxLength={150} className={inputClass} value={form.venueName} onChange={set('venueName')} />
      </Field>
      <div className="sm:col-span-2">
        <Field label="Description" error={errors.description}>
          <textarea rows={4} maxLength={5000} className={inputClass} value={form.description} onChange={set('description')} />
        </Field>
      </div>
      <div className="sm:col-span-2">
        <Field label="Banner image URL (optional)" error={errors.bannerUrl} hint="Leave empty for a gradient in the event's colour.">
          <input type="url" className={inputClass} value={form.bannerUrl} onChange={set('bannerUrl')} placeholder="https://…" />
        </Field>
      </div>
      <Field label="Starts" error={errors.startsAt}>
        <input type="datetime-local" required className={inputClass} value={form.startsAt} onChange={set('startsAt')} />
      </Field>
      <Field label="Ends" error={errors.endsAt}>
        <input type="datetime-local" required className={inputClass} value={form.endsAt} onChange={set('endsAt')} />
      </Field>
      <Field label="Sale opens" error={errors.saleOpensAt}>
        <input type="datetime-local" required className={inputClass} value={form.saleOpensAt} onChange={set('saleOpensAt')} />
      </Field>
      <Field label="Sale closes" error={errors.saleClosesAt}>
        <input type="datetime-local" required className={inputClass} value={form.saleClosesAt} onChange={set('saleClosesAt')} />
      </Field>
      <Field label="Seating" error={errors.seatingMode}
             hint={seatingLocked ? "Can't change once tiers exist." : 'Seated events get a seat map; general admission sells a number of tickets.'}>
        <select className={inputClass} value={form.seatingMode} onChange={set('seatingMode')} disabled={seatingLocked}>
          <option value="SEATED">Seated</option>
          <option value="GENERAL">General admission</option>
        </select>
      </Field>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Hold (min)" error={errors.holdMinutes}>
          <input type="number" min={1} max={30} className={inputClass} value={form.holdMinutes} onChange={set('holdMinutes')} />
        </Field>
        <Field label="Max per person" error={errors.maxTicketsPerUser}>
          <input type="number" min={1} max={20} className={inputClass} value={form.maxTicketsPerUser} onChange={set('maxTicketsPerUser')} />
        </Field>
      </div>
      <label className="flex items-center gap-3 sm:col-span-2">
        <input type="checkbox" className="size-5 accent-accent" checked={form.flashDrop} onChange={set('flashDrop')} />
        <span>
          <span className="font-semibold">⚡ Flash drop</span>
          <span className="block text-sm text-ink-muted">Buyers join a waiting room and are let in in batches when the sale opens.</span>
        </span>
      </label>
      {error && !Object.keys(errors).length && <p className="text-sm text-danger sm:col-span-2">{error.message}</p>}
      <div className="sm:col-span-2">
        <Button type="submit" loading={submitting}>{submitLabel}</Button>
      </div>
    </form>
  )
}
