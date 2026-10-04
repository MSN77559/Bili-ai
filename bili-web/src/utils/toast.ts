import { reactive } from 'vue'

/**
 * 全局轻提示（06 §6）：3 秒自动消失，顶部居中。函数式调用。
 */
export interface ToastItem {
  id: number
  text: string
}

export const toastState = reactive<{ items: ToastItem[] }>({ items: [] })

let seq = 0

export function toast(text: string) {
  const id = ++seq
  toastState.items.push({ id, text })
  setTimeout(() => {
    const i = toastState.items.findIndex((t) => t.id === id)
    if (i >= 0) toastState.items.splice(i, 1)
  }, 3000)
}
