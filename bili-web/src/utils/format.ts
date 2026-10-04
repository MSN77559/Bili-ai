/**
 * 展示格式化工具（Phase 2，02 FR-HM05 万级格式化规则 SSOT）。
 * 万级规则：n<10000 原样数字；n≥10000 截断保留一位小数「x.x万」，
 * 小数尾零保留（10000→「1.0万」不写「1万」；99999→「9.9万」不四舍五入）。
 */
import { GRADIENTS } from '@/mock/home'

/** 万级格式化（播放数/点赞数/粉丝数，播放页/卡片角标复用） */
export function formatCount(n: number): string {
  if (n < 10000) return String(n)
  const v = Math.floor(n / 1000) / 10 // 截断不四舍五入：99999 → 9.9
  return `${v.toFixed(1)}万`
}

/** 秒 → m:ss（75→"1:15"）；≥1h → h:mm:ss */
export function formatDuration(sec: number): string {
  const s = Math.max(0, Math.round(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const pad = (x: number) => String(x).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${m}:${pad(s % 60)}`
}

/** createdAt "yyyy-MM-dd HH:mm:ss" → "yyyy-MM-dd"（播放页标题行；字符串切片避免时区解析） */
export const formatDate = (s: string): string => (s ? s.slice(0, 10) : '')

/** createdAt "yyyy-MM-dd HH:mm:ss" → "MM-dd"（卡片 meta，mock 时代同款短日期） */
export const formatDateShort = (s: string): string => (s ? s.slice(5, 10) : '')

/** coverUrl=null 封面渐变兜底（ADR-17）：按 videoId 在 GRADIENTS 预设中取模循环 */
export function gradientFor(videoId: number): string {
  const len = GRADIENTS.length
  return GRADIENTS[((videoId % len) + len) % len]
}
