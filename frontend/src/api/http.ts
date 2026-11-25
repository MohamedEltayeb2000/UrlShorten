export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}


const SEGMENT = /^[A-Za-z0-9_-]{1,64}$/
const SAFE_API_PATH = /^\/api(\/[A-Za-z0-9_-]+)+(\?[A-Za-z0-9_=&%.,:-]*)?$/

/** Validates one URL path segment (an id, link code or stage name) before it is placed into a request path. */
export function seg(value: string | number): string {
  const s = String(value)
  if (!SEGMENT.test(s)) throw new ApiError(0, 'INVALID_ARGUMENT', 'Invalid identifier')
  return s
}

/** Validates a numeric query value (page, size); only small non-negative integers are allowed. */
export function int(value: number): number {
  if (!Number.isSafeInteger(value) || value < 0 || value > 100000) throw new ApiError(0, 'INVALID_ARGUMENT', 'Invalid number')
  return value
}

/** Only same-origin /api/... paths made of safe characters may be requested (no scheme, host, traversal or encoded separators). */
function safePath(path: string): string {
  if (!SAFE_API_PATH.test(path)) throw new ApiError(0, 'INVALID_PATH', 'Invalid request path')
  return path.replace(/[^A-Za-z0-9_\-/?=&%.,:]/g, '')
}

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(safePath(path), {
      ...init,
      headers: { 'Content-Type': 'application/json', ...((init?.headers as Record<string, string>) ?? {}) },
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running?')
  }
  if (!res.ok) {
    let code = 'ERROR'
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      code = body?.error?.code ?? code
      message = body?.error?.message ?? message
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, code, message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** Fetch a plain-text (e.g. markdown) body. */
export async function httpText(path: string): Promise<string> {
  let res: Response
  try {
    res = await fetch(safePath(path), { headers: { Accept: 'text/markdown' } })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Is the backend running?')
  }
  if (!res.ok) {
    let code = 'ERROR'
    let message = `Request failed (${res.status})`
    try {
      const body = await res.json()
      code = body?.error?.code ?? code
      message = body?.error?.message ?? message
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, code, message)
  }
  return res.text()
}

/** Download text as a file via a Blob and a temporary anchor. */
export function saveText(text: string, filename: string, type = 'text/markdown') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
