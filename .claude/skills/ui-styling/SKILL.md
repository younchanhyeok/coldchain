---
name: ui-styling
description: coldchain 프론트엔드(frontend/) UI 작업 시 반드시 적용하는 ColdChain 디자인 시스템. 컴포넌트·페이지·차트·지도·테이블 스타일링 전부 해당. 새 화면 생성, 기존 화면 수정, 색상/타이포/여백 결정 시 항상 이 문서를 따른다.
---

# ColdChain UI/UX Design Guidelines

## ⚠️ Milestone Guard (최우선 규칙 — 아래 모든 규칙보다 우선)

**AI prediction 요소(예측 라인, 예상 이탈 시간, 예측 요약 배너, RISK 상태)는 M4에서 예측 API가 생긴 이후에만 렌더링한다.**

- M4 이전: 차트는 실측 라인 + 임계 참조선만. 범례에도 예측 항목을 넣지 않는다.
- 데이터가 없는 지표는 placeholder("M4 예측 이후 제공")로 표시하고, 가짜 값을 절대 넣지 않는다.

---

## Overall Style

Modern enterprise SaaS dashboard inspired by:

- Apple Developer
- Linear
- Vercel
- Stripe Dashboard
- Raycast

The interface should feel premium, minimal, and data-focused.
Avoid Bootstrap/AdminLTE appearance.

## Color System

| Token | Value |
|---|---|
| Background | `#000000` |
| Sidebar | `#050505` |
| Header | `#090909` |
| Card | `#18181B` |
| Card Hover | `#1D1D20` |
| Border | `#262626` |
| Divider | `#202020` |

## Accent Colors

| Token | Value |
|---|---|
| Primary | `#5EA9FF` |
| Warning | `#F4B860` |
| Danger | `#FF6B6B` |
| Success | `#41D17D` |

Only these colors should attract attention. Everything else should remain grayscale.

## Typography

Font: **Pretendard Variable**

| Role | Weight |
|---|---|
| Title | 600 |
| Body | 400 |
| Labels | 400 |
| Numbers | 500 |

Avoid bold UI. Large KPI numbers should feel elegant rather than heavy.

## Card

- Radius: `20px`
- Border: `1px solid #262626`
- Shadow: very subtle — cards should feel slightly floating. Never use strong shadows.
  - `box-shadow: 0 0 0 1px rgba(255,255,255,.03), 0 12px 40px rgba(0,0,0,.35);`
- Padding: `28px`
- Gap: `24px`

**Every major visualization (map, chart, table, timeline) must be wrapped inside a reusable
dashboard card — Header(title + optional actions/status) + Body + optional Footer. Raw content
must never sit directly on the page.**

## Layout

- 8px spacing system
- Outer margin: `40px`
- Card gap: `24px`
- Inside padding: `28px`
- Use generous whitespace.

## KPI Cards

Each KPI card must contain:

- Label
- Large Number
- Small Description
- Lucide Icon (colored circular/rounded background behind icon)

Icons: `Truck`, `TriangleAlert`, `Package`, `TrendingUp`. **Do not use emoji.**

## Charts

- Dark background, thin grid (`#1d1d1d`)
- Line: 2px
- Measured: solid Blue (`#5EA9FF`)
- Prediction: dashed Amber (`#F4B860`) — **M4 이후에만** (Milestone Guard)
- Threshold: thin Red line
- Chart should always explain the AI prediction — once prediction data exists.

## Tables

- Minimal borders
- Hover highlight
- Status uses pills
- Temperature is color coded (normal = grayscale/blue, breach = Danger)

## Maps

- Dark monochrome map. Roads should be subtle.
- Only markers use colors: Blue = Safe, Amber = Predicted Risk (M4~), Red = Breached
- Routes should visualize logistics flow.

## Motion

- Hover: `translateY(-2px)`
- Transition: `250ms ease`
- No flashy animations.

## Never (금지 목록)

- Never generate Bootstrap-like dashboards.
- Never use thick borders.
- Never use saturated colors.
- Never use colorful backgrounds.
- Never use skeuomorphic elements.
- Never use unnecessary gradients.
- Avoid heavy shadows.
- Prioritize whitespace over density.
- Design should resemble Linear, Vercel and Apple internal dashboards.

## ColdChain Design Principles

1. AI should always be visually distinguishable.
2. Measured data and AI prediction must always appear together — **once prediction exists (M4~)**.
3. Every dashboard must answer: What is happening? / What will happen? / What action should the user take?
4. Blue represents actual data.
5. Amber represents AI prediction.
6. Red represents breach.
7. Maps should explain movement.
8. Charts should explain prediction.
9. Tables should explain priority.
10. Every screen must communicate value within 3 seconds.

## Overall Feeling

The product should feel like a premium B2B SaaS platform rather than an admin template.
Every screen should prioritize clarity, whitespace, and visual hierarchy.
The dashboard must communicate AI-powered logistics monitoring.
