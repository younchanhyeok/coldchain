import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../lib/auth'

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

interface RequestOptions {
  /** 인증 헤더 자동 주입과 401 시 refresh 재시도를 건너뛴다 — 로그인/refresh 자체, 매직링크
   *  뷰(/t/{token})나 어드민 키 전용 엔드포인트처럼 애초에 JWT 개념이 없는 요청에 쓴다. */
  skipAuth?: boolean
  headers?: Record<string, string>
}

let refreshPromise: Promise<boolean> | null = null

function authHeader(): Record<string, string> {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// 동시에 여러 요청이 401을 맞아도 refresh 호출은 하나만 나가도록 진행 중인 Promise를 공유한다.
export function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) return false
      try {
        const response = await fetch(new URL('/api/v1/auth/refresh', BASE_URL), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (!response.ok) return false
        setTokens(await response.json())
        return true
      } catch {
        return false
      }
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

function redirectToLogin(): void {
  clearTokens()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

async function fetchWithAuth(url: URL, init: RequestInit, options: RequestOptions): Promise<Response> {
  const headers = { ...init.headers, ...(options.skipAuth ? {} : authHeader()), ...options.headers }
  const response = await fetch(url, { ...init, headers })

  if (response.status !== 401 || options.skipAuth) {
    return response
  }

  const refreshed = await refreshAccessToken()
  if (!refreshed) {
    redirectToLogin()
    return response
  }

  return fetch(url, { ...init, headers: { ...init.headers, ...authHeader(), ...options.headers } })
}

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | number | undefined>,
  options: RequestOptions = {},
): Promise<T> {
  const url = new URL(path, BASE_URL)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) url.searchParams.set(key, String(value))
    }
  }

  const response = await fetchWithAuth(url, {}, options)
  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status} ${url.pathname}`)
  }
  return response.json() as Promise<T>
}

export async function apiPost<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  const url = new URL(path, BASE_URL)
  const response = await fetchWithAuth(
    url,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    },
    options,
  )
  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status} ${url.pathname}`)
  }
  return response.json() as Promise<T>
}
