import { AlertCircle, Wifi, WifiOff, X } from 'lucide-react'
import { AvalonGame } from './games/avalon/AvalonGame'
import { GuandanGame } from './games/guandan/GuandanGame'
import { TimeBombGame } from './games/timebomb/TimeBombGame'
import { CrossfireGame } from './games/crossfire/CrossfireGame'
import { GoodCopBadCopGame } from './games/goodcopbadcop/GoodCopBadCopGame'
import { LiarDiceGame } from './games/liarsdice/LiarDiceGame'
import { PokerGame } from './games/poker/PokerGame'
import { Home } from './pages/Home'
import { Lobby } from './pages/Lobby'
import { useGameSocket } from './useGameSocket'
import { usePreferredOrientation } from './usePreferredOrientation'
import type { AvalonView, CrossfireView, GoodCopView, GuandanView, LiarDiceView, PokerView, TimeBombView } from './types'

export function App() {
  const socket = useGameSocket()
  const isPlaying = Boolean(socket.room && socket.room.status !== 'WAITING' && socket.game)
  // Every actual game table is designed around a landscape canvas. Keep the
  // lobby/home screens portrait-friendly, then lock the native host once the
  // server has supplied a playable game state.
  const preferredOrientation = isPlaying ? 'landscape' : 'portrait'
  usePreferredOrientation(preferredOrientation)
  const pathRoom = location.pathname.match(/^\/room\/(\d{6})/)?.[1]
  const content = !socket.room
    ? <Home connected={socket.connected} initialRoomId={pathRoom} onCreate={socket.createRoom} onJoin={socket.joinRoom} />
    : socket.room.status === 'WAITING' || !socket.game
      ? <Lobby room={socket.room} session={socket.session} avalonConfig={socket.avalonConfig} liarDiceConfig={socket.liarDiceConfig} send={socket.send} onLeave={socket.leaveRoom} />
      : socket.room.gameType === 'AVALON'
      ? <AvalonGame room={socket.room} session={socket.session} view={socket.game as AvalonView} send={socket.send} onLeave={socket.leaveRoom} />
      : socket.room.gameType === 'GUANDAN'
          ? <GuandanGame room={socket.room} session={socket.session} view={socket.game as GuandanView} send={socket.send} onLeave={socket.leaveRoom} />
          : socket.room.gameType === 'TIME_BOMB'
            ? <TimeBombGame room={socket.room} session={socket.session} view={socket.game as TimeBombView} send={socket.send} onLeave={socket.leaveRoom} />
            : socket.room.gameType === 'CROSSFIRE'
              ? <CrossfireGame room={socket.room} session={socket.session} view={socket.game as CrossfireView} send={socket.send} onLeave={socket.leaveRoom} />
              : socket.room.gameType === 'GOOD_COP_BAD_COP'
                ? <GoodCopBadCopGame room={socket.room} session={socket.session} view={socket.game as GoodCopView} send={socket.send} onLeave={socket.leaveRoom} />
                : socket.room.gameType === 'LIARS_DICE'
                  ? <LiarDiceGame room={socket.room} session={socket.session} view={socket.game as LiarDiceView} send={socket.send} onLeave={socket.leaveRoom} />
                  : <PokerGame room={socket.room} session={socket.session} view={socket.game as PokerView} send={socket.send} onLeave={socket.leaveRoom} />

  return (
    <main className="app-shell">
      <div className={`connection-orb ${socket.connected ? 'online' : ''}`} aria-label={socket.connected ? '已连接' : '正在重连'}>
        {socket.connected ? <Wifi size={15} /> : <WifiOff size={15} />}
      </div>
      {content}
      {socket.error ? (
        <div className="error-toast" role="alert">
          <AlertCircle size={18} /><span>{socket.error}</span>
          <button onClick={() => socket.setError(null)} aria-label="关闭"><X size={18} /></button>
        </div>
      ) : null}
    </main>
  )
}
