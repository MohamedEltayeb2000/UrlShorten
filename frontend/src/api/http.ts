export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}

export async function http<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
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
    res = await fetch(path, { headers: { Accept: 'text/markdown' } })
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
