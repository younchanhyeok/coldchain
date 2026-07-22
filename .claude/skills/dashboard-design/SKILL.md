---
name: dashboard-design
description: 대시보드 정보 구조·레이아웃·KPI/AI 요약/지도/차트/테이블/빈 상태 설계 원칙. 새 대시보드 화면을 만들거나 기존 대시보드의 구성·배치를 바꿀 때 적용한다.
---

# Dashboard Design Principles

## Purpose

Dashboards are not collections of widgets.

Every dashboard must immediately answer three questions:

1. What is happening?
2. Why is it happening?
3. What action should the user take?

Users should understand the current situation within 3 seconds.

---

## Information Hierarchy

Always follow this order.

1. KPI Summary
2. AI Insight
3. Live Monitoring
4. Detailed Analysis
5. Historical Data

Never place detailed tables before the summary.

---

## Dashboard Layout

Recommended structure.

────────────────────────

Header

↓

KPI Cards

↓

AI Summary Banner

↓

Map / Monitoring Panel

↓

Risk Table

↓

Trend Chart

↓

Detailed Analytics

────────────────────────

Do not randomly arrange cards.

The user's eyes should naturally move from top to bottom.

---

## KPI Cards

Every KPI card should include

- Label
- Large Number
- Supporting Description
- Lucide Icon
- Colored Circular Icon Background

Example

배송 중
128

조회된 트래커 수

🚚

Avoid empty KPI cards.

If no data exists, explain why.

---

## AI Summary

Every AI-powered dashboard must include a summary.

Example

AI Prediction Summary

- 4 cargoes are expected to exceed the temperature threshold within 2 hours.
- Estimated prevented loss: ₩2.4M
- Average lead time: 11.2 minutes.

The AI summary should appear above charts.

---

## Maps

Maps should communicate movement.

Do not display empty maps.

Always include

- Route
- Origin
- Destination
- Current Position
- Risk Marker

Marker Colors

Blue = Normal

Amber = AI Predicted Risk

Red = Temperature Breach

Maps should answer

"Where is the problem?"

---

## Charts

Charts explain trends.

Never display only measured values.

Always compare

Measured Temperature

↓

AI Prediction

↓

Threshold

Prediction should use dashed lines.

Threshold should use a thin red line.

The chart should explain

"When will the issue happen?"

---

## Tables

Tables explain details.

Prioritize

Highest Risk

↓

Soonest Prediction

↓

Remaining Time

↓

Temperature

↓

Location

↓

Cargo ID

Avoid alphabetical ordering.

---

## Empty States

Never leave empty cards.

Use meaningful explanations.

Example

"No predicted risks during the selected period."

instead of

"No Data"

---

## Visual Priority

Users should notice

1. Risks
2. AI Prediction
3. Current Status
4. History

Never give equal visual weight to every component.

---

## Dashboard Philosophy

A dashboard is not built to impress.

It is built to support fast decisions.

Every component should help the user decide what to do next.
