import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { useLogin, useRegister } from '../auth/auth.js'
import { Button, Card, Field, inputClass } from '../components/ui.jsx'
import { useToast } from '../components/Toasts.jsx'

/** Only follow ?next= links within this site. */
function safeNext(value) {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="mx-auto max-w-md">
      <Card className="p-6 sm:p-8">
        <h1 className="font-display text-3xl font-black">{title}</h1>
        <p className="mt-1 text-ink-muted">{subtitle}</p>
        <div className="mt-6">{children}</div>
      </Card>
      <p className="mt-4 text-center text-sm text-ink-muted">{footer}</p>
    </div>
  )
}

export function LoginPage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const navigate = useNavigate()
  const toast = useToast()
  const login = useLogin()
  const [form, setForm] = useState({ email: '', password: '' })

  const submit = (e) => {
    e.preventDefault()
    login.mutate(form, {
      onSuccess: (user) => {
        toast.success(`Welcome back, ${user.name.split(' ')[0]}!`)
        navigate(next, { replace: true })
      },
    })
  }

  return (
    <AuthCard
      title="Log in"
      subtitle="Your tickets are waiting."
      footer={
        <>
          New here?{' '}
          <Link to={`/register?next=${encodeURIComponent(next)}`} className="font-semibold text-accent">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Email">
          <input type="email" required autoComplete="email" className={inputClass} value={form.email}
                 onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password">
          <input type="password" required autoComplete="current-password" className={inputClass} value={form.password}
                 onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        {login.isError && (
          <p className="text-sm text-danger" role="alert">
            {login.error.code === 'BAD_CREDENTIALS' ? 'Email or password is incorrect.' : login.error.message}
          </p>
        )}
        <Button type="submit" loading={login.isPending}>Log in</Button>
      </form>
    </AuthCard>
  )
}

export function RegisterPage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const navigate = useNavigate()
  const toast = useToast()
  const register = useRegister()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const errors = register.error?.fieldErrors ?? {}

  const submit = (e) => {
    e.preventDefault()
    register.mutate(form, {
      onSuccess: (user) => {
        toast.success(`You're in, ${user.name.split(' ')[0]}!`, 'Your FestPass account is ready.')
        navigate(next, { replace: true })
      },
    })
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="One account for every fest on campus."
      footer={
        <>
          Already have one?{' '}
          <Link to={`/login?next=${encodeURIComponent(next)}`} className="font-semibold text-accent">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Name" error={errors.name}>
          <input required autoComplete="name" className={inputClass} value={form.name}
                 onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Field>
        <Field label="Email" error={errors.email}>
          <input type="email" required autoComplete="email" className={inputClass} value={form.email}
                 onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password" error={errors.password} hint="At least 8 characters.">
          <input type="password" required minLength={8} autoComplete="new-password" className={inputClass} value={form.password}
                 onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        {register.isError && register.error.code === 'EMAIL_TAKEN' && (
          <p className="text-sm text-danger" role="alert">
            That email is already registered. <Link to="/login" className="font-semibold underline">Log in instead?</Link>
          </p>
        )}
        <Button type="submit" loading={register.isPending}>Create account</Button>
      </form>
    </AuthCard>
  )
}
