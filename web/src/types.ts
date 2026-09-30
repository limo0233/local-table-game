export type GameType = 'AVALON' | 'GUANDAN' | 'TIME_BOMB' | 'CROSSFIRE' | 'GOOD_COP_BAD_COP' | 'LIARS_DICE' | 'POKER'
export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED'

export interface Player {
  id: string
  nickname: string
  connected: boolean
  isHost: boolean
  ready: boolean
  isBot?: boolean
}

export interface RoomView {
  roomId: string
  gameType: GameType
  status: RoomStatus
  hostId: string
  players: Player[]
}

export interface Session {
  roomId: string
  playerId: string
  sessionId: string
  nickname: string
  resumed?: boolean
}

export type AvalonPhase = 'TEAM_SELECTION' | 'TEAM_VOTE' | 'MISSION' | 'ASSASSINATION' | 'FINISHED'
export type AvalonRole = 'MERLIN' | 'PERCIVAL' | 'LOYAL_SERVANT' | 'ASSASSIN' | 'MORGANA' | 'MORDRED' | 'OBERON' | 'MINION_OF_MORDRED'
export interface AvalonConfig { playerCount: number; roles: AvalonRole[] }
export interface AvalonVoteRecord {
  round: number
  proposal: number
  leaderId: string
  team: string[]
  votes: Record<string, boolean>
  approved: boolean
}
export interface AvalonView {
  public: {
    phase: AvalonPhase
    round: number
    leaderId: string
    teamSize: number
    selectedTeam: string[]
    proposalRejects: number
    currentProposal: number
    totalVoteCount: number
    voteHistory: AvalonVoteRecord[]
    missions: { round: number; team: string[]; approved: boolean; success?: boolean; failCount?: number }[]
    submittedPlayerIds: string[]
    winner?: 'GOOD' | 'EVIL'
    endReason?: string
  }
  myRole: AvalonRole
  knowledge: { playerId: string; hint: string }[]
  canSelectTeam: boolean
  canVote: boolean
  canSubmitMission: boolean
  canAssassinate: boolean
}

export interface Card { rank: string; suit: 'CLUBS' | 'DIAMONDS' | 'HEARTS' | 'SPADES' }
export interface PokerView {
  public: {
    phase: string
    handNumber: number
    seats: {
      id: string; chips: number; folded: boolean; allIn: boolean; streetBet: number
      isDealer: boolean; isSmallBlind: boolean; isBigBlind: boolean; revealedCards?: Card[]
    }[]
    communityCards: Card[]
    potTotal: number
    currentBet: number
    minRaiseTo: number
    actingPlayerId?: string
    lastAwards: { amount: number; winnerIds: string[] }[]
  }
  myCards: Card[]
  toCall: number
  availableActions: ('FOLD' | 'CHECK' | 'CALL' | 'RAISE_TO' | 'ALL_IN')[]
}

export type TimeBombRole = 'SHERLOCK' | 'MORIARTY'
export type TimeBombWire = 'SAFE' | 'DEFUSE' | 'BOMB'
export type TimeBombPhase = 'ROLE_REVEAL' | 'ROUND' | 'CUT_RESULT' | 'ROUND_RESET' | 'GAME_OVER'
export interface TimeBombView {
  public: {
    phase: TimeBombPhase
    round: number
    currentCutterId: string
    wireCountsByPlayer: Record<string, number>
    cutsThisRound: number
    defuseFound: number
    totalDefuse: number
    history: { round: number; cutterId: string; targetId: string; wire: TimeBombWire; remainingTargetWires: number }[]
    lastCut?: { round: number; cutterId: string; targetId: string; wire: TimeBombWire; remainingTargetWires: number }
    winner?: TimeBombRole
    endReason?: string
    confirmedPlayerIds: string[]
  }
  myRole: TimeBombRole
  privateWires: TimeBombWire[]
  canConfirmRole: boolean
  canCut: boolean
  targetIds: string[]
}

export type CrossfireRole = 'VIP' | 'AGENT' | 'ASSASSIN' | 'BYSTANDER' | 'DECOY' | 'RED_DECOY' | 'BLUE_DECOY'
export type CrossfireTeam = 'AGENTS' | 'ASSASSIN' | 'BYSTANDER'
export type CrossfirePhase = 'INITIAL_VIEW' | 'INTEL_PASS' | 'SHUFFLE' | 'DISCUSSION' | 'TARGETING' | 'RESOLVE' | 'GAME_OVER'
export interface CrossfireView {
  public: {
    phase: CrossfirePhase
    round: number
    dealerPlayerId?: string
    seatOrder: string[]
    currentShuffleGroup: string[]
    shuffleGroupIndex: number
    shuffleGroupCount: number
    currentPlayerId?: string
    discussionDeadline?: number
    confirmedPlayerIds: string[]
    lockedPlayers: string[]
    targetsSubmitted: number
    history: string[]
    winnerTeam?: CrossfireTeam
    revealedRoles: Record<string, CrossfireRole>
    resultByPlayer: Record<string, { role: CrossfireRole; team: CrossfireTeam; won: boolean; reason: string }>
  }
  initialRole: CrossfireRole
  seenRoles: { sourcePlayerId: string; role: CrossfireRole }[]
  shuffleHistory: { playerIds: string[] }[]
  currentRole?: CrossfireRole
  finalRole?: CrossfireRole
  canConfirmInitial: boolean
  canConfirmIntel: boolean
  canConfirmShuffle: boolean
  canStartTargeting: boolean
  canSubmitTarget: boolean
  targetIds: string[]
}

export type IntegrityType = 'HONEST' | 'CROOKED' | 'AGENT' | 'KINGPIN'
export type GoodCopTeam = 'HONEST' | 'CROOKED'
export type EquipmentType = 'SHIELD'
export type GoodCopPhase = 'TURN' | 'ACTION_RESOLVE' | 'GAME_OVER'
export interface GoodCopView {
  public: {
    phase: GoodCopPhase
    currentPlayerId: string
    hiddenIntegrityCounts: Record<string, number>
    revealedIntegrity: Record<string, IntegrityType[]>
    equipmentCounts: Record<string, number>
    gunHolder?: string
    aimTarget?: string
    wounded: string[]
    eliminated: string[]
    history: { actorId: string; action: string; targetId?: string; detail?: string }[]
    winnerTeam?: GoodCopTeam
    revealedTeams: Record<string, GoodCopTeam>
  }
  myIntegrity: { id: string; type: IntegrityType; revealed: boolean }[]
  myTeam: GoodCopTeam
  myEquipment: EquipmentType[]
  investigationResult?: { id: string; type: IntegrityType; revealed: boolean }
  canInvestigate: boolean
  canEquip: boolean
  canArm: boolean
  canAim: boolean
  canShoot: boolean
  targetIds: string[]
}

export type LiarDicePhase = 'PLAYING'
export interface LiarDiceConfig { diceCount: number }
export interface LiarDiceView {
  public: {
    phase: LiarDicePhase
    round: number
    diceCount: Record<string, number>
    settledPlayerIds: string[]
    allSettled: boolean
    nextRoundPlayerIds: string[]
    resultsByPlayer: Record<string, number[]>
    pointCounts: Record<string, number>
  }
  myDice: number[]
  canReroll: boolean
  canSettle: boolean
  canNextRound: boolean
  settled: boolean
}

export type GuandanCardRank = '3' | '4' | '5' | '6' | '7' | '8' | '9' | '10' | 'J' | 'Q' | 'K' | 'A' | '2' | 'SMALL_JOKER' | 'BIG_JOKER'
export interface GuandanCard { id: string; rank: GuandanCardRank; suit: 'CLUBS' | 'DIAMONDS' | 'HEARTS' | 'SPADES' | 'JOKER' }
export type GuandanPhase = 'PLAYING' | 'TRIBUTE' | 'FINISHED'
export type GuandanCombinationType = 'SINGLE' | 'PAIR' | 'TRIPLE' | 'TRIPLE_WITH_PAIR' | 'STRAIGHT' | 'CONSECUTIVE_PAIRS' | 'CONSECUTIVE_TRIPLES' | 'STRAIGHT_FLUSH' | 'BOMB' | 'ROCKET'
export interface GuandanPlay { playerId: string; cards: GuandanCard[]; type: GuandanCombinationType; strength: number }
export interface GuandanRanking { playerId: string; position: number }
export interface GuandanTributeRequirement { fromPlayerId: string; toPlayerId: string }
export interface GuandanTributeState {
  requirements: GuandanTributeRequirement[]
  submittedPlayerIds: string[]
  returnedPlayerIds: string[]
  antiTribute: boolean
}
export interface GuandanView {
  public: {
    phase: GuandanPhase
    currentPlayerId: string
    lastPlay?: GuandanPlay
    passedPlayerIds: string[]
    handSizes: Record<string, number>
    teamScores: number[]
    winnerTeam?: number
    turnNumber: number
    roundNumber: number
    currentLevel: GuandanCardRank
    ranking: GuandanRanking[]
    lastRoundRanking: GuandanRanking[]
    tribute?: GuandanTributeState
    matchWinnerTeam?: number
    lastEvent?: string
  }
  myHand: GuandanCard[]
  canPlay: boolean
  canPass: boolean
  canContinue: boolean
  canTribute: boolean
  canReturnTribute: boolean
  tributeTargetPlayerId?: string
}

export type GameView = AvalonView | PokerView | GuandanView | TimeBombView | CrossfireView | GoodCopView | LiarDiceView
