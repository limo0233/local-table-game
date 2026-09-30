type SessionCrypto = Pick<Crypto, 'getRandomValues'> & Partial<Pick<Crypto, 'randomUUID'>>

export function createSessionId(source: SessionCrypto = globalThis.crypto): string {
  if (typeof source?.randomUUID === 'function') return source.randomUUID()
  if (typeof source?.getRandomValues !== 'function') throw new Error('当前浏览器不支持安全随机数，请改用最新版 Chrome')

  const bytes = source.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80

  const value = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`
}
