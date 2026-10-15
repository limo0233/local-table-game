import type { CSSProperties, ReactNode } from 'react'

interface Props {
  index: number
  name: string
  status: ReactNode
  detail?: ReactNode
  badge?: ReactNode
  className?: string
  style?: CSSProperties
  current?: boolean
  selected?: boolean
  offline?: boolean
  disabled?: boolean
  onClick?: () => void
}

/** Text-only player identity used by the different game tables. */
export function PlayerNameplate({ index, name, status, detail, badge, className = '', style, current, selected, offline, disabled, onClick }: Props) {
  const classes = ['player-nameplate', className, current ? 'is-current' : '', selected ? 'is-selected' : '', offline ? 'is-offline' : ''].filter(Boolean).join(' ')
  const displayName = name.trim().slice(0, 4) || '玩家'
  const content = <>
    <span className="player-nameplate-seat">{String(index + 1).padStart(2, '0')}</span>
    <span className="player-nameplate-copy"><strong title={name}>{displayName}</strong><small>{status}</small>{detail ? <em>{detail}</em> : null}</span>
    {badge ? <b className="player-nameplate-badge">{badge}</b> : null}
  </>
  return onClick
    ? <button type="button" className={classes} style={style} disabled={disabled} onClick={onClick}>{content}</button>
    : <div className={classes} style={style}>{content}</div>
}
