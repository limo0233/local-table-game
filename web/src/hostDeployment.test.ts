import { describe, expect, it } from 'vitest'
import { isHostConsole, isValidPort, selectLanAddress } from './hostDeployment'

describe('host deployment validation', () => {
  it('accepts only unprivileged valid TCP ports', () => {
    expect(isValidPort('8787')).toBe(true)
    expect(isValidPort(65535)).toBe(true)
    expect(isValidPort('1023')).toBe(false)
    expect(isValidPort('65536')).toBe(false)
    expect(isValidPort('8.5')).toBe(false)
  })

  it('exposes deployment controls only on the host loopback page', () => {
    expect(isHostConsole('127.0.0.1')).toBe(true)
    expect(isHostConsole('localhost')).toBe(true)
    expect(isHostConsole('192.168.1.20')).toBe(false)
  })

  it('supports local-only mode when no LAN address is available', () => {
    expect(selectLanAddress({ selectedAddress: '', networkOptions: [] })).toBeNull()
    expect(selectLanAddress({
      selectedAddress: '192.168.1.8',
      networkOptions: [{ address: '192.168.1.8', interfaceName: 'wlan0', label: 'Wi-Fi', recommended: true }],
    })).toBe('192.168.1.8')
  })
})
