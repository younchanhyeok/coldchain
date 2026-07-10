export interface AuthTokens {
  accessToken: string
  refreshToken: string
  role: string
  companyName: string
}

const ACCESS_KEY = 'coldchain.accessToken'
const REFRESH_KEY = 'coldchain.refreshToken'
const ROLE_KEY = 'coldchain.role'
const COMPANY_NAME_KEY = 'coldchain.companyName'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function getRole(): string | null {
  return localStorage.getItem(ROLE_KEY)
}

export function getCompanyName(): string | null {
  return localStorage.getItem(COMPANY_NAME_KEY)
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null
}

export function setTokens(tokens: AuthTokens): void {
  localStorage.setItem(ACCESS_KEY, tokens.accessToken)
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
  localStorage.setItem(ROLE_KEY, tokens.role)
  localStorage.setItem(COMPANY_NAME_KEY, tokens.companyName)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem(COMPANY_NAME_KEY)
}
