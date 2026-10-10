// Thin API client. The active demo company travels in X-Demo-Company-Id (simulated access, not authentication).
const BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

let activeCompanyId: string | null = null
export function setActiveCompanyId(id: string | null) {
  activeCompanyId = id
}

export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (activeCompanyId) headers['X-Demo-Company-Id'] = activeCompanyId
  let body = init.body
  if (init.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(init.json)
  }
  let res: Response
  try {
    res = await fetch(BASE + path, { ...init, headers: { ...headers, ...(init.headers as Record<string, string>) }, body })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the TenderReady backend. Is it running on port 8090?')
  }
  if (res.status === 204) return null as T
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    const d = data as { message?: string; code?: string } | null
    throw new ApiError(res.status, d?.code ?? 'ERROR', d?.message ?? `Request failed (HTTP ${res.status})`)
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return { message: text.slice(0, 300) }
  }
}
