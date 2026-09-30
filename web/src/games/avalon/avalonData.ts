import type { AvalonRole } from '../../types'

export const avalonRoleNames: Record<AvalonRole, string> = {
  MERLIN: '梅林',
  PERCIVAL: '派西维尔',
  LOYAL_SERVANT: '忠诚骑士',
  ASSASSIN: '刺客',
  MORGANA: '莫甘娜',
  MORDRED: '莫德雷德',
  OBERON: '奥伯伦',
  MINION_OF_MORDRED: '莫德雷德爪牙',
}

export const avalonRoleDescriptions: Partial<Record<AvalonRole, string>> = {
  MERLIN: '知道除莫德雷德外的邪恶玩家',
  PERCIVAL: '看到梅林与莫甘娜，但无法区分',
  ASSASSIN: '好人完成三次任务后刺杀梅林',
  MORGANA: '在派西维尔眼中伪装成梅林',
  MORDRED: '不会被梅林看到',
  OBERON: '不认识邪恶同伴，同伴也看不到他',
}

export const optionalGoodRoles: AvalonRole[] = ['PERCIVAL']
export const optionalEvilRoles: AvalonRole[] = ['MORGANA', 'MORDRED', 'OBERON']
export const evilAvalonRoles = new Set<AvalonRole>(['ASSASSIN', 'MORGANA', 'MORDRED', 'OBERON', 'MINION_OF_MORDRED'])

export function evilCountFor(playerCount: number) {
  if (playerCount <= 6) return 2
  if (playerCount <= 9) return 3
  return 4
}
