import { CircleHelp, Scissors, Shield, Bomb, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { PlayerRing } from '../../components/PlayerRing'
import { RoomBackButton } from '../../components/RoomBackButton'
import { timeBombRoleNames } from '../roleNames'
import type { RoomView, Session, TimeBombView, TimeBombWire } from '../../types'

interface Props {
  room: RoomView
  session: Session | null
  view: TimeBombView
  send: (type: string, payload?: unknown) => void
  onLeave: () => boolean | void
}

export function TimeBombGame({ room, session, view, send, onLeave }: Props) {
  const [targetId, setTargetId] = useState('')
  const [wireIndex, setWireIndex] = useState(0)
  const [showRules, setShowRules] = useState(false)
  const isHost = room.hostId === session?.playerId
  const targetCount = targetId ? view.public.wireCountsByPlayer[targetId] ?? 0 : 0
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '玩家'
  const roleName = `${timeBombRoleNames[view.myRole]}阵营`

  const chooseTarget = (id: string) => {
    setTargetId(id)
    setWireIndex(0)
  }

  return (
    <section className="new-game-screen timebomb-screen screen-pad">
      <header className="game-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>TimeBomb</h1><span>房间 {room.roomId}</span></div></div>
        <div className="game-header-actions"><span className="new-game-phase">第 {view.public.round} 轮</span><button type="button" aria-label="TimeBomb规则" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button></div>
      </header>

      <div className="new-game-summary"><span><Shield size={16} />已拆 {view.public.defuseFound}/{view.public.totalDefuse} 条拆弹线</span><span>本轮剪线 {view.public.cutsThisRound}/{room.players.length}</span></div>
      <PlayerRing className="game-player-ring timebomb-player-ring" ariaLabel="TimeBomb玩家座位" center={<div className="player-ring-status"><strong>{view.public.phase === 'GAME_OVER' ? '游戏结束' : `第 ${view.public.round} 轮`}</strong><small>剩余线路：{Object.values(view.public.wireCountsByPlayer).reduce((sum, count) => sum + count, 0)} 条</small></div>} players={room.players.map(player => ({ id: player.id, nickname: player.nickname, isMe: player.id === session?.playerId, connected: player.connected, current: player.id === view.public.currentCutterId, status: player.id === view.public.currentCutterId ? '持剪' : '等待', badge: `${view.public.wireCountsByPlayer[player.id] ?? 0} 条` }))} />
      <div className="new-game-private"><strong>你的阵营：{roleName}</strong><div className="wire-list">{view.privateWires.map((wire, index) => <span className={`wire-chip ${wire.toLowerCase()}`} key={`${wire}-${index}`}>{wireLabel(wire)}</span>)}</div><small>线路顺序会被重新混洗，你只能确认组成。</small></div>

      {view.public.phase === 'ROLE_REVEAL' ? <div className="new-game-action-card"><h2>确认身份</h2><p>记住自己的阵营和线路组成，确认后等待其他玩家。</p><button className="primary-button" disabled={!view.canConfirmRole} onClick={() => send('timebomb:confirm_role')}>{view.canConfirmRole ? '我已确认' : '已确认，等待其他玩家'}</button></div> : null}

      {view.public.phase === 'ROUND' ? <div className="new-game-action-card"><h2>{view.canCut ? '轮到你剪线' : `等待 ${playerName(view.public.currentCutterId)} 剪线`}</h2>{view.canCut ? <><label className="new-game-field">选择玩家<select value={targetId} onChange={event => chooseTarget(event.target.value)}><option value="">选择一名玩家</option>{view.targetIds.map(id => <option value={id} key={id}>{playerName(id)}（{view.public.wireCountsByPlayer[id] ?? 0} 条）</option>)}</select></label>{targetId ? <label className="new-game-field">选择未知线路<select value={wireIndex} onChange={event => setWireIndex(Number(event.target.value))}>{Array.from({ length: targetCount }, (_, index) => <option value={index} key={index}>第 {index + 1} 条</option>)}</select></label> : null}<button className="primary-button" disabled={!targetId || !targetCount} onClick={() => send('timebomb:cut', { targetId, wireIndex })}><Scissors size={18} />确认剪线</button></> : <p className="waiting-action">其他玩家正在选择线路…</p>}</div> : null}

      {view.public.lastCut ? <div className={`new-game-result ${view.public.lastCut.wire.toLowerCase()}`}><strong>{playerName(view.public.lastCut.cutterId)} → {playerName(view.public.lastCut.targetId)}</strong><span>{wireLabel(view.public.lastCut.wire)} · 对方剩余 {view.public.lastCut.remainingTargetWires} 条</span></div> : null}
      {view.public.phase === 'GAME_OVER' ? <div className="new-game-result final"><Bomb size={22} /><h2>{view.public.winner === 'SHERLOCK' ? '侦探阵营获胜' : '破坏者阵营获胜'}</h2><p>{view.public.endReason}</p>{isHost ? <button className="primary-button" onClick={() => send('game:restart')}><RotateCcw size={17} />返回大厅</button> : null}</div> : null}
      {showRules ? <GameRulesModal game="TIME_BOMB" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function wireLabel(wire: TimeBombWire) {
  return ({ SAFE: '安全线', DEFUSE: '拆弹线', BOMB: '炸弹' } as Record<TimeBombWire, string>)[wire]
}
