import { Check, CircleHelp, Crown, Eye, History, RotateCcw, Shield, Swords, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { PlayerRing } from '../../components/PlayerRing'
import { RoomBackButton } from '../../components/RoomBackButton'
import type { AvalonView, RoomView, Session } from '../../types'
import { avalonRoleNames, evilAvalonRoles } from './avalonData'

interface Props { room: RoomView; session: Session | null; view: AvalonView; send: (type: string, payload?: unknown) => void; onLeave: () => boolean | void }

export function AvalonGame({ room, session, view, send, onLeave }: Props) {
  const [team, setTeam] = useState<string[]>([])
  const [showHistory, setShowHistory] = useState(false)
  const [showIntro, setShowIntro] = useState(false)
  const [confirmRestart, setConfirmRestart] = useState(false)
  const isEvil = evilAvalonRoles.has(view.myRole)
  const isHost = room.hostId === session?.playerId
  const playerName = (id: string) => room.players.find(player => player.id === id)?.nickname ?? '未知'
  const completed = view.public.missions.filter(m => m.approved && m.success !== undefined)
  const successCount = completed.filter(mission => mission.success).length
  const failureCount = completed.length - successCount

  useEffect(() => setTeam([]), [view.public.round, view.public.leaderId])
  const toggle = (id: string) => setTeam(current => current.includes(id) ? current.filter(item => item !== id) : current.length < view.public.teamSize ? [...current, id] : current)

  return (
    <section className="avalon-screen game-screen screen-pad">
      <header className="game-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><h1>阿瓦隆</h1><span>房间 {room.roomId}</span></div></div>
        <div className="game-header-actions">
          <div className="phase-copy">第 {view.public.round} 轮任务</div>
          <button aria-label="查看历史记录" onClick={() => setShowHistory(true)}><History size={17} /><span>历史</span></button>
          <button aria-label="查看游戏介绍" onClick={() => setShowIntro(true)}><CircleHelp size={18} /></button>
        </div>
      </header>
      <div className="mission-rail" aria-label="任务进度">
        {[0, 1, 2, 3, 4].map(index => {
          const result = completed[index]
          return <div key={index} className={`mission-node ${result ? result.success ? 'success' : 'failure' : index === completed.length ? 'current' : ''}`}>
            {result ? result.success ? <Check /> : <X /> : index + 1}
          </div>
        })}
      </div>
      <div className="proposal-meta"><span><Crown size={16} />队长：{playerName(view.public.leaderId)}</span><span>本轮投票 {view.public.currentProposal}/5 · 累计 {view.public.totalVoteCount}</span></div>
      <div className="mission-counts"><span>任务成功 <strong>{successCount}</strong></span><span>任务失败 <strong>{failureCount}</strong></span><span>连续否决 <strong>{view.public.proposalRejects}</strong></span></div>

      <PlayerRing className="game-player-ring avalon-player-ring" ariaLabel="阿瓦隆玩家座位" center={<div className="table-center"><Shield /><strong>{phaseLabel(view.public.phase)}</strong><span>{view.public.teamSize} 人任务</span></div>} players={room.players.map(player => {
        const selected = (view.canSelectTeam ? team : view.public.selectedTeam).includes(player.id)
        return { id: player.id, nickname: player.nickname, isMe: player.id === session?.playerId, connected: player.connected, selected, disabled: !view.canSelectTeam, status: player.id === view.public.leaderId ? '队长' : '玩家', badge: selected ? '已选' : undefined, onClick: () => toggle(player.id) }
      })} />

      <div className={`identity-panel ${isEvil ? 'evil' : ''}`}>
        <div className="identity-icon">{isEvil ? <Swords /> : <Eye />}</div>
        <div><span>你的身份</span><h2>{avalonRoleNames[view.myRole]}</h2>
          <p>{view.knowledge.length ? view.knowledge.map(k => `${playerName(k.playerId)}：${k.hint}`).join(' · ') : '谨慎判断其他玩家的阵营。'}</p>
        </div>
      </div>

      <div className="game-actions">
        {view.canSelectTeam ? <button className="primary-button" disabled={team.length !== view.public.teamSize} onClick={() => send('avalon:select_team', { team })}>确认队伍 {team.length}/{view.public.teamSize}</button> : null}
        {view.canVote ? <div className="split-actions"><button className="danger-button" onClick={() => send('avalon:vote', { approve: false })}><X />反对</button><button className="success-button" onClick={() => send('avalon:vote', { approve: true })}><Check />赞成</button></div> : null}
        {view.canSubmitMission ? <div className="split-actions"><button className="success-button" onClick={() => send('avalon:mission', { success: true })}><Check />任务成功</button>{isEvil ? <button className="danger-button" onClick={() => send('avalon:mission', { success: false })}><X />任务失败</button> : null}</div> : null}
        {view.canAssassinate ? <div className="assassinate-panel"><strong>选择要刺杀的梅林</strong><div>{room.players.filter(p => p.id !== session?.playerId).map(p => <button key={p.id} onClick={() => send('avalon:assassinate', { targetId: p.id })}>{p.nickname}</button>)}</div></div> : null}
        {!view.canSelectTeam && !view.canVote && !view.canSubmitMission && !view.canAssassinate && view.public.phase !== 'FINISHED' ? <p className="waiting-action">等待其他玩家操作…</p> : null}
        {view.public.phase === 'FINISHED' ? <div className="result-panel"><h2>{view.public.winner === 'GOOD' ? '好人阵营获胜' : '邪恶阵营获胜'}</h2><p>{view.public.endReason}</p>{isHost ? <button className="primary-button" onClick={() => send('game:restart')}>返回大厅，重新准备</button> : null}</div> : null}
      </div>
      {showHistory ? <div className="modal-backdrop" role="presentation" onClick={() => setShowHistory(false)}>
        <section className="game-modal history-modal" role="dialog" aria-modal="true" aria-label="投票与任务历史" onClick={event => event.stopPropagation()}>
          <header><div><History size={19} /><h2>历史记录</h2></div><button aria-label="关闭历史记录" onClick={() => setShowHistory(false)}><X size={20} /></button></header>
          <div className="history-stats"><span>投票 {view.public.totalVoteCount} 次</span><span>成功任务 {successCount}</span><span>失败任务 {failureCount}</span></div>
          <h3>队伍投票</h3>
          <div className="vote-history-list">
            {view.public.voteHistory.length ? view.public.voteHistory.slice().reverse().map(record => <article key={`${record.round}-${record.proposal}`} className={record.approved ? 'approved' : 'rejected'}>
              <div className="history-line"><strong>第 {record.round} 轮 · 第 {record.proposal} 次</strong><span>{record.approved ? '队伍通过' : '队伍否决'}</span></div>
              <p>队长：{playerName(record.leaderId)} · 队伍：{record.team.map(playerName).join('、')}</p>
              <div className="vote-name-list">{Object.entries(record.votes).map(([id, approve]) => <span className={approve ? 'yes' : 'no'} key={id}>{playerName(id)} {approve ? '赞成' : '反对'}</span>)}</div>
            </article>) : <p className="empty-history">尚未完成队伍投票</p>}
          </div>
          <h3>任务结果</h3>
          <div className="mission-history-list">{completed.length ? completed.map(mission => <div key={mission.round} className={mission.success ? 'success' : 'failure'}><strong>第 {mission.round} 轮</strong><span>{mission.success ? '任务成功' : `任务失败 · ${mission.failCount ?? 0} 张失败票`}</span><small>{mission.team.map(playerName).join('、')}</small></div>) : <p className="empty-history">尚未完成任务</p>}</div>
        </section>
      </div> : null}

      {showIntro ? <div className="modal-backdrop" role="presentation" onClick={() => setShowIntro(false)}>
        <section className="game-modal intro-modal" role="dialog" aria-modal="true" aria-label="阿瓦隆游戏介绍" onClick={event => event.stopPropagation()}>
          <header><div><CircleHelp size={20} /><h2>阿瓦隆简介</h2></div><button aria-label="关闭游戏介绍" onClick={() => setShowIntro(false)}><X size={20} /></button></header>
          <ol>
            <li>队长选择任务队员，所有玩家秘密投票是否批准队伍。</li>
            <li>超过半数赞成时队伍通过；连续五次否决则邪恶阵营获胜。</li>
            <li>任务队员秘密提交成功或失败，好人只能提交成功。</li>
            <li>率先完成三次失败任务时邪恶获胜；好人三次成功后进入刺杀。</li>
            <li>刺客找出梅林则邪恶翻盘，否则好人最终获胜。</li>
          </ol>
          <p>7 人及以上时，第 4 轮任务需要至少两张失败票才会失败。</p>
          {isHost && view.public.phase !== 'FINISHED' ? <button className="abandon-button" onClick={() => { setShowIntro(false); setConfirmRestart(true) }}><RotateCcw size={17} />放弃本局并重开</button> : null}
        </section>
      </div> : null}

      {confirmRestart ? <div className="modal-backdrop" role="presentation" onClick={() => setConfirmRestart(false)}>
        <section className="game-modal confirm-modal" role="alertdialog" aria-modal="true" aria-label="确认放弃本局" onClick={event => event.stopPropagation()}>
          <h2>确认放弃当前游戏？</h2>
          <p>当前身份、投票和任务进度将被清空，所有玩家返回大厅并重新准备。</p>
          <div><button className="secondary-button" onClick={() => setConfirmRestart(false)}>继续游戏</button><button className="danger-button" onClick={() => send('game:restart')}>确认放弃</button></div>
        </section>
      </div> : null}
    </section>
  )
}

function phaseLabel(phase: AvalonView['public']['phase']) {
  return ({ TEAM_SELECTION: '组建队伍', TEAM_VOTE: '队伍投票', MISSION: '执行任务', ASSASSINATION: '刺杀梅林', FINISHED: '游戏结束' } as const)[phase]
}
