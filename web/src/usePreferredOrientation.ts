import { useEffect } from 'react'

type OrientationMode = 'portrait' | 'landscape'

declare global {
  interface Window {
    HostUi?: { setOrientation: (mode: OrientationMode | 'unspecified') => void }
  }
}

export function usePreferredOrientation(mode: OrientationMode) {
  useEffect(() => {
    document.documentElement.dataset.preferredOrientation = mode
    window.HostUi?.setOrientation(mode)

    const orientation = screen.orientation as (ScreenOrientation & { lock?: (orientation: string) => Promise<void> }) | undefined
    orientation?.lock?.(mode).catch(() => undefined)

    return () => {
      orientation?.unlock?.()
      window.HostUi?.setOrientation('unspecified')
    }
  }, [mode])
}
