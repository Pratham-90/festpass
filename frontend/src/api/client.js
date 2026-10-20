// Small fetch wrapper. Every API call goes through here so auth, CSRF and
// ProblemDetail error handling can be added in one place in later phases.

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.code = problem?.code
    this.problem = problem
  }
}

export async function api(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    credentials: 'same-origin',
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  })
  const isJson = res.headers.get('content-type')?.includes('json')
  const body = isJson ? await res.json() : null
  if (!res.ok) throw new ApiError(res.status, body)
  return body
}
