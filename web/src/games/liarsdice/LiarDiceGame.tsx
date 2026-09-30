import { CircleHelp, Dice5 } from 'lucide-react'
import { useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { PlayerRing } from '../../components/PlayerRing'
import { RoomBackButton } from '../../components/RoomBackButton'
import type { LiarDiceView, RoomView, Session } from '../../types'

interface Props {
  room: RoomView
  session: Session | null
  view: LiarDiceView
  send: (type: string, payload?: unknown) => void
  onLeave: () => boolean | void
}

export function LiarDiceGame({ room, session, view, send, onLeave }: Props) {
  const isHost = room.hostId === session?.playerId
  const [showRules, setShowRules] = useState(false)

  return (
    <section className="new-game-screen liarsdice-screen screen-pad">
      <header className="game-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>骰子模拟器</h1><span>房间 {room.roomId}</span></div></div>
        <div className="game-header-actions"><span className="new-game-phase">多人骰子模拟器</span><button type="button" aria-label="骰子模拟器说明" title="骰子模拟器说明" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button></div>
      </header>

      <div className="new-game-summary"><span><Dice5 size={16} />每位玩家 {view.public.diceCount[session?.playerId ?? ''] ?? view.myDice.length} 颗骰子</span><span>{view.public.allSettled ? '全部已结算' : `已结算 ${view.public.settledPlayerIds.length}/${room.players.length}`}</span></div>
      {view.public.allSettled ? <div className="liardice-point-stats"><strong>全场点数统计</strong><div>{[1, 2, 3, 4, 5, 6].map(point => <span key={point}><b>{point}</b><em>{view.public.pointCounts[String(point)] ?? 0}</em></span>)}</div></div> : null}
      <PlayerRing className="game-player-ring liardice-player-ring" ariaLabel="多人骰子玩家座位" center={<div className="player-ring-status"><strong>第 {view.public.round} 局</strong><small>{view.public.allSettled ? '结果已公开' : `等待结算 ${view.public.settledPlayerIds.length}/${room.players.length}`}</small></div>} players={room.players.map(player => ({ id: player.id, nickname: player.nickname, isMe: player.id === session?.playerId, connected: player.connected, current: player.id === session?.playerId && !view.settled, status: view.public.allSettled ? (view.public.resultsByPlayer[player.id] ?? []).join(' · ') : view.public.settledPlayerIds.includes(player.id) ? '已结算' : '待操作', badge: view.public.allSettled ? undefined : `${view.public.diceCount[player.id] ?? 0} 颗` }))} />
      <div className="dice-hand"><strong>我的骰子</strong><div className="dice-select-grid">{view.myDice.map((die, index) => <span className="die" aria-label={`第 ${index + 1} 颗骰子 ${die} 点`} key={`${index}-${die}`}>{die}</span>)}</div><small>{view.settled ? '你已结算，等待其他玩家。' : '点击“重掷”会将你的全部骰子一起重新生成。'}</small></div>

      <div className="new-game-action-card"><h2>{view.public.allSettled ? '本轮已完成结算' : view.settled ? '已完成结算' : '确认本轮骰子'}</h2>{view.public.allSettled ? <><p>{view.canNextRound ? '确认后等待其他玩家，所有人确认后自动开始下一局。' : '你已确认下一局，等待其他玩家。'}</p><button className="primary-button" disabled={!view.canNextRound} onClick={() => send('liarsdice:next_round')}>{view.canNextRound ? '下一局' : '已确认下一局'}</button></> : <div className="new-game-button-grid"><button className="secondary-button" disabled={!view.canReroll} onClick={() => send('liarsdice:reroll')}>重掷</button><button className="primary-button" disabled={!view.canSettle} onClick={() => send('liarsdice:settle')}>结算</button></div>}</div>

      <p className="new-game-note">软件只负责生成、重掷、结算和展示骰子；玩家之间的讨论、判断与胜负由线下自行完成。</p>
      {view.public.allSettled ? <div className="liardice-results" aria-label="玩家骰子结果"><strong>玩家骰子结果</strong><div>{room.players.map(player => <span key={player.id}><b>{player.id === session?.playerId ? '你' : player.nickname}</b><em>{(view.public.resultsByPlayer[player.id] ?? []).join(' · ')}</em></span>)}</div></div> : null}
      {showRules ? <GameRulesModal game="LIARS_DICE" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}
