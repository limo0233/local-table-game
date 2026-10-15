import { Bot, Check, Copy, Crown, Pencil, QrCode, Radio, Shield, Trash2, UserRound, Wifi, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { RoomBackButton } from '../components/RoomBackButton'
import { AvalonSetupPanel } from '../games/avalon/AvalonSetupPanel'
import { isHostConsole } from '../hostDeployment'
import type { AvalonConfig, LiarDiceConfig, RoomView, Session } from '../types'

interface Props { room: RoomView; session: Session | null; avalonConfig: AvalonConfig | null; liarDiceConfig: LiarDiceConfig | null; send: (type: string, payload?: unknown) => void; onLeave: () => boolean | void }

export function Lobby({ room, session, avalonConfig, liarDiceConfig, send, onLeave }: Props) {
  const me = room.players.find(player => player.id === session?.playerId)
  const isHost = room.hostId === session?.playerId
  const hostConsole = isHostConsole(location.hostname)
  const [joinUrl, setJoinUrl] = useState<string | null>(() => hostConsole ? null : `${location.origin}/room/${room.roomId}`)
  const [copied, setCopied] = useState(false)
  const [pokerConfig, setPokerConfig] = useState({ startingChips: 1000, smallBlind: 10, bigBlind: 20, maxPlayers: 8 })
  const [liarDiceCount, setLiarDiceCount] = useState(5)
  const [serverDeployed, setServerDeployed] = useState<boolean | null>(() => hostConsole ? null : true)
  const [nicknameDraft, setNicknameDraft] = useState(me?.nickname ?? '')

  useEffect(() => setNicknameDraft(me?.nickname ?? ''), [me?.nickname])
  useEffect(() => {
    if (liarDiceConfig) setLiarDiceCount(liarDiceConfig.diceCount)
    else if (room.gameType === 'LIARS_DICE') setLiarDiceCount(5)
  }, [liarDiceConfig, room.gameType])

  useEffect(() => {
    const controller = new AbortController()
    const refreshJoinUrl = async () => {
      try {
        const response = await fetch('/api/health', { cache: 'no-store', signal: controller.signal })
        if (!response.ok) return
        const health = await response.json() as { addresses: string[]; selectedAddress?: string; port: number; deployed: boolean }
        setServerDeployed(hostConsole ? health.deployed : true)
        const ip = health.deployed && health.selectedAddress && health.addresses.includes(health.selectedAddress)
          ? health.selectedAddress
          : health.deployed ? health.addresses[0] : undefined
        setJoinUrl(ip ? `http://${ip}:${health.port}/room/${room.roomId}` : hostConsole ? null : `${location.origin}/room/${room.roomId}`)
      } catch {
        if (!controller.signal.aborted && hostConsole) setJoinUrl(null)
      }
    }
    void refreshJoinUrl()
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refreshJoinUrl()
    }, 3_000)
    return () => {
      controller.abort()
      window.clearInterval(timer)
    }
  }, [hostConsole, room.roomId])

  const gameLabel = room.gameType === 'AVALON' ? '阿瓦隆' : room.gameType === 'GUANDAN' ? '掼蛋' : room.gameType === 'TIME_BOMB' ? 'TimeBomb' : room.gameType === 'CROSSFIRE' ? 'Crossfire' : room.gameType === 'GOOD_COP_BAD_COP' ? 'Good Cop Bad Cop' : room.gameType === 'LIARS_DICE' ? '骰子模拟器' : '德州扑克'
  const requiredPlayers = room.gameType === 'AVALON' ? avalonConfig?.playerCount ?? 5 : room.gameType === 'GUANDAN' ? 4 : room.gameType === 'TIME_BOMB' ? 4 : room.gameType === 'CROSSFIRE' ? 5 : room.gameType === 'GOOD_COP_BAD_COP' ? 4 : 2
  const playerLimit = room.gameType === 'AVALON' ? requiredPlayers : room.gameType === 'GUANDAN' ? 4 : room.gameType === 'TIME_BOMB' ? 8 : room.gameType === 'CROSSFIRE' ? 10 : room.gameType === 'GOOD_COP_BAD_COP' ? 8 : room.gameType === 'LIARS_DICE' ? 10 : 8
  const hasEnoughPlayers = room.gameType === 'AVALON' ? room.players.length === requiredPlayers : room.players.length >= requiredPlayers
  const canStart = isHost && hasEnoughPlayers && room.players.every(p => p.isHost || p.ready)
  const testBotsEnabled = import.meta.env.VITE_ENABLE_TEST_BOTS !== 'false'
  const botCount = room.players.filter(player => player.isBot).length
  const canManageBots = isHost && testBotsEnabled && room.status === 'WAITING' && serverDeployed === false
  const copy = async () => {
    if (!joinUrl) return
    await navigator.clipboard?.writeText(joinUrl)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  return (
    <section className="lobby-screen screen-pad">
      <header className="room-header">
        <div className="room-header-leading"><RoomBackButton isHost={isHost} onLeave={onLeave} /><div><span>{gameLabel}</span><h1>等待玩家加入</h1></div></div>
        <div className="room-id"><small>房间</small>{room.roomId}</div>
      </header>
      <div className={`share-panel ${joinUrl ? '' : 'local-only'}`}>
        {joinUrl ? <>
          <div className="qr-wrap"><img src={`/api/qr?text=${encodeURIComponent(joinUrl)}`} alt="加入房间二维码" /><QrCode size={22} /></div>
          <div className="share-copy"><Wifi size={20} /><strong>同一 Wi-Fi 或房主热点</strong><p>{joinUrl}</p><button onClick={copy}>{copied ? <Check size={17} /> : <Copy size={17} />}{copied ? '已复制' : '复制地址'}</button></div>
        </> : <div className="local-only-share"><Wifi size={24} /><div><strong>当前仅本机可用</strong><p>房间已经创建。连接路由器 Wi-Fi 或开启手机热点后，分享二维码会自动出现。</p></div></div>}
      </div>
      {room.gameType === 'AVALON' ? <AvalonSetupPanel config={avalonConfig} currentPlayers={room.players.length} isHost={isHost} send={send} /> : null}
      {room.gameType === 'LIARS_DICE' && isHost ? <div className="liardice-config"><div><strong>每位玩家骰子数量</strong><small>房主统一设置，所有玩家使用相同数量；每人 1–10 颗。</small></div><label className="liardice-count-field"><span>骰子数量</span><select value={liarDiceCount} onChange={event => setLiarDiceCount(Number(event.target.value))}>{Array.from({ length: 10 }, (_, index) => index + 1).map(count => <option value={count} key={count}>{count} 颗</option>)}</select><span>统一</span></label><button className="secondary-button" onClick={() => send('liarsdice:configure', { diceCount: liarDiceCount })}>保存骰子数量</button></div> : null}
      <div className="lobby-actions">
        <div className="nickname-editor">
          <label htmlFor="lobby-nickname"><Pencil size={15} />我的昵称{me?.ready ? <small>已准备，需取消准备后修改</small> : null}</label>
          <div><input id="lobby-nickname" maxLength={24} value={nicknameDraft} disabled={Boolean(me?.ready)} onChange={event => setNicknameDraft(event.target.value)} /><button disabled={Boolean(me?.ready) || !nicknameDraft.trim() || nicknameDraft.trim() === me?.nickname} onClick={() => send('player:rename', { nickname: nicknameDraft.trim() })}>保存</button></div>
        </div>
        {isHost && room.gameType === 'POKER' ? <div className="poker-config">
          <label>初始筹码<input type="number" min="100" step="100" value={pokerConfig.startingChips} onChange={e => setPokerConfig({ ...pokerConfig, startingChips: Number(e.target.value) })} /></label>
          <label>小盲<input type="number" min="1" value={pokerConfig.smallBlind} onChange={e => setPokerConfig({ ...pokerConfig, smallBlind: Number(e.target.value) })} /></label>
          <label>大盲<input type="number" min={pokerConfig.smallBlind} value={pokerConfig.bigBlind} onChange={e => setPokerConfig({ ...pokerConfig, bigBlind: Number(e.target.value) })} /></label>
        </div> : null}
        {canManageBots ? <div className="test-bot-panel"><div><strong><Bot size={16} />测试玩家</strong><small>仅用于单人验证流程 · 当前 {botCount} 个</small></div><div className="test-bot-actions"><button className="secondary-button" disabled={room.players.length >= playerLimit} onClick={() => send('room:bots', { action: 'fill' })}><Bot size={15} />添加机器人</button><button className="danger-button" disabled={!botCount} onClick={() => send('room:bots', { action: 'clear' })}><Trash2 size={15} />移除机器人</button></div></div> : null}
        {!isHost ? <button className={me?.ready ? 'secondary-button' : 'primary-button'} onClick={() => send('player:ready', { ready: !me?.ready })}>{me?.ready ? '取消准备' : '我已准备'}</button> : null}
        {isHost ? <button className="primary-button" disabled={!canStart} onClick={() => send('game:start', room.gameType === 'POKER' ? { pokerConfig } : {})}><Shield size={18} />开始游戏</button> : null}
        {isHost && !canStart ? <p className="helper-text">{!hasEnoughPlayers ? `等待玩家加入（需要 ${requiredPlayers} 人）` : '等待所有玩家准备'}</p> : null}
      </div>
      <div className="lobby-title"><div><UserRound size={19} />玩家 {room.players.length}/{playerLimit}</div><span><Radio size={15} />实时同步</span></div>
      <div className="player-list">
        {room.players.map((player, index) => <div className={`player-row ${player.connected ? '' : 'offline'}`} key={player.id}>
          <div className="player-seat-number">{String(index + 1).padStart(2, '0')}</div>
          <div><strong>{player.nickname}</strong><small>{player.id === session?.playerId ? '你' : player.connected ? '已连接' : '重连中'}</small></div>
          {player.isHost ? <span className="host-mark"><Crown size={15} />房主</span> : player.isBot && serverDeployed === false ? <span className="bot-mark"><Bot size={14} />测试机器人</span> : <span className={player.ready ? 'ready-mark' : 'wait-mark'}>{player.ready ? <><Check size={15} />已准备</> : '等待准备'}</span>}
          {isHost && !player.isHost ? <button className="kick-button" aria-label={`踢出 ${player.nickname}`} onClick={() => send('player:kick', { targetId: player.id })}><X size={14} /></button> : null}
        </div>)}
      </div>
    </section>
  )
}
