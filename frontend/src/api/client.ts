export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | number | undefined>,
  headers?: Record<string, string>,
): Promise<T> {
  const url = new URL(path, BASE_URL)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) url.searchParams.set(key, String(value))
    }
  }

  const response = await fetch(url, headers ? { headers } : undefined)
  if (!response.ok) {
    throw new Error(`API 요청 실패: ${response.status} ${url.pathname}`)
  }
  return response.json() as Promise<T>
}
