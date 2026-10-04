// 头像回退工具（FR-PF04）：默认头像 data URI，避免依赖后端在线才能显示
// 纯色品牌粉 #FB7299 64x64 SVG（ADR-16 视觉基线）
export const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><rect width="64" height="64" fill="%23FB7299"/></svg>'

// 空间背景图回退（FR-PF07）：默认图 data URI（蓝紫渐变 SVG，与 banners/default.png 同视觉）
// 文件丢失/后端停机时兜底，正常路径仍用后端返回的 bannerUrl
export const defaultBanner =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="200"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="%232b4c7e"/><stop offset="0.5" stop-color="%233a6ea5"/><stop offset="1" stop-color="%237fb0d8"/></linearGradient></defs><rect width="1200" height="200" fill="url(%23g)"/></svg>'

/** 拼完整头像 URL：相对路径（/api/files/...）配同源 baseURL */
export function avatarUrl(path: string | null | undefined): string {
  if (!path) return defaultAvatar
  if (path.startsWith('data:') || path.startsWith('http')) return path
  return path // vite proxy 同源直连
}
