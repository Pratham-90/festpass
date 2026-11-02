// Friendly messages for every error code the API sends (docs/api.md, "Errors").

const MESSAGES = {
  SEAT_UNAVAILABLE: { title: 'Someone just grabbed that seat!', description: 'Pick another one, they go fast.' },
  SOLD_OUT: { title: 'Sold out', description: 'Not enough tickets left in that tier.' },
  LIMIT_EXCEEDED: { title: 'Ticket limit reached', description: "You've already got the maximum for this event." },
  HOLD_EXPIRED: { title: 'Your hold expired', description: "The seats went back on sale. You weren't charged." },
  NOT_ADMITTED: { title: 'Not your turn yet', description: 'Join the waiting room and wait to be let in.' },
  SALE_NOT_OPEN: { title: "Sales aren't open", description: 'Check the countdown on the event page.' },
  RATE_LIMITED: { title: 'Slow down a little', description: 'Too many tries. Wait a moment and try again.' },
  VALIDATION_FAILED: { title: 'Check the form', description: 'Some fields need fixing.' },
  PAYMENT_DECLINED: { title: 'Payment declined', description: 'Try again while your seats are still held.' },
  UNAUTHENTICATED: { title: 'Please log in', description: 'Your session may have ended.' },
  BAD_CREDENTIALS: { title: 'Wrong email or password' },
  FORBIDDEN: { title: "You can't do that", description: "Your account doesn't have access." },
  CSRF_INVALID: { title: 'Please refresh the page', description: 'Your session token went stale.' },
  EMAIL_TAKEN: { title: 'That email is already registered', description: 'Try logging in instead.' },
  NOT_FOUND: { title: 'Not found', description: "It may have been removed, or the link is wrong." },
  INVALID_STATE: { title: "That can't be done right now" },
  CONFLICT: { title: 'That clashes with existing data' },
  INTERNAL_ERROR: { title: 'Something went wrong on our side', description: 'Please try again.' },
}

/** { title, description } for an error, preferring the server's own detail for state errors. */
export function messageFor(error) {
  if (!error?.code) {
    return { title: "Can't reach FestPass", description: 'Check your connection and try again.' }
  }
  const known = MESSAGES[error.code] ?? { title: 'Something went wrong' }
  // These codes carry a specific, human-readable reason from the server.
  const useServerDetail = ['INVALID_STATE', 'SALE_NOT_OPEN', 'LIMIT_EXCEEDED'].includes(error.code)
  return {
    title: known.title,
    description: useServerDetail && error.message ? error.message : known.description,
  }
}
