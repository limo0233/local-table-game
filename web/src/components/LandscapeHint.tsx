import { RotateCw } from 'lucide-react'

export function LandscapeHint() {
  return (
    <div className="landscape-hint" role="note">
      <RotateCw size={14} />
      <span>横屏时桌面信息更清晰</span>
    </div>
  )
}
