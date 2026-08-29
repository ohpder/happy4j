import { reactive } from 'vue'

const toasts = reactive([])
let sequence = 0

export function useToasts() {
  function push(message, tone = 'success') {
    const id = ++sequence
    toasts.push({ id, message, tone })
    setTimeout(() => remove(id), 3200)
  }

  function remove(id) {
    const index = toasts.findIndex((toast) => toast.id === id)
    if (index >= 0) toasts.splice(index, 1)
  }

  return { toasts, push, remove }
}
