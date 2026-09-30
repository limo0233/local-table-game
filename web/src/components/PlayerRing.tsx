import type { CSSProperties, ReactNode } from 'react'

export interface RingPlayer {
  id: string
  nickname: string
  isMe?: boolean
  connected?: boolean
  current?: boolean
  selected?: boolean
  disabled?: boolean
  status?: ReactNode
  badge?: ReactNode
  onClick?: () => void
}

interface Props {
  players: RingPlayer[]
  center: ReactNode
  className?: string
  ariaLabel?: string
}

/** Responsive table seating shared by tabletop game screens. */
export function PlayerRing({ players, center, className = '', ariaLabel = '环形玩家座位' }: Props) {
  const count = Math.max(players.length, 1)
  return (
    <div className={`player-ring ${className}`} aria-label={ariaLabel} style={{ '--ring-count': count } as CSSProperties}>
      <div className="player-ring-center">{center}</div>
      {players.map((player, index) => {
        const style = { '--ring-angle': `${(index / count) * 360 - 90}deg` } as CSSProperties
        const content = <>
          <span className="player-ring-avatar">{player.nickname.slice(0, 1)}</span>
          <span className="player-ring-copy"><strong>{player.isMe ? '你' : player.nickname}</strong>{player.status ? <small>{player.status}</small> : null}</span>
          {player.badge ? <b className="player-ring-badge">{player.badge}</b> : null}
        </>
        const classes = ['player-ring-seat', player.current ? 'current' : '', player.selected ? 'selected' : '', player.connected === false ? 'offline' : ''].filter(Boolean).join(' ')
        return player.onClick ? <button type="button" className={classes} style={style} disabled={player.disabled} onClick={player.onClick} key={player.id}>{content}</button> : <div className={classes} style={style} key={player.id}>{content}</div>
      })}
    </div>
  )
}
