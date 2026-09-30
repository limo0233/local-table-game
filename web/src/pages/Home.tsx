import { CloudUpload, Crown, Power, RefreshCw, Router, Settings, Users } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { GameRulesModal } from '../components/GameRulesModal'
import { type DeploymentResponse, type HostDeploymentStatus, isHostConsole, isValidPort, selectLanAddress } from '../hostDeployment'
import type { GameType } from '../types'

interface Props {
  connected: boolean
  initialRoomId?: string
  onCreate: (game: GameType, nickname: string) => void
  onJoin: (roomId: string, nickname: string) => void
}

export function Home({ connected, initialRoomId, onCreate, onJoin }: Props) {
  const [nickname, setNickname] = useState(() => localStorage.getItem('lan-board-game:nickname') ?? '')
  const [roomId, setRoomId] = useState(initialRoomId ?? '')
  const hostConsole = isHostConsole(location.hostname)
  const [hostStatus, setHostStatus] = useState<HostDeploymentStatus | null>(null)
  const [selectedAddress, setSelectedAddress] = useState('')
  const [port, setPort] = useState('8787')
  const [deploying, setDeploying] = useState(false)
  const [undeploying, setUndeploying] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [deployError, setDeployError] = useState<string | null>(null)
  const [deployMessage, setDeployMessage] = useState<string | null>(null)
  const [rulesGame, setRulesGame] = useState<GameType | null>(null)

  const loadHostStatus = useCallback(async (silent = false, signal?: AbortSignal) => {
    try {
      const response = await fetch('/api/host/config', { cache: 'no-store', signal })
      if (!response.ok) throw new Error('无法读取本机网络配置')
      const next = await response.json() as HostDeploymentStatus
      setHostStatus(next)
      setSelectedAddress(current => next.networkOptions.some(option => option.address === current)
        ? current
        : selectLanAddress(next) ?? '')
      setPort(String(next.port))
      if (!silent) setDeployError(null)
      return true
    } catch (cause) {
      if (signal?.aborted) return false
      setDeployError(cause instanceof Error ? cause.message : '读取网络配置失败')
      return false
    }
  }, [])

  useEffect(() => {
    if (!hostConsole || initialRoomId) return
    const controller = new AbortController()
    const refresh = (silent: boolean) => void loadHostStatus(silent, controller.signal)
    refresh(false)
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refresh(true)
    }, 3_000)
    const onFocus = () => refresh(true)
    window.addEventListener('focus', onFocus)
    return () => {
      controller.abort()
      window.clearInterval(timer)
      window.removeEventListener('focus', onFocus)
    }
  }, [hostConsole, initialRoomId, loadHostStatus])

  const deploy = async () => {
    if (!selectedAddress || !isValidPort(port)) return
    setDeploying(true)
    setDeployError(null)
    setDeployMessage(null)
    try {
      const response = await fetch(`/api/host/deploy?address=${encodeURIComponent(selectedAddress)}&port=${encodeURIComponent(port)}`, { method: 'POST' })
      const result = await response.json() as DeploymentResponse
      if (!response.ok || !result.ok) throw new Error(result.message || '部署失败')
      setHostStatus(current => current ? { ...current, selectedAddress: result.selectedAddress, port: result.port, deployed: true } : current)
      if (result.restarting) {
        window.setTimeout(() => location.replace(`http://127.0.0.1:${result.port}/`), 1_050)
      } else {
        await loadHostStatus()
        setDeployMessage('局域网服务器已部署')
        setDeploying(false)
      }
    } catch (cause) {
      setDeployError(cause instanceof Error ? cause.message : '部署失败，请重试')
      setDeploying(false)
    }
  }

  const undeploy = async () => {
    if (!hostStatus?.deployed) return
    setUndeploying(true)
    setDeployError(null)
    setDeployMessage(null)
    try {
      const response = await fetch('/api/host/undeploy', { method: 'POST' })
      const result = await response.json() as DeploymentResponse
      if (!response.ok || !result.ok) throw new Error(result.message || '关闭部署失败')
      setHostStatus(current => current ? { ...current, deployed: false } : current)
      setDeployMessage('局域网部署已关闭，现在仅房主本机可访问')
      if (result.restarting) {
        window.setTimeout(() => location.replace(`http://127.0.0.1:${result.port}/`), 1_050)
      } else {
        await loadHostStatus(true)
        setUndeploying(false)
      }
    } catch (cause) {
      setDeployError(cause instanceof Error ? cause.message : '关闭部署失败，请重试')
      setUndeploying(false)
    }
  }

  const refreshHostStatus = async () => {
    setRefreshing(true)
    setDeployError(null)
    setDeployMessage(null)
    const refreshed = await loadHostStatus()
    if (refreshed) setDeployMessage('网络地址已刷新')
    setRefreshing(false)
  }

  const submit = (action: () => void) => {
    const clean = nickname.trim()
    if (!clean || !connected) return
    localStorage.setItem('lan-board-game:nickname', clean)
    action()
  }

  const canPlay = connected
  const deploymentBusy = deploying || undeploying || refreshing

  if (initialRoomId) {
    return (
      <section className="join-screen screen-pad">
        <header className="brand-header"><Crown /><h1>加入房间</h1></header>
        <div className="join-panel">
          <span className="room-code-label">房间号</span>
          <strong className="room-code">{initialRoomId}</strong>
          <label className="field-label" htmlFor="nickname">你的昵称</label>
          <input id="nickname" className="text-input" value={nickname} maxLength={24} autoComplete="nickname" onChange={e => setNickname(e.target.value)} placeholder="输入昵称" />
          <button className="primary-button" disabled={!nickname.trim() || !connected} onClick={() => submit(() => onJoin(initialRoomId, nickname.trim()))}>进入房间</button>
          <p className="helper-text">断线后会自动恢复座位；如果曾退出，请使用原昵称重新加入。</p>
        </div>
      </section>
    )
  }

  return (
    <section className="home-screen screen-pad">
      <header className="home-header">
        <div><span>LAN</span><h1>BOARD GAME</h1></div>
        <button className="icon-button" aria-label="网络设置" onClick={() => document.getElementById('host-deployment')?.scrollIntoView({ behavior: 'smooth' })}><Settings size={22} /></button>
      </header>
      <div className="server-line"><i className={connected ? 'ready' : ''} />{!connected ? '正在连接本地管理服务' : hostConsole && !hostStatus?.deployed ? '本地服务器已就绪，可先创建仅本机房间' : '局域网服务器已上线'}</div>

      {hostConsole ? <section id="host-deployment" className={`deployment-panel ${hostStatus?.deployed ? 'online' : ''}`}>
        <header>
          <div><Router size={20} /><span><strong>局域网部署</strong><small>选择路由器 Wi-Fi 或手机热点对应的地址</small></span></div>
          <div className="deployment-header-actions">
            <button className="undeploy-button" aria-label="关闭局域网部署" title="关闭局域网部署" disabled={!hostStatus?.deployed || deploymentBusy} onClick={() => void undeploy()}><Power size={15} /><span>{undeploying ? '关闭中' : '关闭'}</span></button>
            <button aria-label="刷新网络地址" title="刷新网络地址" disabled={deploymentBusy} onClick={() => void refreshHostStatus()}><RefreshCw className={refreshing ? 'spinning' : undefined} size={17} /></button>
          </div>
        </header>
        <div className="deployment-fields">
          <label>局域网 IP
            <select value={selectedAddress} disabled={deploymentBusy || !hostStatus?.networkOptions.length} onChange={event => setSelectedAddress(event.target.value)}>
              {hostStatus?.networkOptions.map(option => <option value={option.address} key={`${option.interfaceName}-${option.address}`}>{option.address} · {option.label}{option.recommended ? '（推荐）' : ''}</option>)}
            </select>
          </label>
          <label>端口号
            <input inputMode="numeric" value={port} disabled={deploymentBusy} onChange={event => setPort(event.target.value.replace(/\D/g, '').slice(0, 5))} />
          </label>
        </div>
        {!hostStatus?.networkOptions.length ? <p className="deployment-warning">未检测到局域网 IP。房主仍可在本机创建房间；连接 Wi-Fi 或开启热点后，此处会自动刷新并生成分享地址。</p> : null}
        {hostStatus && !hostStatus.deployed && hostStatus.networkOptions.length ? <p className="local-mode-note">当前处于仅本机模式；确认地址并部署后即可让其他玩家加入。</p> : null}
        {port && !isValidPort(port) ? <p className="deployment-warning">端口号必须在 1024–65535 之间。</p> : null}
        {deployError ? <p className="deployment-warning">{deployError}</p> : null}
        {deployMessage ? <p className="deployment-success">{deployMessage}</p> : null}
        {hostStatus?.deployed ? <div className="deployment-address"><span>当前访问地址</span><strong>http://{hostStatus.selectedAddress}:{hostStatus.port}</strong></div> : null}
        <button className="deploy-button" disabled={!connected || deploymentBusy || !selectedAddress || !isValidPort(port)} onClick={() => void deploy()}><CloudUpload size={18} />{deploying ? '正在部署…' : hostStatus?.deployed ? '重新部署上线' : '部署上线'}</button>
        <p className="router-tip">连接路由器时，所有手机必须在同一普通 Wi-Fi；请勿使用访客网络，并关闭“AP/客户端隔离”。</p>
      </section> : null}

      <label className="field-label" htmlFor="home-nickname">本人昵称</label>
      <input id="home-nickname" className="text-input" value={nickname} maxLength={24} onChange={e => setNickname(e.target.value)} placeholder="输入你的昵称" />

      <label className="field-label room-id-label" htmlFor="home-room-id">房间号</label>
      <div className="room-join-row"><div className="room-input-field"><Users size={18} /><input id="home-room-id" inputMode="numeric" value={roomId} onChange={e => setRoomId(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="输入 6 位房间号（可选）" /></div><button type="button" className="join-room-button" disabled={roomId.length !== 6 || !nickname.trim() || !canPlay} onClick={() => submit(() => onJoin(roomId, nickname.trim()))}>加入</button></div>

      <div className="home-game-list" aria-label="选择游戏">
        <div className="game-entry avalon-entry">
          <div className="entry-copy"><h2>阿瓦隆</h2><p>5–10 人 · 身份推理与团队任务</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('AVALON')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('AVALON', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry guandan-entry">
          <div className="entry-copy"><h2>掼蛋</h2><p>4 人 · 两副牌搭档出牌</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('GUANDAN')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('GUANDAN', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry timebomb-entry">
          <div className="entry-copy"><h2>TimeBomb</h2><p>4–8 人 · 隐藏阵营与剪线拆弹</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('TIME_BOMB')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('TIME_BOMB', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry crossfire-entry">
          <div className="entry-copy"><h2>Crossfire</h2><p>5–10 人 · 有限情报与秘密射击</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('CROSSFIRE')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('CROSSFIRE', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry goodcop-entry">
          <div className="entry-copy"><h2>Good Cop Bad Cop</h2><p>4–8 人 · 调查、装备与阵营对抗</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('GOOD_COP_BAD_COP')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('GOOD_COP_BAD_COP', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry liarsdice-entry">
          <div className="entry-copy"><h2>骰子模拟器</h2><p>2–10 人 · 私密骰子重掷与结算</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('LIARS_DICE')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('LIARS_DICE', nickname.trim()))}>创建房间</button>
        </div>
        <div className="game-entry poker-entry">
          <div className="entry-copy"><h2>德州扑克</h2><p>2–8 人 · 完整下注与边池结算</p><button type="button" className="card-rules-button" onClick={() => setRulesGame('POKER')}>规则介绍</button></div>
          <button type="button" className="game-create-button" disabled={!nickname.trim() || !canPlay} onClick={() => submit(() => onCreate('POKER', nickname.trim()))}>创建房间</button>
        </div>
      </div>
      {rulesGame ? <GameRulesModal game={rulesGame} onClose={() => setRulesGame(null)} /> : null}
    </section>
  )
}
