import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate, type Location } from 'react-router-dom'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { setTokens } from '../lib/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const tokens = await login(email, password)
      setTokens(tokens)
      const from = (location.state as { from?: Location } | null)?.from
      navigate(from ? `${from.pathname}${from.search}` : '/', { replace: true })
    } catch (err) {
      // 401(자격 증명 오류)만 비밀번호 탓으로 — 백엔드 다운·500을 같은 메시지로 내면
      // 데모 중 서버가 안 떠 있는 상황을 비밀번호 오류로 오독하게 된다(리뷰 반영).
      if (err instanceof ApiError && err.status === 401) {
        setError('이메일 또는 비밀번호가 올바르지 않습니다.')
      } else {
        setError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-body text-neutral-100">
      <form
        onSubmit={handleSubmit}
        className="w-[360px] rounded-card border border-border bg-card p-8 shadow-card"
      >
        <div className="mb-6 text-lg font-semibold tracking-tight">❄ ColdChain 화주 로그인</div>
        <div className="flex flex-col gap-3">
          <input
            type="email"
            placeholder="이메일"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
            className="h-11 rounded-control border border-border bg-body px-3 text-sm text-neutral-200 placeholder:text-neutral-600"
          />
          <input
            type="password"
            placeholder="비밀번호"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="h-11 rounded-control border border-border bg-body px-3 text-sm text-neutral-200 placeholder:text-neutral-600"
          />
        </div>
        {error && <div className="mt-3 text-sm text-danger">{error}</div>}
        <button
          type="submit"
          disabled={submitting}
          className="mt-6 h-11 w-full rounded-control bg-primary text-sm font-medium text-black disabled:opacity-50"
        >
          {submitting ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </div>
  )
}
