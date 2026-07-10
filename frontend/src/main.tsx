import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { RequireAuth } from './components/auth/RequireAuth.tsx'
import { AdminPage } from './pages/AdminPage.tsx'
import { ConsigneeTrackPage } from './pages/ConsigneeTrackPage.tsx'
import { LoginPage } from './pages/LoginPage.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {/* 매직링크(수령기관)·어드민 키 — JWT와 무관한 별도 인가 축이라 RequireAuth 밖에 둔다. */}
        <Route path="/t/:token" element={<ConsigneeTrackPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <App />
            </RequireAuth>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
