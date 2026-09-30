import { Check, LockKeyhole, Settings2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { AvalonConfig, AvalonRole } from '../../types'
import { avalonRoleDescriptions, avalonRoleNames, evilCountFor, optionalEvilRoles } from './avalonData'

interface Props {
  config: AvalonConfig | null
  currentPlayers: number
  isHost: boolean
  send: (type: string, payload?: unknown) => void
}

export function AvalonSetupPanel({ config, currentPlayers, isHost, send }: Props) {
  const [playerCount, setPlayerCount] = useState(config?.playerCount ?? 5)
  const [specialRoles, setSpecialRoles] = useState<AvalonRole[]>(() => selectedSpecialRoles(config))
  const configKey = config ? `${config.playerCount}:${config.roles.join(',')}` : ''

  useEffect(() => {
    if (!config) return
    setPlayerCount(config.playerCount)
    setSpecialRoles(selectedSpecialRoles(config))
  }, [configKey])

  const roles = useMemo(() => buildRoles(playerCount, specialRoles), [playerCount, specialRoles])
  const evilSlots = evilCountFor(playerCount) - 1
  const selectedEvilCount = specialRoles.filter(role => optionalEvilRoles.includes(role)).length
  const goodCount = playerCount - evilCountFor(playerCount)

  const changePlayerCount = (next: number) => {
    setPlayerCount(next)
    const nextEvilSlots = evilCountFor(next) - 1
    setSpecialRoles(current => {
      const good = current.filter(role => !optionalEvilRoles.includes(role))
      const evil = current.filter(role => optionalEvilRoles.includes(role)).slice(0, nextEvilSlots)
      return [...good, ...evil]
    })
  }

  const toggleRole = (role: AvalonRole) => {
    setSpecialRoles(current => {
      if (current.includes(role)) return current.filter(item => item !== role)
      if (optionalEvilRoles.includes(role) && selectedEvilCount >= evilSlots) return current
      return [...current, role]
    })
  }

  if (!isHost) {
    const shown = config?.roles ?? roles
    return (
      <section className="avalon-setup readonly">
        <div className="setup-heading"><Settings2 size={18} /><div><strong>本局配置</strong><span>{config?.playerCount ?? playerCount} 人 · 好人 {goodCount} / 邪恶 {evilCountFor(playerCount)}</span></div></div>
        <div className="role-summary">{summarizeRoles(shown).map(item => <span key={item.role}>{avalonRoleNames[item.role]}{item.count > 1 ? ` ×${item.count}` : ''}</span>)}</div>
      </section>
    )
  }

  return (
    <section className="avalon-setup">
      <div className="setup-heading"><Settings2 size={18} /><div><strong>房主设置</strong><span>选择人数与本局角色</span></div></div>
      <label className="setup-count">玩家人数
        <select value={playerCount} onChange={event => changePlayerCount(Number(event.target.value))}>
          {[5, 6, 7, 8, 9, 10].map(count => <option disabled={count < currentPlayers} value={count} key={count}>{count} 人</option>)}
        </select>
      </label>
      <div className="camp-counts"><span>好人 {goodCount}</span><span>邪恶 {evilCountFor(playerCount)}</span><small>当前已加入 {currentPlayers} 人</small></div>
      <div className="role-picker">
        <RoleChip role="MERLIN" selected locked onClick={() => undefined} />
        <RoleChip role="PERCIVAL" selected={specialRoles.includes('PERCIVAL')} onClick={() => toggleRole('PERCIVAL')} />
        <RoleChip role="ASSASSIN" selected locked evil onClick={() => undefined} />
        {optionalEvilRoles.map(role => <RoleChip
          key={role}
          role={role}
          evil
          selected={specialRoles.includes(role)}
          disabled={!specialRoles.includes(role) && selectedEvilCount >= evilSlots}
          onClick={() => toggleRole(role)}
        />)}
      </div>
      <p className="auto-role-copy">空余位置自动补充忠诚骑士和莫德雷德爪牙。</p>
      <button className="secondary-button save-setup" disabled={playerCount < currentPlayers} onClick={() => send('avalon:configure', { playerCount, roles })}><Check size={17} />保存人数与角色</button>
    </section>
  )
}

function RoleChip({ role, selected, locked = false, evil = false, disabled = false, onClick }: {
  role: AvalonRole
  selected: boolean
  locked?: boolean
  evil?: boolean
  disabled?: boolean
  onClick: () => void
}) {
  return <button type="button" className={`role-chip ${selected ? 'selected' : ''} ${evil ? 'evil' : ''}`} disabled={disabled || locked} onClick={onClick}>
    <span>{selected ? <Check size={14} /> : null}{avalonRoleNames[role]}{locked ? <LockKeyhole size={12} /> : null}</span>
    <small>{avalonRoleDescriptions[role]}</small>
  </button>
}

function selectedSpecialRoles(config: AvalonConfig | null): AvalonRole[] {
  return config?.roles.filter(role => role === 'PERCIVAL' || optionalEvilRoles.includes(role)) ?? ['PERCIVAL', 'MORGANA']
}

function buildRoles(playerCount: number, selected: AvalonRole[]): AvalonRole[] {
  const evilCount = evilCountFor(playerCount)
  const selectedEvil = optionalEvilRoles.filter(role => selected.includes(role)).slice(0, evilCount - 1)
  const good: AvalonRole[] = ['MERLIN']
  if (selected.includes('PERCIVAL')) good.push('PERCIVAL')
  while (good.length < playerCount - evilCount) good.push('LOYAL_SERVANT')
  const evil: AvalonRole[] = ['ASSASSIN', ...selectedEvil]
  while (evil.length < evilCount) evil.push('MINION_OF_MORDRED')
  return [...good, ...evil]
}

function summarizeRoles(roles: AvalonRole[]) {
  const counts = new Map<AvalonRole, number>()
  roles.forEach(role => counts.set(role, (counts.get(role) ?? 0) + 1))
  return Array.from(counts, ([role, count]) => ({ role, count }))
}
