// Every API call goes through here: JSON in and out, the CSRF header on writes,
// and errors turned into ApiError with the server's stable `code` (see docs/api.md).

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.code = problem?.code
    this.problem = problem ?? {}
    // Field-level messages for VALIDATION_FAILED, e.g. { email: 'must be a well-formed email address' }
    this.fieldErrors = problem?.errors ?? {}
  }
}

function readCookie(name) {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

const WRITES = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

async function send(path, { method, body, headers }) {
  const init = {
    method,
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...headers },
  }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  if (WRITES.has(method)) {
    // The server sets XSRF-TOKEN on every response; read it fresh, because login replaces it.
    const token = readCookie('XSRF-TOKEN')
    if (token) init.headers['X-XSRF-TOKEN'] = token
  }
  return fetch(`/api${path}`, init)
}

async function parse(res) {
  if (res.status === 204) return null
  const type = res.headers.get('content-type') || ''
  return type.includes('json') ? res.json() : null
}

/**
 * api('/events') or api('/orders', { method: 'POST', body: {...}, headers: {...} }).
 * Resolves with the parsed JSON (null for 204), rejects with ApiError.
 */
export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  let res = await send(path, { method, body, headers })
  let data = await parse(res)

  // No CSRF cookie yet (first request of the visit) or a stale one: fetch a fresh token once and retry.
  if (res.status === 403 && data?.code === 'CSRF_INVALID') {
    await fetch('/api/health', { credentials: 'same-origin' })
    res = await send(path, { method, body, headers })
    data = await parse(res)
  }

  if (!res.ok) throw new ApiError(res.status, data)
  return data
}
