import type { CrossfireRole, GoodCopTeam, IntegrityType, TimeBombRole } from '../types'

/** Stable protocol IDs stay in English; these maps are the user-facing copy. */
export const timeBombRoleNames: Record<TimeBombRole, string> = {
  SHERLOCK: '侦探',
  MORIARTY: '破坏者',
}

export const crossfireRoleNames: Record<CrossfireRole, string> = {
  VIP: 'VIP',
  AGENT: '特工',
  ASSASSIN: '刺客',
  BYSTANDER: '路人',
  DECOY: '诱饵',
  RED_DECOY: '红方诱饵',
  BLUE_DECOY: '蓝方诱饵',
}

export const integrityTypeNames: Record<IntegrityType, string> = {
  HONEST: '好警',
  CROOKED: '黑警',
  AGENT: '探员',
  KINGPIN: '黑警头目',
}

export const goodCopTeamNames: Record<GoodCopTeam, string> = {
  HONEST: '好警阵营',
  CROOKED: '黑警阵营',
}
