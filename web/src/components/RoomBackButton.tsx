import { ArrowLeft } from 'lucide-react'
import { useState } from 'react'

interface Props {
  isHost: boolean
  onLeave: () => boolean | void
}

export function RoomBackButton({ isHost, onLeave }: Props) {
  const [leaving, setLeaving] = useState(false)
  const leave = () => {
    if (leaving) return
    const message = isHost
      ? '返回主界面将解散当前房间，所有玩家都会退出。确定继续吗？'
      : '确定退出当前房间并返回主界面吗？'
    if (!window.confirm(message)) return
    setLeaving(true)
    const accepted = onLeave()
    if (accepted === false) setLeaving(false)
  }

  return <button type="button" className="room-back-button" disabled={leaving} aria-label={isHost ? '解散房间并返回主界面' : '退出房间并返回主界面'} onClick={leave}><ArrowLeft size={18} />{leaving ? '处理中…' : '返回'}</button>
}
