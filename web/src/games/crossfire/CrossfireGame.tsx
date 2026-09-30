import { CircleHelp, Crosshair, Eye, RotateCcw, Timer } from 'lucide-react'
import { useEffect, useState } from 'react'
import { GameRulesModal } from '../../components/GameRulesModal'
import { PlayerRing } from '../../components/PlayerRing'
import { RoomBackButton } from '../../components/RoomBackButton'
import { crossfireRoleNames } from '../roleNames'
import type { CrossfireView, RoomView, Session } from '../../types'

interface Props {
  room: RoomView
  session: Session | null
  view: CrossfireView
  send: (type: string, payload?: unknown) => void
  onLeave: () => boolean | void
}

export function CrossfireGame({ room, session, view, send, onLeave }: Props) {
  const [targetId, setTargetId] = useState('')
  const [remaining, setRemaining] = useState(0)
  const [showRules, setShowRules] = useState(false)
  const isHost = room.hostId === session?.playerId
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '玩家'
  const playerLabel = (id: string) => {
    const index = room.players.findIndex(player => player.id === id)
    return id === session?.playerId ? `你(${index + 1}号)` : playerName(id)
  }

  useEffect(() => {
    const deadline = view.public.discussionDeadline
    if (!deadline || view.public.phase !== 'DISCUSSION') { setRemaining(0); return }
    const update = () => setRemaining(Math.max(0, deadline - Date.now()))
    update()
    const timer = window.setInterval(update, 500)
    return () => window.clearInterval(timer)
  }, [view.public.discussionDeadline, view.public.phase])

  const phaseLabel = ({ INITIAL_VIEW: '查看初始身份', INTEL_PASS: '传递情报', SHUFFLE: '确认混洗', DISCUSSION: '讨论阶段', TARGETING: '秘密选目标', RESOLVE: '统一结算', GAME_OVER: '游戏结束' } as const)[view.public.phase]
  const formatShuffleGroup = (ids: string[]) => ids.map(playerLabel).join(' + ')

  return (
    <section className="new-game-screen crossfire-screen screen-pad">
      <header className="game-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>Crossfire</h1><span>房间 {room.roomId}</span></div></div>
        <div className="game-header-actions"><span className="new-game-phase">{phaseLabel}</span><button type="button" aria-label="Crossfire规则" onClick={() => setShowRules(true)}><CircleHelp size={17} /></button></div>
      </header>

      <PlayerRing className="game-player-ring crossfire-player-ring" ariaLabel="Crossfire玩家座位" center={<div className="player-ring-status"><strong>{phaseLabel}</strong><small>{view.public.phase === 'TARGETING' ? `已锁定 ${view.public.targetsSubmitted}/${room.players.length}` : '围绕牌桌讨论与行动'}</small></div>} players={room.players.map(player => ({ id: player.id, nickname: player.nickname, isMe: player.id === session?.playerId, connected: player.connected, current: view.public.phase === 'TARGETING' && !view.public.lockedPlayers.includes(player.id), status: view.public.lockedPlayers.includes(player.id) ? '已锁定' : '讨论中', badge: player.id === session?.playerId ? '你' : undefined }))} />
      <div className="new-game-private crossfire-identity-panel">
        <div className="identity-info-section"><span>初始身份</span><strong><Eye size={16} />{crossfireRoleNames[view.initialRole]}</strong></div>
        {view.seenRoles.length ? <div className="identity-info-section"><span>你曾看到</span><div className="intel-list">{view.seenRoles.map((intel, index) => <span key={`${intel.sourcePlayerId}-${index}`}>{playerLabel(intel.sourcePlayerId)}：{crossfireRoleNames[intel.role]}</span>)}</div></div> : null}
        {view.shuffleHistory.length ? <div className="identity-info-section"><span>参与混洗</span><strong className="shuffle-group-line">{formatShuffleGroup(view.shuffleHistory[view.shuffleHistory.length - 1].playerIds)}</strong>{view.shuffleHistory.length > 1 ? <div className="shuffle-history-list"><span>混洗记录</span>{view.shuffleHistory.map((record, index) => <small key={`${index}-${record.playerIds.join('-')}`}>第{index + 1}次：{record.playerIds.map(playerLabel).join(' / ')}</small>)}</div> : null}</div> : null}
        {view.currentRole ? <div className="identity-info-section current-role"><span>本次混洗后身份</span><strong>{crossfireRoleNames[view.currentRole]}</strong></div> : null}
        {view.finalRole ? <div className="identity-info-section final-role"><span>最终身份</span><strong>{crossfireRoleNames[view.finalRole]}</strong></div> : null}
      </div>

      {view.public.phase === 'INITIAL_VIEW' ? <ActionCard title="查看你的初始角色" text="记住屏幕上的角色后确认，其他玩家看不到你的信息。" button={view.canConfirmInitial ? '我已查看' : '已确认，等待其他玩家'} disabled={!view.canConfirmInitial} onClick={() => send('crossfire:confirm_initial')} /> : null}
      {view.public.phase === 'INTEL_PASS' ? <ActionCard title="情报传递完成" text="系统已按座位方向记录你看过的角色，请确认进入混洗阶段。" button={view.canConfirmIntel ? '确认情报' : '已确认，等待其他玩家'} disabled={!view.canConfirmIntel} onClick={() => send('crossfire:confirm_intel')} /> : null}
      {view.public.phase === 'SHUFFLE' ? <ActionCard title={`确认第 ${view.public.shuffleGroupIndex + 1}/${view.public.shuffleGroupCount} 组混洗`} text={`本组：${view.public.currentShuffleGroup.map(playerLabel).join(' / ')}。三名玩家都确认后才会执行一次身份混洗。`} button={view.canConfirmShuffle ? '确认混洗' : '等待本组玩家'} disabled={!view.canConfirmShuffle} onClick={() => send('crossfire:confirm_shuffle')} /> : null}
      {view.public.phase === 'DISCUSSION' ? <div className="new-game-action-card"><h2><Timer size={18} />讨论阶段</h2><p>面对面讨论并推理身份，剩余时间 {formatTime(remaining)}。</p>{isHost ? <button className="primary-button" onClick={() => send('crossfire:start_targeting')}><Crosshair size={18} />结束讨论，开始选目标</button> : <p className="waiting-action">等待房主结束讨论…</p>}</div> : null}
      {view.public.phase === 'TARGETING' ? <div className="new-game-action-card"><h2>秘密选择射击目标</h2><p>已锁定 {view.public.targetsSubmitted}/{room.players.length} 人，其他人的目标不会提前公开。</p>{view.canSubmitTarget ? <><select className="new-game-select" value={targetId} onChange={event => setTargetId(event.target.value)}><option value="">选择目标</option>{view.targetIds.map(id => <option value={id} key={id}>{playerName(id)}</option>)}</select><button className="primary-button" disabled={!targetId} onClick={() => send('crossfire:target', { targetId })}>锁定目标</button></> : <p className="waiting-action">你已锁定，等待其他玩家…</p>}</div> : null}

      {view.public.phase === 'GAME_OVER' ? <div className="new-game-result final"><h2>{view.public.winnerTeam === 'ASSASSIN' ? '刺客阵营达成目标' : '特工阵营守住 VIP'}</h2><div className="role-reveal-list">{Object.entries(view.public.revealedRoles).map(([id, role]) => <span key={id}>{playerName(id)}：{crossfireRoleNames[role]}</span>)}</div>{isHost ? <button className="primary-button" onClick={() => send('game:restart')}><RotateCcw size={17} />返回大厅</button> : null}</div> : null}
      {showRules ? <GameRulesModal game="CROSSFIRE" onClose={() => setShowRules(false)} /> : null}
    </section>
  )
}

function ActionCard({ title, text, button, disabled, onClick }: { title: string; text: string; button: string; disabled: boolean; onClick: () => void }) {
  return <div className="new-game-action-card"><h2>{title}</h2><p>{text}</p><button className="primary-button" disabled={disabled} onClick={onClick}>{button}</button></div>
}

function formatTime(milliseconds: number) {
  const totalSeconds = Math.ceil(milliseconds / 1000)
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`
}
