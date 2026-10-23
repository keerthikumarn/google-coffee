import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Testing Library only advances fake timers by itself when it finds a Jest global; without this shim
// its async helpers (userEvent, waitFor) hang under vi.useFakeTimers().
globalThis.jest = { advanceTimersByTime: (ms) => vi.advanceTimersByTime(ms) }

afterEach(() => cleanup())
