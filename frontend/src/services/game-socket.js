import { authToken } from './api.js'

export class GameSocket {
  constructor(onEnvelope, onConnection) {
    this.onEnvelope = onEnvelope
    this.onConnection = onConnection
    this.socket = null
    this.pending = new Map()
    this.sequence = 0
    this.retry = 0
    this.closed = false
    this.timer = null
  }

  connect() {
    if (this.closed || !authToken.get()) return
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    this.socket = new WebSocket(
      `${protocol}//${location.host}/ws?token=${encodeURIComponent(authToken.get())}`,
    )
    this.socket.addEventListener('open', () => {
      this.retry = 0
      this.onConnection(true)
    })
    this.socket.addEventListener('message', (event) => this.receive(JSON.parse(event.data)))
    this.socket.addEventListener('close', () => {
      this.onConnection(false)
      this.failPending('连接已断开，正在恢复')
      if (!this.closed) this.scheduleReconnect()
    })
    this.socket.addEventListener('error', () => this.socket?.close())
  }

  command(command, payload = {}) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('连接正在恢复，请稍候'))
    }
    const requestId = `web-${Date.now()}-${++this.sequence}`
    this.socket.send(JSON.stringify({ requestId, command, payload }))
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(requestId)
        reject(new Error(`${command} 请求超时`))
      }, 30000)
      this.pending.set(requestId, { resolve, reject, timeout })
    })
  }

  receive(envelope) {
    const pending = envelope.requestId ? this.pending.get(envelope.requestId) : null
    if (pending) {
      clearTimeout(pending.timeout)
      this.pending.delete(envelope.requestId)
      if (envelope.type === 'ERROR') pending.reject(new Error(envelope.payload?.message || '操作失败'))
      else pending.resolve(envelope.payload?.data)
    }
    this.onEnvelope(envelope)
  }

  scheduleReconnect() {
    clearTimeout(this.timer)
    const delay = Math.min(10000, 600 * 2 ** this.retry++)
    this.timer = setTimeout(() => this.connect(), delay)
  }

  failPending(message) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout)
      pending.reject(new Error(message))
    }
    this.pending.clear()
  }

  close() {
    this.closed = true
    clearTimeout(this.timer)
    this.failPending('连接已关闭')
    this.socket?.close()
  }
}
