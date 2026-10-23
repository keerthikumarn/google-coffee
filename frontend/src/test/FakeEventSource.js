import { vi } from 'vitest'

/**
 * A stand-in for the browser's EventSource that the test drives by hand:
 *   FakeEventSource.last.emit('order', payload)   -> the server pushed an event
 *   FakeEventSource.last.fail({ closed: true })   -> the connection dropped (closed for good)
 */
export class FakeEventSource {
  static CONNECTING = 0
  static OPEN = 1
  static CLOSED = 2
  static instances = []

  static get last() {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1]
  }

  constructor(url) {
    this.url = url
    this.readyState = FakeEventSource.CONNECTING
    this.listeners = {}
    this.onerror = null
    FakeEventSource.instances.push(this)
  }

  addEventListener(type, listener) {
    ;(this.listeners[type] ||= []).push(listener)
  }

  close() {
    this.readyState = FakeEventSource.CLOSED
  }

  get closed() {
    return this.readyState === FakeEventSource.CLOSED
  }

  emit(type, data) {
    this.readyState = FakeEventSource.OPEN
    for (const listener of this.listeners[type] || []) listener({ data: JSON.stringify(data) })
  }

  fail({ closed = false } = {}) {
    this.readyState = closed ? FakeEventSource.CLOSED : FakeEventSource.CONNECTING
    this.onerror?.()
  }
}

/** Replace the global EventSource for the current test (undone by vi.unstubAllGlobals). */
export function installFakeEventSource() {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
}
