import type { Card } from '../../types'

const suits = { CLUBS: '♣', DIAMONDS: '♦', HEARTS: '♥', SPADES: '♠' } as const
const ranks: Record<string, string> = { TEN: '10', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A', TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7', EIGHT: '8', NINE: '9' }

export function PlayingCard({ card, hidden = false, small = false }: { card?: Card; hidden?: boolean; small?: boolean }) {
  if (hidden || !card) return <div className={`playing-card card-back ${small ? 'small' : ''}`} aria-label="暗牌"><i /></div>
  const red = card.suit === 'HEARTS' || card.suit === 'DIAMONDS'
  return <div className={`playing-card ${red ? 'red' : ''} ${small ? 'small' : ''}`} aria-label={`${ranks[card.rank]}${suits[card.suit]}`}>
    <strong>{ranks[card.rank]}</strong><span>{suits[card.suit]}</span>
  </div>
}

