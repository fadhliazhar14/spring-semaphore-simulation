import React from 'react'

export default function LiveMonitor({ status, isConnected, logs }) {
  const totalPermits = status.totalPermits || 5
  const activePermits = status.activePermits || 0
  const queueLength = status.queueLength || 0

  // Buat array representasi slot Semaphore
  const permitSlots = Array.from({ length: totalPermits }, (_, idx) => {
    const isActive = idx < activePermits
    return {
      id: idx + 1,
      isActive
    }
  })

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-sm">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <span className="p-2 bg-cyan-500/10 text-cyan-400 rounded-lg">📡</span>
            Live Backend Monitor (SSE)
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Visualisasi state slot Semaphore dan antrean thread secara real-time.
          </p>
        </div>

        {/* Connection Status Badge */}
        <div className="flex items-center gap-2">
          {isConnected ? (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <span className="h-2 w-2 rounded-full bg-emerald-400 animate-ping"></span>
              Live SSE Connected
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
              <span className="h-2 w-2 rounded-full bg-rose-400"></span>
              Disconnected
            </span>
          )}
        </div>
      </div>

      {/* Grid Status: Semaphore Slots & Queue Monitor */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Semaphore Permits Slots */}
        <div className="lg:col-span-2 bg-slate-950/60 border border-slate-800/80 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">
              Slot Semaphore Permits
            </h3>
            <span className="text-xs font-mono text-cyan-400 font-bold bg-cyan-500/10 px-2.5 py-1 rounded-md border border-cyan-500/20">
              {activePermits} / {totalPermits} Permit Terpakai
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-5 md:grid-cols-6 lg:grid-cols-5 gap-3">
            {permitSlots.map((slot) => (
              <div
                key={slot.id}
                className={`relative flex flex-col items-center justify-center p-4 rounded-xl border transition-all duration-300 ${
                  slot.isActive
                    ? 'bg-gradient-to-b from-cyan-500/20 to-blue-600/20 border-cyan-400 shadow-lg shadow-cyan-500/20 scale-105'
                    : 'bg-slate-900/50 border-slate-800 text-slate-500 opacity-60'
                }`}
              >
                {slot.isActive && (
                  <span className="absolute -top-1.5 -right-1.5 flex h-3 w-3">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-3 w-3 bg-cyan-500"></span>
                  </span>
                )}
                <div className="text-2xl mb-1">{slot.isActive ? '⚡' : '🔒'}</div>
                <div
                  className={`text-xs font-bold font-mono ${
                    slot.isActive ? 'text-cyan-300' : 'text-slate-500'
                  }`}
                >
                  Slot #{slot.id}
                </div>
                <div
                  className={`text-[10px] font-medium uppercase mt-0.5 ${
                    slot.isActive ? 'text-emerald-400 font-bold' : 'text-slate-600'
                  }`}
                >
                  {slot.isActive ? 'ACTIVE' : 'IDLE'}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Queue Length Visualizer */}
        <div className="bg-slate-950/60 border border-slate-800/80 rounded-xl p-5 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-slate-300 uppercase tracking-wider">
                Baris Antrean
              </h3>
              <span className="text-xs text-slate-500">Queue Length</span>
            </div>
            <p className="text-xs text-slate-400">
              Jumlah request yang tertahan menunggu giliran permit.
            </p>
          </div>

          <div className="my-4 text-center">
            <div
              className={`text-5xl font-black font-mono transition-colors duration-300 ${
                queueLength > 0 ? 'text-amber-400 animate-pulse' : 'text-slate-600'
              }`}
            >
              {queueLength}
            </div>
            <div className="text-xs font-semibold text-slate-400 uppercase mt-1">
              Thread Mengantre
            </div>
          </div>

          <div className="w-full bg-slate-900 rounded-full h-2 overflow-hidden border border-slate-800">
            <div
              className="bg-gradient-to-r from-amber-500 to-rose-500 h-full transition-all duration-300"
              style={{
                width: `${Math.min(100, (queueLength / 50) * 100)}%`
              }}
            ></div>
          </div>
        </div>
      </div>

      {/* Live Event Stream / Log Terminal */}
      <div className="bg-slate-950/90 border border-slate-800/80 rounded-xl p-4">
        <div className="flex items-center justify-between mb-2 pb-2 border-b border-slate-800/60">
          <span className="text-xs font-mono font-bold text-slate-400 flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-indigo-500"></span>
            LIVE ACTIVITY LOG
          </span>
          <span className="text-[11px] font-mono text-slate-500">
            Pesan Terakhir: {status.message || 'Standby'}
          </span>
        </div>
        <div className="h-32 overflow-y-auto space-y-1 font-mono text-xs text-slate-300 scrollbar-thin scrollbar-thumb-slate-800">
          {logs && logs.length > 0 ? (
            logs.map((logItem, index) => (
              <div key={index} className="flex items-start gap-2 py-0.5">
                <span className="text-slate-500 select-none">[{logItem.time}]</span>
                <span
                  className={
                    logItem.text.includes('berhasil')
                      ? 'text-emerald-400'
                      : logItem.text.includes('gagal') || logItem.text.includes('timeout')
                      ? 'text-rose-400'
                      : 'text-cyan-300'
                  }
                >
                  {logItem.text}
                </span>
              </div>
            ))
          ) : (
            <div className="text-slate-600 italic py-2">
              Menunggu aktivitas simulasi...
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
