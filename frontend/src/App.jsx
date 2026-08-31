import React, { useState, useEffect, useRef } from 'react'
import ControlPanel from './components/ControlPanel'
import LiveMonitor from './components/LiveMonitor'
import ResultDashboard from './components/ResultDashboard'

const API_BASE = 'http://localhost:8080/api'

export default function App() {
  const [config, setConfig] = useState({
    eventName: 'Coldplay War Ticket Simulation 2026',
    totalTickets: 100,
    semaphorePermits: 5,
    requestCount: 300,
    useDelay: true
  })

  const [currentEventId, setCurrentEventId] = useState(null)
  const [isInitializing, setIsInitializing] = useState(false)
  const [isRunning, setIsRunning] = useState(false)
  const [isConnected, setIsConnected] = useState(false)
  const [logs, setLogs] = useState([])
  const [memoryHistory, setMemoryHistory] = useState([])

  const [status, setStatus] = useState({
    availableTickets: 100,
    totalTickets: 100,
    activePermits: 0,
    totalPermits: 5,
    queueLength: 0,
    totalRequests: 0,
    successRequests: 0,
    failedOutOfStock: 0,
    failedTimeout: 0,
    memoryUsage: 0,
    message: 'Aplikasi siap'
  })

  const eventSourceRef = useRef(null)

  // Append new log item (terbaru di bawah, max 50 items)
  const addLog = (text) => {
    const time = new Date().toLocaleTimeString('id-ID', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
    setLogs((prev) => [...prev.slice(-49), { time, text }])
  }

  // Setup Server-Sent Events (SSE)
  useEffect(() => {
    function connectSse() {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }

      const sse = new EventSource(`${API_BASE}/simulation/stream`)
      eventSourceRef.current = sse

      sse.onopen = () => {
        setIsConnected(true)
        addLog('Terhubung ke Server-Sent Events (SSE) Stream.')
      }

      sse.addEventListener('INIT', (event) => {
        setIsConnected(true)
        addLog(`SSE Init: ${event.data}`)
      })

      sse.addEventListener('STATUS_UPDATE', (event) => {
        try {
          const data = JSON.parse(event.data)
          setStatus(data)
          if (data.memoryUsage !== undefined) {
            const now = new Date(data.timestamp || Date.now())
            const time = now.toLocaleTimeString('id-ID', {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit'
            })
            const fullTime = now.toLocaleTimeString('id-ID', {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
              fractionalSecondDigits: 3
            })
            setMemoryHistory((prev) => {
              const nextId = prev.length > 0 ? (prev[prev.length - 1].id || 0) + 1 : 1
              return [
                ...prev.slice(-29),
                { id: nextId, time, fullTime, memoryUsage: data.memoryUsage }
              ]
            })
          }
          if (data.message) {
            addLog(data.message)
          }
        } catch (e) {
          console.error('Failed to parse SSE payload', e)
        }
      })

      sse.onerror = () => {
        setIsConnected(false)
        sse.close()
        // Coba reconnect setelah 3 detik
        setTimeout(connectSse, 3000)
      }
    }

    connectSse()

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
    }
  }, [])

  // Inisialisasi / Reset Simulasi
  const handleInit = async () => {
    try {
      setIsInitializing(true)
      const res = await fetch(`${API_BASE}/simulation/init`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventName: config.eventName,
          totalTickets: config.totalTickets,
          semaphorePermits: config.semaphorePermits,
          useDelay: config.useDelay
        })
      })

      if (!res.ok) {
        throw new Error(`HTTP error! status: ${res.status}`)
      }

      const data = await res.json()
      setCurrentEventId(data.id)
      addLog(`Event "${data.name}" berhasil dibuat (ID: ${data.id}, Stok: ${data.totalTickets}).`)

      // Fetch snapshot status
      const statusRes = await fetch(`${API_BASE}/simulation/status`)
      if (statusRes.ok) {
        const s = await statusRes.json()
        setStatus(s)
        if (s.memoryUsage !== undefined) {
          const now = new Date(s.timestamp || Date.now())
          const time = now.toLocaleTimeString('id-ID', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
          })
          const fullTime = now.toLocaleTimeString('id-ID', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            fractionalSecondDigits: 3
          })
          setMemoryHistory([{ id: 1, time, fullTime, memoryUsage: s.memoryUsage }])
        }
      }
    } catch (err) {
      addLog(`Gagal inisialisasi: ${err.message}`)
    } finally {
      setIsInitializing(false)
    }
  }

  // Mulai Traffic Simulasi (Concurrent requests)
  const handleStartTraffic = async () => {
    if (!currentEventId) return

    setIsRunning(true)
    addLog(`Memulai simulasi ${config.requestCount} concurrent requests (Delay: ${config.useDelay ? '1s' : '0s'})...`)

    try {
      // Trigger via backend batch traffic endpoint
      const res = await fetch(`${API_BASE}/simulation/traffic?requestCount=${config.requestCount}&useDelay=${config.useDelay ?? true}`, {
        method: 'POST'
      })

      if (!res.ok) {
        throw new Error('Gagal memicu batch traffic.')
      }

      addLog(`Semua request (${config.requestCount}) telah ditembakkan ke backend!`)
    } catch (err) {
      addLog(`Error saat traffic: ${err.message}`)
    } finally {
      setTimeout(() => {
        setIsRunning(false)
        addLog('Batch request selesai diproses.')
      }, 2000)
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-4 md:p-8 flex flex-col items-center">
      {/* Container */}
      <div className="w-full max-w-6xl space-y-6">
        {/* Header Title */}
        <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800">
          <div>
            <h1 className="text-2xl md:text-3xl font-black bg-gradient-to-r from-indigo-400 via-purple-300 to-pink-400 bg-clip-text text-transparent">
              Spring Boot Semaphore Simulation
            </h1>
            <p className="text-xs md:text-sm text-slate-400 mt-0.5">
              Simulasi Concurrency "War Ticket" dengan Algoritma Semaphore & Real-Time SSE
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-300">
              Spring Boot 3.4 + React + Tailwind
            </span>
          </div>
        </header>

        {/* 1. Control Panel (Issue #12) */}
        <ControlPanel
          config={config}
          setConfig={setConfig}
          onInit={handleInit}
          onStartTraffic={handleStartTraffic}
          isInitializing={isInitializing}
          isRunning={isRunning}
          currentEventId={currentEventId}
        />

        {/* 2. Live Backend Monitor (Issue #13 & #19) */}
        <LiveMonitor
          status={status}
          isConnected={isConnected}
          logs={logs}
          memoryHistory={memoryHistory}
        />

        {/* 3. Result Dashboard (Issue #14) */}
        <ResultDashboard
          status={status}
        />

        {/* Footer */}
        <footer className="text-center text-xs text-slate-600 pt-4">
          Spring Boot Semaphore Concurrency Simulation &copy; 2026
        </footer>
      </div>
    </div>
  )
}
