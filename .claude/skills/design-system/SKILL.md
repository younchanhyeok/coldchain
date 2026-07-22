---
name: design-system
description: ColdChain 재사용 컴포넌트 구현 규칙(그리드/radius/border/shadow/버튼/카드/KPI/테이블/입력/드롭다운/차트/사이드바/내비/알림/빈상태/모션/접근성). 컴포넌트를 새로 만들거나 스타일을 정할 때 가장 먼저 참고한다 — 이미 있는 컴포넌트는 재사용하고 새로 디자인하지 않는다.
---

# ColdChain Design System

This document defines reusable UI components.

Every interface should reuse these components.

Never invent new component styles.

Maintain consistency across all screens.

---

# Grid

Use an 8px spacing system.

Page Padding

40px

Card Gap

24px

Section Gap

32px

Component Gap

16px

---

# Radius

Cards

20px

Buttons

12px

Inputs

12px

Badges

999px

Modals

24px

---

# Borders

Use

1px solid #262626

Never use thick borders.

---

# Shadows

Cards

0 0 0 1px rgba(255,255,255,.03),
0 16px 40px rgba(0,0,0,.28)

Hover

0 20px 50px rgba(0,0,0,.35)

Avoid heavy shadows.

---

# Buttons

Primary

Blue background

White text

Hover slightly brighter

Secondary

Dark background

Gray border

Ghost

Transparent

Borderless

Danger

Dark red

---

# Cards

Background

#18181B

Radius

20px

Padding

28px

Border

#262626

Cards should float slightly above the background.

---

# KPI Card

Always contains

Label

↓

Value

↓

Description

↓

Icon

Never omit descriptions.

Icons should use Lucide.

Icon backgrounds should be colored circles.

---

# Tables

Row Height

56px

Header

14px

Body

15px

Hover

Background #1E1E1E

Selected

Left border Blue

Status

Always use rounded pills.

---

# Inputs

Height

44px

Radius

12px

Dark background

Subtle border

Blue focus ring

---

# Dropdown

Height

44px

Dark background

Rounded

Minimal shadow

---

# Charts

Line Width

2px

Grid

#1D1D1D

Measured

Solid Blue

Prediction

Dashed Amber

Threshold

Thin Red

Legend

Top Right

---

# Sidebar

Width

260px

Background

#050505

Active Menu

Blue background

Hover

Gray background

Icons

Lucide

Stroke 2

---

# Navigation

Height

72px

Minimal

No large logos

Actions aligned right

---

# Notifications

Success

Green

Prediction

Amber

Error

Red

Neutral

Gray

Always use icons.

---

# Empty States

Illustration

Optional

Headline

Description

CTA

Never display only "No Data".

Explain why data is unavailable.

---

# Motion

Transition

250ms ease

Hover

translateY(-2px)

Scale

1.01

No flashy animation.

---

# Accessibility

Minimum contrast ratio

4.5

Interactive targets

44px

Never rely only on color.

Always pair colors with icons or labels.

---

# Consistency

If a component already exists,

reuse it.

Do not redesign the same component twice.

Consistency is more important than creativity.
