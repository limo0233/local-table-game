import { describe, expect, it, vi } from 'vitest'
import { createSessionId } from './sessionId'

describe('createSessionId', () => {
  it('uses randomUUID when the browser exposes it', () => {
    const randomUUID = vi.fn(() => 'native-uuid')
    const source = { randomUUID, getRandomValues: vi.fn() } as unknown as Crypto

    expect(createSessionId(source)).toBe('native-uuid')
    expect(randomUUID).toHaveBeenCalledOnce()
  })

  it('creates a UUID with getRandomValues on an insecure HTTP origin', () => {
    const source = {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.set(Array.from({ length: 16 }, (_, index) => index))
        return bytes
      },
    } as unknown as Crypto

    const id = createSessionId(source)

    expect(id).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })

  it('reports unsupported browsers instead of silently ignoring the tap', () => {
    expect(() => createSessionId({} as Crypto)).toThrow('当前浏览器不支持安全随机数')
  })
})
