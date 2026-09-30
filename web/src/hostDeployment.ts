export interface HostNetworkOption {
  address: string
  interfaceName: string
  label: string
  recommended: boolean
}

export interface HostDeploymentStatus {
  port: number
  selectedAddress: string
  deployed: boolean
  networkOptions: HostNetworkOption[]
}

export interface DeploymentResponse {
  ok: boolean
  message?: string
  port: number
  selectedAddress: string
  deployed: boolean
  restarting: boolean
}

export function isHostConsole(hostname: string) {
  return hostname === '127.0.0.1' || hostname === 'localhost' || hostname === '::1'
}

export function isValidPort(value: string | number) {
  const port = typeof value === 'number' ? value : Number(value)
  return Number.isInteger(port) && port >= 1024 && port <= 65535
}

export function selectLanAddress(status: Pick<HostDeploymentStatus, 'selectedAddress' | 'networkOptions'>) {
  if (status.networkOptions.some(option => option.address === status.selectedAddress)) return status.selectedAddress
  return status.networkOptions[0]?.address ?? null
}
