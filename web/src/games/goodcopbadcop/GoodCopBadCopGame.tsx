import { CircleHelp, Crosshair, Eye, RotateCcw, Search, Shield, Target } from 'lucide-react'
import { useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { PlayerRing } from '../../components/PlayerRing'
import { RoomBackButton } from '../../components/RoomBackButton'
import { goodCopTeamNames, integrityTypeNames } from '../roleNames'
import type { GoodCopTeam, GoodCopView, IntegrityType, RoomView, Session } from '../../types'

interface Props {
  room: RoomView
  session: Session | null
  view: GoodCopView
  send: (type: string, payload?: unknown) => void
  onLeave: () => boolean | void
}

export function GoodCopBadCopGame({ room, session, view, send, onLeave }: Props) {
  const [targetId, setTargetId] = useState('')
  const [cardIndex, setCardIndex] = useState(0)
  const [showRules, setShowRules] = useState(false)
  const isHost = room.hostId === session?.playerId
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '玩家'
  const roleName = (role: IntegrityType) => integrityTypeNames[role]
  const teamName = (team: GoodCopTeam) => goodCopTeamNames[team]
  const activeTargets = view.targetIds
  const currentName = playerName(view.public.currentPlayerId)

  return (
    <section className="new-game-screen goodcop-screen screen-pad">
      <header className="game-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>Good Cop Bad Cop</h1><span>房间 {room.roomId}</span></div></div>
        <div className="game-header-actions"><span className="new-game-phase">{view.public.phase === 'GAME_OVER' ? '游戏结束' : view.public.phase === 'ACTION_RESOLVE' ? '处理射击' : '行动阶段'}</span><button type="button" aria-label="Good Cop Bad Cop规则" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button></div>
      </header>

      <PlayerRing className="game-player-ring goodcop-player-ring" ariaLabel="Good Cop Bad Cop玩家座位" center={<div className="player-ring-status"><strong>{view.public.phase === 'GAME_OVER' ? '游戏结束' : view.public.phase === 'ACTION_RESOLVE' ? '处理射击' : '行动阶段'}</strong><small>当前：{currentName}</small></div>} players={room.players.map(player => ({ id: player.id, nickname: player.nickname, isMe: player.id === session?.playerId, connected: player.connected, current: player.id === view.public.currentPlayerId, status: view.public.eliminated.includes(player.id) ? '淘汰' : view.public.wounded.includes(player.id) ? '受伤' : player.id === view.public.currentPlayerId ? '当前行动' : '等待', badge: `${view.public.hiddenIntegrityCounts[player.id] ?? 0} 张` }))} />
      <div className="new-game-private"><strong><Eye size={16} />你的阵营：{teamName(view.myTeam)}</strong><div className="integrity-cards">{view.myIntegrity.map(card => <span className={card.revealed ? 'revealed' : 'hidden'} key={card.id}>{card.revealed ? roleName(card.type) : '隐藏身份牌'}</span>)}</div>{view.myEquipment.length ? <small><Shield size={13} />装备：{view.myEquipment.map(item => item === 'SHIELD' ? '防弹盾' : item).join('、')}</small> : null}{view.investigationResult ? <p className="investigation-note">最近调查结果：{roleName(view.investigationResult.type)}（目标身份牌仍保持隐藏）</p> : null}</div>

      {view.public.phase === 'TURN' && view.public.currentPlayerId === session?.playerId ? <div className="new-game-action-card"><h2>轮到你行动</h2><div className="new-game-inline"><select className="new-game-select" value={targetId} onChange={event => setTargetId(event.target.value)}><option value="">选择玩家</option>{activeTargets.map(id => <option value={id} key={id}>{playerName(id)}</option>)}</select><select className="new-game-select compact" value={cardIndex} onChange={event => setCardIndex(Number(event.target.value))}><option value={0}>第 1 张隐藏牌</option><option value={1}>第 2 张隐藏牌</option><option value={2}>第 3 张隐藏牌</option></select></div><div className="new-game-button-grid">{view.canInvestigate ? <button className="secondary-button" disabled={!targetId} onClick={() => send('goodcop:investigate', { targetId, cardIndex })}><Search size={16} />调查</button> : null}{view.canEquip ? <button className="secondary-button" onClick={() => send('goodcop:equip')}><Shield size={16} />装备</button> : null}{view.canArm ? <button className="primary-button" onClick={() => send('goodcop:arm')}><Crosshair size={16} />武装</button> : null}</div></div> : null}
      {view.public.phase === 'ACTION_RESOLVE' && view.canAim ? <div className="new-game-action-card"><h2><Target size={18} />选择瞄准目标</h2><select className="new-game-select" value={targetId} onChange={event => setTargetId(event.target.value)}><option value="">选择目标</option>{activeTargets.map(id => <option value={id} key={id}>{playerName(id)}</option>)}</select><button className="primary-button" disabled={!targetId} onClick={() => send('goodcop:aim', { targetId })}>确认瞄准</button></div> : null}
      {view.public.phase === 'ACTION_RESOLVE' && view.canShoot ? <div className="new-game-action-card"><h2>目标：{playerName(view.public.aimTarget ?? '')}</h2><p>确认后将结算防弹、受伤或淘汰；未被防弹抵消时公开目标的全部身份。</p><button className="danger-button full-width-button" onClick={() => send('goodcop:shoot')}><Target size={17} />开枪</button></div> : null}
      {view.public.phase !== 'GAME_OVER' && view.public.currentPlayerId !== session?.playerId ? <p className="waiting-action">等待 {currentName} 行动…</p> : null}

      {view.public.history.length ? <div className="new-game-history"><h3>行动记录</h3>{view.public.history.slice(-6).map((item, index) => <p key={`${item.actorId}-${item.action}-${index}`}>{playerName(item.actorId)} · {actionLabel(item.action)}{item.targetId ? ` → ${playerName(item.targetId)}` : ''}{item.detail ? `：${item.detail}` : ''}</p>)}</div> : null}

      {view.public.phase === 'GAME_OVER' ? <div className="new-game-result final"><h2>{view.public.winnerTeam === 'HONEST' ? '好警阵营获胜' : '黑警阵营获胜'}</h2><p>你的阵营：{teamName(view.myTeam)}</p>{isHost ? <button className="primary-button" onClick={() => send('game:restart')}><RotateCcw size={17} />返回大厅</button> : null}</div> : null}
      {showRules ? <GameRulesModal game="GOOD_COP_BAD_COP" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function actionLabel(action: string) {
  return ({ INVESTIGATE: '调查', EQUIP: '装备', ARM: '武装', AIM: '瞄准', SHOOT: '射击' } as Record<string, string>)[action] ?? action
}
