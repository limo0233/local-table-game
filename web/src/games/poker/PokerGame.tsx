import { CircleHelp, Coins, Radio, RotateCcw } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { LandscapeHint } from '../../components/LandscapeHint'
import { RoomBackButton } from '../../components/RoomBackButton'
import type { PokerView, RoomView, Session } from '../../types'
import { PlayingCard } from './PlayingCard'

interface Props { room: RoomView; session: Session | null; view: PokerView; send: (type: string, payload?: unknown) => void; onLeave: () => boolean | void }

export function PokerGame({ room, session, view, send, onLeave }: Props) {
  const mySeat = view.public.seats.find(seat => seat.id === session?.playerId)
  const maxRaise = (mySeat?.chips ?? 0) + (mySeat?.streetBet ?? 0)
  const [raiseTo, setRaiseTo] = useState(view.public.minRaiseTo)
  const [confirmRestart, setConfirmRestart] = useState(false)
  const [showRules, setShowRules] = useState(false)
  const isHost = room.hostId === session?.playerId
  useEffect(() => setRaiseTo(Math.min(maxRaise, view.public.minRaiseTo)), [view.public.minRaiseTo, maxRaise])
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '玩家'
  const actions = new Set(view.availableActions)
  const mySeatIndex = Math.max(0, view.public.seats.findIndex(seat => seat.id === session?.playerId))
  const positions = useMemo(() => view.public.seats.map((_, index) => {
    const relative = (index - mySeatIndex + view.public.seats.length) % view.public.seats.length
    const angle = (relative / view.public.seats.length) * Math.PI * 2 + Math.PI / 2
    return { '--seat-x': `${50 + Math.cos(angle) * 43}%`, '--seat-y': `${50 + Math.sin(angle) * 42}%` } as React.CSSProperties
  }), [mySeatIndex, view.public.seats])

  const act = (action: string, amount?: number) => send('poker:action', { action, amount })
  return (
    <section className="poker-screen game-screen">
      <header className="poker-header screen-pad"><div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>德州扑克</h1><span><Radio size={13} />局域网</span></div></div><div className="poker-header-actions"><div className="room-id"><small>房间</small>{room.roomId}</div><button type="button" className="poker-rules-button" aria-label="查看德州扑克规则" title="查看德州扑克规则" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button>{isHost && view.public.phase !== 'GAME_FINISHED' ? <button className="poker-restart-button" onClick={() => setConfirmRestart(true)}><RotateCcw size={15} />重开</button> : null}</div></header>
      <LandscapeHint />
      <div className="poker-hand-meta screen-pad"><span>第 {view.public.handNumber} 手</span><strong>{pokerPhaseLabel(view.public.phase)}</strong><span>当前注额 {view.public.currentBet}</span></div>
      <div className="poker-stage">
        <div className="poker-table">
          <div className="pot-label"><Coins size={16} />底池 <strong>{view.public.potTotal}</strong></div>
          <div className="community-cards">
          {[0, 1, 2, 3, 4].map(index => <PlayingCard key={index} card={view.public.communityCards[index]} hidden={!view.public.communityCards[index]} small />)}
          </div>
          {view.public.seats.map((seat, index) => {
            const player = room.players.find(item => item.id === seat.id)
            return <div className={`poker-seat ${seat.id === view.public.actingPlayerId ? 'acting' : ''} ${seat.folded ? 'folded' : ''}`} style={positions[index]} key={seat.id}>
              <div className="seat-marks">{seat.isDealer ? <i>D</i> : null}{seat.isSmallBlind ? <b>SB</b> : null}{seat.isBigBlind ? <b>BB</b> : null}</div>
              <div className="seat-bubble"><strong>{seat.id === session?.playerId ? '你' : player?.nickname ?? '玩家'}</strong><span>{seat.chips} 筹码</span><small>{seat.folded ? '已弃牌' : seat.allIn ? '全下' : seat.id === view.public.actingPlayerId ? '当前行动' : '等待'}</small></div>
              {seat.streetBet > 0 ? <span className="seat-bet">下注 {seat.streetBet}</span> : null}
              <div className="seat-cards">{seat.revealedCards ? seat.revealedCards.map((card, i) => <PlayingCard card={card} small key={i} />) : seat.id !== session?.playerId ? <><PlayingCard hidden small /><PlayingCard hidden small /></> : null}</div>
            </div>
          })}
          <div className="my-cards">{view.myCards.map((card, i) => <PlayingCard card={card} key={i} />)}</div>
        </div>
      </div>
      <div className="poker-actions screen-pad">
        <div className="turn-line"><span>当前行动：<strong>{view.public.actingPlayerId ? playerName(view.public.actingPlayerId) : '结算中'}</strong></span><span>{view.toCall ? `需跟注 ${view.toCall}` : '可过牌'}</span></div>
        <div className="poker-action-grid">
          <button disabled={!actions.has('FOLD')} className="fold" onClick={() => act('FOLD')}>弃牌</button>
          {actions.has('CHECK') ? <button className="check" onClick={() => act('CHECK')}>过牌</button> : <button disabled={!actions.has('CALL')} className="call" onClick={() => act('CALL')}>跟注 {view.toCall}</button>}
          <button disabled={!actions.has('RAISE_TO')} className="raise" onClick={() => act('RAISE_TO', raiseTo)}>加注</button>
          <button disabled={!actions.has('ALL_IN')} className="all-in" onClick={() => act('ALL_IN')}>全下</button>
        </div>
        {actions.has('RAISE_TO') ? <div className="raise-control"><span>加注到</span><input type="range" min={Math.min(view.public.minRaiseTo, maxRaise)} max={maxRaise} step={10} value={raiseTo} onChange={e => setRaiseTo(Number(e.target.value))} /><strong>{raiseTo}</strong></div> : null}
        {view.public.phase === 'HAND_FINISHED' ? <div className="hand-result">本手结束 · {view.public.lastAwards.map(a => `${a.winnerIds.map(playerName).join('/')} 赢得 ${a.amount}`).join('；')}</div> : null}
        {view.public.phase === 'GAME_FINISHED' && isHost ? <button className="primary-button restart-poker" onClick={() => send('game:restart')}>返回大厅，重新准备</button> : null}
      </div>
      {confirmRestart ? <div className="modal-backdrop" role="presentation" onClick={() => setConfirmRestart(false)}>
        <section className="game-modal confirm-modal" role="alertdialog" aria-modal="true" aria-label="确认放弃本局" onClick={event => event.stopPropagation()}>
          <h2>确认放弃当前牌局？</h2>
          <p>当前手牌、筹码和下注进度将被清空，所有玩家返回大厅并重新准备。</p>
          <div><button className="secondary-button" onClick={() => setConfirmRestart(false)}>继续游戏</button><button className="danger-button" onClick={() => send('game:restart')}>确认放弃</button></div>
        </section>
      </div> : null}
      {showRules ? <GameRulesModal game="POKER" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function pokerPhaseLabel(phase: string) {
  return ({ PREFLOP: '翻牌前', FLOP: '翻牌', TURN: '转牌', RIVER: '河牌', SHOWDOWN: '摊牌', HAND_FINISHED: '本手结束', GAME_FINISHED: '牌局结束' } as Record<string, string>)[phase] ?? phase
}
