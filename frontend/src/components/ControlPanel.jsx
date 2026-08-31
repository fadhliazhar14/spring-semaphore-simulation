import React from 'react'

export default function ControlPanel({
  config,
  setConfig,
  onInit,
  onStartTraffic,
  isInitializing,
  isRunning,
  currentEventId
}) {
  const handleChange = (e) => {
    const { name, value } = e.target
    setConfig((prev) => ({
      ...prev,
      [name]: name === 'eventName' ? value : Math.max(1, parseInt(value) || 1)
    }))
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-sm">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <span className="p-2 bg-indigo-500/10 text-indigo-400 rounded-lg">⚙️</span>
            Control Panel Simulasi
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Konfigurasi parameter Semaphore dan picu ribuan concurrent request.
          </p>
        </div>
        <div>
          {currentEventId ? (
            <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              ● Event ID #{currentEventId} Siap
            </span>
          ) : (
            <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
              ○ Belum Diinisialisasi
            </span>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {/* Event Name */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Nama Event
          </label>
          <input
            type="text"
            name="eventName"
            value={config.eventName}
            onChange={handleChange}
            disabled={isRunning}
            className="w-full bg-slate-800/80 border border-slate-700 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 transition-colors disabled:opacity-50"
            placeholder="Misal: Coldplay Tour 2026"
          />
        </div>

        {/* Total Tickets */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Stok Tiket Awal
          </label>
          <input
            type="number"
            name="totalTickets"
            value={config.totalTickets}
            onChange={handleChange}
            disabled={isRunning}
            min="1"
            className="w-full bg-slate-800/80 border border-slate-700 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 transition-colors disabled:opacity-50"
          />
        </div>

        {/* Semaphore Permits */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Batas Permit Semaphore
          </label>
          <input
            type="number"
            name="semaphorePermits"
            value={config.semaphorePermits}
            onChange={handleChange}
            disabled={isRunning}
            min="1"
            max="100"
            className="w-full bg-slate-800/80 border border-slate-700 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 transition-colors disabled:opacity-50"
          />
        </div>

        {/* Total Simulated Requests */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
            Jumlah Request War
          </label>
          <input
            type="number"
            name="requestCount"
            value={config.requestCount}
            onChange={handleChange}
            disabled={isRunning}
            min="1"
            max="5000"
            className="w-full bg-slate-800/80 border border-slate-700 rounded-xl px-4 py-2.5 text-white text-sm focus:outline-none focus:border-indigo-500 transition-colors disabled:opacity-50"
          />
        </div>
      </div>

      {/* Toggle & Slider Delay Pemrosesan */}
      <div className="mb-6 p-4 rounded-xl bg-slate-800/40 border border-slate-700/60 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <label className="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              name="enableDelay"
              checked={config.enableDelay}
              onChange={(e) =>
                setConfig((prev) => ({ ...prev, enableDelay: e.target.checked }))
              }
              disabled={isRunning}
              className="sr-only peer"
            />
            <div className="w-11 h-6 bg-slate-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-600"></div>
          </label>
          <div>
            <span className="text-sm font-semibold text-white">Simulasi Delay Pemrosesan</span>
            <p className="text-xs text-slate-400">
              {config.enableDelay
                ? 'Menyimulasikan jeda transaksi (misal: validasi payment)'
                : 'Tanpa delay tambahan (eksekusi instan)'}
            </p>
          </div>
        </div>

        {config.enableDelay && (
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <input
              type="range"
              name="processingDelayMs"
              min="0"
              max="500"
              step="10"
              value={config.processingDelayMs}
              onChange={handleChange}
              disabled={isRunning}
              className="w-32 h-2 bg-slate-700 rounded-lg appearance-none cursor-pointer accent-indigo-500"
            />
            <div className="flex items-center gap-1.5">
              <input
                type="number"
                name="processingDelayMs"
                value={config.processingDelayMs}
                onChange={handleChange}
                disabled={isRunning}
                min="0"
                max="5000"
                className="w-20 bg-slate-800 border border-slate-700 rounded-lg px-2.5 py-1 text-white text-sm text-right font-mono focus:outline-none focus:border-indigo-500"
              />
              <span className="text-xs font-mono text-slate-400">ms</span>
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-4 pt-2 border-t border-slate-800/80">
        <button
          onClick={onInit}
          disabled={isRunning || isInitializing}
          className="flex-1 md:flex-none px-6 py-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-medium text-sm transition-all duration-200 border border-slate-700 flex items-center justify-center gap-2 disabled:opacity-50"
        >
          {isInitializing ? (
            <>
              <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>Menginisialisasi...</span>
            </>
          ) : (
            <>
              <span>🔄</span> Inisialisasi / Reset Simulasi
            </>
          )}
        </button>

        <button
          onClick={onStartTraffic}
          disabled={!currentEventId || isRunning}
          className="flex-1 px-6 py-3 rounded-xl bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 hover:from-indigo-500 hover:to-pink-500 text-white font-bold text-sm shadow-lg shadow-indigo-500/25 hover:shadow-indigo-500/40 transition-all duration-200 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isRunning ? (
            <>
              <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <span>Simulasi War Sedang Berlangsung...</span>
            </>
          ) : (
            <>
              <span>⚡</span> Mulai Simulasi War Ticket ({config.requestCount} Request)
            </>
          )}
        </button>
      </div>
    </div>
  )
}
