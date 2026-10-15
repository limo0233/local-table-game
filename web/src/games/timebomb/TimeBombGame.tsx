import { CircleHelp, Scissors, Shield, Bomb, RotateCcw } from 'lucide-react'
import type { CSSProperties } from 'react'
import { useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { LandscapeHint } from '../../components/LandscapeHint'
import { PlayerNameplate } from '../../components/PlayerNameplate'
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
      <LandscapeHint />

      <div className="timebomb-workbench">
        <div className="timebomb-wire-grid" aria-label="玩家线路盒">
          {room.players.map(player => {
            const selectable = view.canCut && view.targetIds.includes(player.id)
            const index = room.players.findIndex(item => item.id === player.id)
            return <PlayerNameplate
              key={player.id}
              index={index}
              name={player.id === session?.playerId ? '你' : player.nickname}
              status={`${view.public.wireCountsByPlayer[player.id] ?? 0} 条未知线路`}
              detail={player.id === view.public.currentCutterId ? '本轮持剪者' : player.id === targetId ? '已选择目标' : '线路盒待检'}
              badge={player.id === view.public.currentCutterId ? <Scissors size={12} /> : undefined}
              className="timebomb-wire-box"
              current={player.id === view.public.currentCutterId}
              selected={player.id === targetId}
              offline={!player.connected}
              disabled={!selectable}
              onClick={selectable ? () => chooseTarget(player.id) : undefined}
            />
          })}
        </div>
        <div className="timebomb-console">
          <span>第 {view.public.round} 轮</span>
          <strong>{view.public.defuseFound}<small> / {view.public.totalDefuse}</small></strong>
          <em>已找到拆弹线</em>
          <div><i style={{ '--progress': `${(view.public.cutsThisRound / room.players.length) * 100}%` } as CSSProperties} /></div>
          <small>本轮剪线 {view.public.cutsThisRound}/{room.players.length} · 剩余 {Object.values(view.public.wireCountsByPlayer).reduce((sum, count) => sum + count, 0)} 条</small>
        </div>
      </div>
      <div className="new-game-private"><strong>你的阵营：{roleName}</strong><div className="wire-list">{view.privateWires.map((wire, index) => <span className={`wire-chip ${wire.toLowerCase()}`} key={`${wire}-${index}`}>{wireLabel(wire)}</span>)}</div><small>线路顺序会被重新混洗，你只能确认组成。</small></div>

      {view.public.phase === 'ROLE_REVEAL' ? <div className="new-game-action-card"><h2>确认身份</h2><p>记住自己的阵营和线路组成，确认后等待其他玩家。</p><button className="primary-button" disabled={!view.canConfirmRole} onClick={() => send('timebomb:confirm_role')}>{view.canConfirmRole ? '我已确认' : '已确认，等待其他玩家'}</button></div> : null}

      {view.public.phase === 'ROUND' ? <div className="new-game-action-card timebomb-cut-panel"><h2>{view.canCut ? '轮到你剪线' : `等待 ${playerName(view.public.currentCutterId)} 剪线`}</h2>{view.canCut ? <><p>{targetId ? `已选择 ${playerName(targetId)}，请选择一条未知线路。` : '直接点击上方玩家线路盒选择目标。'}</p>{targetId ? <div className="unknown-wire-row" aria-label="选择未知线路">{Array.from({ length: targetCount }, (_, index) => <button type="button" className={wireIndex === index ? 'selected' : ''} onClick={() => setWireIndex(index)} key={index}><span />第 {index + 1} 条</button>)}</div> : null}<button className="primary-button" disabled={!targetId || !targetCount} onClick={() => send('timebomb:cut', { targetId, wireIndex })}><Scissors size={18} />确认剪断第 {wireIndex + 1} 条</button></> : <p className="waiting-action">其他玩家正在选择线路…</p>}</div> : null}

      {view.public.lastCut ? <div className={`new-game-result ${view.public.lastCut.wire.toLowerCase()}`}><strong>{playerName(view.public.lastCut.cutterId)} → {playerName(view.public.lastCut.targetId)}</strong><span>{wireLabel(view.public.lastCut.wire)} · 对方剩余 {view.public.lastCut.remainingTargetWires} 条</span></div> : null}
      {view.public.history.length ? <details className="game-history-drawer"><summary>剪线记录 · {view.public.history.length}</summary><div>{view.public.history.slice().reverse().map((record, index) => <p key={`${record.round}-${record.cutterId}-${index}`}><span>第 {record.round} 轮</span>{playerName(record.cutterId)} → {playerName(record.targetId)}：<strong>{wireLabel(record.wire)}</strong></p>)}</div></details> : null}
      {view.public.phase === 'GAME_OVER' ? <div className="new-game-result final"><Bomb size={22} /><h2>{view.public.winner === 'SHERLOCK' ? '侦探阵营获胜' : '破坏者阵营获胜'}</h2><p>{view.public.endReason}</p>{isHost ? <button className="primary-button" onClick={() => send('game:restart')}><RotateCcw size={17} />返回大厅</button> : null}</div> : null}
      {showRules ? <GameRulesModal game="TIME_BOMB" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function wireLabel(wire: TimeBombWire) {
  return ({ SAFE: '安全线', DEFUSE: '拆弹线', BOMB: '炸弹' } as Record<TimeBombWire, string>)[wire]
}
