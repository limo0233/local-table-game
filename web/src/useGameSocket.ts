import { useCallback, useEffect, useRef, useState } from 'react'
import { createSessionId } from './sessionId'
import type { AvalonConfig, GameType, GameView, LiarDiceConfig, RoomView, Session } from './types'

interface Envelope { type: string; requestId?: string; payload: unknown }

const SESSION_KEY = 'lan-board-game:session:v1'
const DISCONNECT_NOTICE = '连接已断开，正在自动重连'
const DISCONNECT_GRACE_MS = 5_000

export function loadSession(): Session | null {
  try { return JSON.parse(localStorage.getItem(SESSION_KEY) ?? 'null') as Session | null } catch { return null }
}

export function useGameSocket() {
  const socketRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<number | null>(null)
  const disconnectNoticeTimer = useRef<number | null>(null)
  const attempts = useRef(0)
  const attachedRef = useRef(false)
  const pendingLeaveRef = useRef(false)
  const leaveSentRef = useRef(false)
  const [connected, setConnected] = useState(false)
  const [room, setRoom] = useState<RoomView | null>(null)
  const [game, setGame] = useState<GameView | null>(null)
  const [avalonConfig, setAvalonConfig] = useState<AvalonConfig | null>(null)
  const [liarDiceConfig, setLiarDiceConfig] = useState<LiarDiceConfig | null>(null)
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const [error, setError] = useState<string | null>(null)

  const clearDisconnectNotice = useCallback(() => {
    if (disconnectNoticeTimer.current !== null) {
      window.clearTimeout(disconnectNoticeTimer.current)
      disconnectNoticeTimer.current = null
    }
    setError(current => current === DISCONNECT_NOTICE ? null : current)
  }, [])

  const scheduleDisconnectNotice = useCallback(() => {
    if (disconnectNoticeTimer.current !== null) return
    disconnectNoticeTimer.current = window.setTimeout(() => {
      disconnectNoticeTimer.current = null
      setError(DISCONNECT_NOTICE)
    }, DISCONNECT_GRACE_MS)
  }, [])

  const clearRoomState = useCallback((message?: string) => {
    pendingLeaveRef.current = false
    leaveSentRef.current = false
    attachedRef.current = false
    clearDisconnectNotice()
    localStorage.removeItem(SESSION_KEY)
    setSession(null)
    setRoom(null)
    setGame(null)
    setAvalonConfig(null)
    setLiarDiceConfig(null)
    history.replaceState({}, '', '/')
    if (message) setError(message)
  }, [clearDisconnectNotice])

  const send = useCallback((type: string, payload: unknown = {}) => {
    const socket = socketRef.current
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      scheduleDisconnectNotice()
      return false
    }
    try {
      socket.send(JSON.stringify({ type, payload }))
      return true
    } catch (cause) {
      console.warn('WebSocket send failed', cause)
      scheduleDisconnectNotice()
      return false
    }
  }, [scheduleDisconnectNotice])

  useEffect(() => {
    let disposed = false
    const connect = () => {
      if (disposed) return
      const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
      const socket = new WebSocket(`${protocol}://${location.host}/ws`)
      let heartbeatTimer: number | null = null
      let connectionTimeoutTimer: number | null = window.setTimeout(() => {
        if (socket.readyState === WebSocket.OPEN) return
        scheduleDisconnectNotice()
        socket.close(4000, 'connection timeout')
      }, DISCONNECT_GRACE_MS)
      let lastPongAt = Date.now()
      socketRef.current = socket
      attachedRef.current = false

      socket.onopen = () => {
        if (connectionTimeoutTimer !== null) window.clearTimeout(connectionTimeoutTimer)
        connectionTimeoutTimer = null
        if (socketRef.current !== socket) return
        lastPongAt = Date.now()
        heartbeatTimer = window.setInterval(() => {
          if (socket.readyState !== WebSocket.OPEN) return
          if (Date.now() - lastPongAt > DISCONNECT_GRACE_MS) {
            if (heartbeatTimer !== null) window.clearInterval(heartbeatTimer)
            heartbeatTimer = null
            socket.close(4000, 'heartbeat timeout')
            return
          }
          try { socket.send(JSON.stringify({ type: 'system:ping', payload: {} })) } catch { socket.close(4000, 'heartbeat send failed') }
        }, 2_000)
        clearDisconnectNotice()
        setConnected(true)
        setError(null)
        attempts.current = 0
        const saved = loadSession()
        const pathRoom = location.pathname.match(/^\/room\/(\d{6})/)?.[1]
        if (saved && pathRoom === saved.roomId) {
          socket.send(JSON.stringify({ type: 'room:join', payload: saved }))
        }
      }

      socket.onmessage = (event) => {
        if (socketRef.current !== socket) return
        let message: Envelope
        try {
          message = JSON.parse(event.data) as Envelope
        } catch (cause) {
          console.warn('WebSocket message parse failed', cause)
          setError('收到无法识别的服务器消息')
          return
        }
        if (message.type === 'session:attached') {
          attachedRef.current = true
          const attached = message.payload as Omit<Session, 'nickname'>
          const nickname = loadSession()?.nickname ?? ''
          const next = { ...attached, nickname }
          localStorage.setItem(SESSION_KEY, JSON.stringify(next))
          setSession(next)
          if (location.pathname !== `/room/${next.roomId}`) history.pushState({}, '', `/room/${next.roomId}`)
          if (pendingLeaveRef.current && !leaveSentRef.current) {
            try {
              socket.send(JSON.stringify({ type: 'room:leave', payload: {} }))
              leaveSentRef.current = true
            } catch { leaveSentRef.current = false }
          }
        } else if (message.type === 'room:state') {
          const nextRoom = message.payload as RoomView
          setRoom(nextRoom)
          if (nextRoom.status === 'WAITING') setGame(null)
          setSession(current => {
            if (!current) return current
            const nickname = nextRoom.players.find(player => player.id === current.playerId)?.nickname
            if (!nickname || nickname === current.nickname) return current
            const next = { ...current, nickname }
            localStorage.setItem(SESSION_KEY, JSON.stringify(next))
            localStorage.setItem('lan-board-game:nickname', nickname)
            return next
          })
          if (nextRoom.gameType !== 'AVALON') setAvalonConfig(null)
          if (nextRoom.gameType !== 'LIARS_DICE') setLiarDiceConfig(null)
        } else if (message.type === 'avalon:config') setAvalonConfig(message.payload as AvalonConfig)
        else if (message.type === 'liarsdice:configure') setLiarDiceConfig(message.payload as LiarDiceConfig)
        else if (message.type === 'game:state') setGame(message.payload as GameView)
        else if (message.type === 'system:pong') lastPongAt = Date.now()
        else if (message.type === 'session:kicked') clearRoomState((message.payload as { message: string }).message)
        else if (message.type === 'room:left') clearRoomState()
        else if (message.type === 'room:closed') clearRoomState((message.payload as { message: string }).message)
        else if (message.type === 'game:error') setError((message.payload as { message: string }).message)
      }

      socket.onerror = (event) => {
        if (socketRef.current !== socket) return
        console.warn('WebSocket error', event)
        scheduleDisconnectNotice()
      }

      socket.onclose = (event) => {
        if (connectionTimeoutTimer !== null) window.clearTimeout(connectionTimeoutTimer)
        connectionTimeoutTimer = null
        if (heartbeatTimer !== null) window.clearInterval(heartbeatTimer)
        heartbeatTimer = null
        if (socketRef.current !== socket) return
        console.info('WebSocket closed', { code: event.code, reason: event.reason, clean: event.wasClean })
        attachedRef.current = false
        leaveSentRef.current = false
        setConnected(false)
        if (!disposed) {
          scheduleDisconnectNotice()
          const delay = Math.min(10_000, 600 * 2 ** attempts.current++)
          if (reconnectTimer.current !== null) window.clearTimeout(reconnectTimer.current)
          reconnectTimer.current = window.setTimeout(connect, delay)
        }
      }
    }

    connect()
    return () => {
      disposed = true
      if (reconnectTimer.current !== null) window.clearTimeout(reconnectTimer.current)
      if (disconnectNoticeTimer.current !== null) window.clearTimeout(disconnectNoticeTimer.current)
      reconnectTimer.current = null
      disconnectNoticeTimer.current = null
      attachedRef.current = false
      leaveSentRef.current = false
      socketRef.current?.close()
    }
  }, [clearDisconnectNotice, clearRoomState, scheduleDisconnectNotice])

  const createRoom = (gameType: GameType, nickname: string) => {
    try {
      const sessionId = createSessionId()
      localStorage.setItem(SESSION_KEY, JSON.stringify({ nickname, sessionId }))
      if (!send('room:create', { gameType, nickname, sessionId })) localStorage.removeItem(SESSION_KEY)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '创建房间失败，请重试')
    }
  }

  const joinRoom = (roomId: string, nickname: string) => {
    try {
      const saved = loadSession()
      const sessionId = saved?.roomId === roomId ? saved.sessionId : createSessionId()
      localStorage.setItem(SESSION_KEY, JSON.stringify({ roomId, nickname, sessionId }))
      send('room:join', { roomId, nickname, sessionId })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '加入房间失败，请重试')
    }
  }

  const leaveRoom = () => {
    pendingLeaveRef.current = true
    leaveSentRef.current = false
    const socket = socketRef.current
    if (!socket || socket.readyState !== WebSocket.OPEN || !attachedRef.current) return true
    try {
      socket.send(JSON.stringify({ type: 'room:leave', payload: {} }))
      leaveSentRef.current = true
    } catch {
      scheduleDisconnectNotice()
    }
    return true
  }

  return { connected, room, game, avalonConfig, liarDiceConfig, session, error, setError, send, createRoom, joinRoom, leaveRoom }
}
