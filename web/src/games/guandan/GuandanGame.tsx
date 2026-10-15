import { CircleHelp, Radio, Send, SkipForward, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { LandscapeHint } from '../../components/LandscapeHint'
import { PlayerNameplate } from '../../components/PlayerNameplate'
import { RoomBackButton } from '../../components/RoomBackButton'
import type { GuandanCard, GuandanCombinationType, GuandanView, RoomView, Session } from '../../types'

interface Props {
  room: RoomView
  session: Session | null
  view: GuandanView
  send: (type: string, payload?: unknown) => void
  onLeave: () => boolean | void
}

const suitSymbols: Record<GuandanCard['suit'], string> = {
  CLUBS: '♣', DIAMONDS: '♦', HEARTS: '♥', SPADES: '♠', JOKER: '★',
}

export function GuandanGame({ room, session, view, send, onLeave }: Props) {
  const [selected, setSelected] = useState<string[]>([])
  const [showRules, setShowRules] = useState(false)
  const isHost = room.hostId === session?.playerId
  const isMyTurn = view.public.currentPlayerId === session?.playerId
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '玩家'

  useEffect(() => setSelected([]), [view.public.turnNumber, view.public.currentPlayerId])

  const toggleCard = (id: string) => {
    if (!view.canPlay && !view.canTribute && !view.canReturnTribute) return
    if (view.canTribute || view.canReturnTribute) {
      setSelected(current => current.includes(id) ? [] : [id])
      return
    }
    setSelected(current => current.includes(id) ? current.filter(cardId => cardId !== id) : [...current, id])
  }

  const play = () => {
    if (!selected.length) return
    send('guandan:play', { cards: selected })
    setSelected([])
  }

  const teamLabel = (index: number) => index % 2 === 0 ? '一队' : '二队'
  const rankingByPlayer = new Map(view.public.ranking.map(item => [item.playerId, item.position]))
  const mySeatIndex = Math.max(0, room.players.findIndex(player => player.id === session?.playerId))
  const seatPosition = (index: number) => ['self', 'right', 'partner', 'left'][(index - mySeatIndex + 4) % 4]
  const tribute = view.public.tribute
  const submitTribute = () => {
    if (selected.length !== 1) return
    send('guandan:tribute', { cardId: selected[0] })
    setSelected([])
  }
  const returnTribute = () => {
    if (selected.length !== 1 || !view.tributeTargetPlayerId) return
    send('guandan:return_tribute', { targetPlayerId: view.tributeTargetPlayerId, cardId: selected[0] })
    setSelected([])
  }

  return (
    <section className="guandan-screen game-screen screen-pad">
      <header className="game-header guandan-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>掼蛋</h1><span><Radio size={13} />房间 {room.roomId}</span></div></div>
        <div className="game-header-actions"><div className="guandan-turn"><Users size={15} />{isMyTurn ? '轮到你出牌' : `${playerName(view.public.currentPlayerId)} 出牌`}</div><button type="button" aria-label="掼蛋规则" title="掼蛋规则" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button></div>
      </header>
      <LandscapeHint />

      <div className="guandan-scorebar"><span>一队 <strong>{view.public.teamScores[0] ?? 0}</strong></span><span>二队 <strong>{view.public.teamScores[1] ?? 0}</strong></span><small>第 {view.public.roundNumber} 局 · 打 {view.public.currentLevel}</small></div>

      <div className="guandan-seat-table" aria-label="掼蛋四人搭档牌桌">
        <div className="guandan-partner-line" aria-hidden="true" />
        <div className="guandan-center"><span>{view.public.phase === 'FINISHED' ? (view.public.matchWinnerTeam != null ? `第 ${view.public.matchWinnerTeam + 1} 队赢得整场比赛` : `第 ${(view.public.winnerTeam ?? 0) + 1} 队本局获胜`) : view.public.phase === 'TRIBUTE' ? '进贡与还贡' : isMyTurn ? '请出牌' : '等待出牌'}</span>
          {view.public.lastPlay ? <div className="last-play"><small>{playerName(view.public.lastPlay.playerId)} · {combinationLabel(view.public.lastPlay.type)}</small><div>{view.public.lastPlay.cards.map(card => <GuandanCardView card={card} small key={card.id} />)}</div></div> : <small>新一轮</small>}
          {view.public.lastEvent ? <small className="guandan-event">{view.public.lastEvent}</small> : null}
        </div>
        {room.players.map((player, index) => <PlayerNameplate
          key={player.id}
          index={index}
          name={player.id === session?.playerId ? '你' : player.nickname}
          status={`${teamLabel(index)} · ${view.public.handSizes[player.id] ?? 0} 张`}
          detail={rankingByPlayer.has(player.id) ? `第 ${rankingByPlayer.get(player.id)} 名` : view.public.passedPlayerIds.includes(player.id) ? '已过牌' : player.id === view.public.currentPlayerId ? '当前行动' : '等待出牌'}
          badge={player.id === session?.playerId ? '本人' : undefined}
          className={`guandan-seat ${seatPosition(index)} ${view.public.passedPlayerIds.includes(player.id) ? 'passed' : ''}`}
          current={player.id === view.public.currentPlayerId}
          offline={!player.connected}
        />)}
      </div>

      <section className="guandan-hand-panel">
        <div className="guandan-hand-heading"><span>我的手牌 <strong>{view.myHand.length}</strong></span><small>{view.canPlay ? '点击选择要出的牌' : view.canTribute ? '请选择自己最大的牌进贡' : view.canReturnTribute ? '请选择一张牌还贡' : '等待其他玩家操作'}</small></div>
        <div className="guandan-hand" aria-label="我的手牌">
          {view.myHand.map(card => <GuandanCardButton card={card} selected={selected.includes(card.id)} disabled={!view.canPlay && !view.canTribute && !view.canReturnTribute} onClick={() => toggleCard(card.id)} key={card.id} />)}
        </div>
      </section>

      {view.public.phase === 'FINISHED' ? <div className="guandan-result"><strong>{view.public.matchWinnerTeam != null ? `第 ${view.public.matchWinnerTeam + 1} 队赢得整场比赛` : `第 ${(view.public.winnerTeam ?? 0) + 1} 队本局获胜`}</strong>{view.public.ranking.length ? <div className="guandan-ranking">{view.public.ranking.map(item => <span key={item.playerId}>第{item.position}名：{playerName(item.playerId)}</span>)}</div> : null}{view.public.matchWinnerTeam == null && isHost ? <button className="primary-button" disabled={!view.canContinue} onClick={() => send('guandan:continue')}>继续下一局</button> : null}{isHost ? <button className="secondary-button" onClick={() => send('game:restart')}>返回大厅</button> : <span>等待房主选择继续或返回大厅</span>}</div> : view.public.phase === 'TRIBUTE' ? <div className="guandan-actions"><span className="guandan-tribute-status">{tribute?.antiTribute ? '本轮抗贡，头游先出' : `已进贡 ${tribute?.submittedPlayerIds.length ?? 0}/${tribute?.requirements.filter(item => item.fromPlayerId !== '').length ?? 0}，已还贡 ${tribute?.returnedPlayerIds.length ?? 0}`}</span><button className="primary-button" disabled={!view.canTribute || selected.length !== 1} onClick={submitTribute}>进贡</button><button className="primary-button" disabled={!view.canReturnTribute || selected.length !== 1} onClick={returnTribute}>还贡</button></div> : <div className="guandan-actions"><button className="secondary-button" disabled={!view.canPass} onClick={() => send('guandan:pass')}><SkipForward size={17} />过牌</button><button className="primary-button" disabled={!view.canPlay || !selected.length} onClick={play}><Send size={17} />出牌 {selected.length ? `(${selected.length})` : ''}</button></div>}
      {showRules ? <GameRulesModal game="GUANDAN" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function GuandanCardButton({ card, selected, disabled, onClick }: { card: GuandanCard; selected: boolean; disabled: boolean; onClick: () => void }) {
  return <button type="button" className={`guandan-card-button ${selected ? 'selected' : ''}`} disabled={disabled} onClick={onClick}><GuandanCardView card={card} /></button>
}

function GuandanCardView({ card, small = false }: { card: GuandanCard; small?: boolean }) {
  const red = card.suit === 'HEARTS' || card.suit === 'DIAMONDS'
  const label = card.rank === 'SMALL_JOKER' ? '小王' : card.rank === 'BIG_JOKER' ? '大王' : card.rank
  return <span className={`guandan-card ${red ? 'red' : ''} ${small ? 'small' : ''}`} aria-label={`${label}${suitSymbols[card.suit]}`}><strong>{label}</strong><i>{suitSymbols[card.suit]}</i></span>
}

function combinationLabel(type: GuandanCombinationType) {
  return ({ SINGLE: '单牌', PAIR: '对子', TRIPLE: '三张', TRIPLE_WITH_PAIR: '三带二', STRAIGHT: '顺子', CONSECUTIVE_PAIRS: '三连对', CONSECUTIVE_TRIPLES: '钢板', STRAIGHT_FLUSH: '同花顺', BOMB: '炸弹', ROCKET: '四王炸' } as Record<GuandanCombinationType, string>)[type]
}
