import type { AuthTokens } from '../lib/auth'
import { apiPost } from './client'

export function login(email: string, password: string): Promise<AuthTokens> {
  return apiPost<AuthTokens>('/api/v1/auth/login', { email, password }, { skipAuth: true })
}
