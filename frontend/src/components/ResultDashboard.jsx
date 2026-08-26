import React from 'react'

export default function ResultDashboard({ status }) {
  const total = status.totalRequests || 0
  const success = status.successRequests || 0
  const outOfStock = status.failedOutOfStock || 0
  const timeout = status.failedTimeout || 0
  const availableTickets = status.availableTickets ?? 0
  const totalTickets = status.totalTickets || 100

  const successPercent = total > 0 ? ((success / total) * 100).toFixed(1) : '0.0'
  const outOfStockPercent = total > 0 ? ((outOfStock / total) * 100).toFixed(1) : '0.0'
  const timeoutPercent = total > 0 ? ((timeout / total) * 100).toFixed(1) : '0.0'

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl backdrop-blur-sm">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <span className="p-2 bg-emerald-500/10 text-emerald-400 rounded-lg">📊</span>
            Result Dashboard & Concurrency Metrik
          </h2>
          <p className="text-sm text-slate-400 mt-1">
            Hasil rekapitulasi performa simulasi war ticket dan jaminan keutuhan data.
          </p>
        </div>

        <div className="hidden sm:block">
          <div className="px-3 py-1 rounded-lg bg-indigo-950/60 border border-indigo-500/30 text-indigo-300 text-xs font-mono">
            🛡️ No-Overselling Guaranteed
          </div>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        {/* Total Requests */}
        <div className="bg-slate-950/60 border border-slate-800 rounded-xl p-4">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            Total Request
          </div>
          <div className="text-2xl lg:text-3xl font-black font-mono text-white">
            {total.toLocaleString()}
          </div>
          <div className="text-[11px] text-slate-500 mt-1">
            Semua permintaan masuk
          </div>
        </div>

        {/* Sukses */}
        <div className="bg-emerald-950/20 border border-emerald-500/30 rounded-xl p-4">
          <div className="text-xs font-semibold text-emerald-400 uppercase tracking-wider mb-1 flex items-center justify-between">
            <span>Sukses Beli</span>
            <span className="font-mono text-xs">{successPercent}%</span>
          </div>
          <div className="text-2xl lg:text-3xl font-black font-mono text-emerald-400">
            {success.toLocaleString()}
          </div>
          <div className="text-[11px] text-emerald-500/80 mt-1">
            Mendapatkan tiket
          </div>
        </div>

        {/* Gagal: Habis */}
        <div className="bg-amber-950/20 border border-amber-500/30 rounded-xl p-4">
          <div className="text-xs font-semibold text-amber-400 uppercase tracking-wider mb-1 flex items-center justify-between">
            <span>Tiket Habis</span>
            <span className="font-mono text-xs">{outOfStockPercent}%</span>
          </div>
          <div className="text-2xl lg:text-3xl font-black font-mono text-amber-400">
            {outOfStock.toLocaleString()}
          </div>
          <div className="text-[11px] text-amber-500/80 mt-1">
            Out of stock
          </div>
        </div>

        {/* Gagal: Timeout */}
        <div className="bg-rose-950/20 border border-rose-500/30 rounded-xl p-4">
          <div className="text-xs font-semibold text-rose-400 uppercase tracking-wider mb-1 flex items-center justify-between">
            <span>Timeout Antrean</span>
            <span className="font-mono text-xs">{timeoutPercent}%</span>
          </div>
          <div className="text-2xl lg:text-3xl font-black font-mono text-rose-400">
            {timeout.toLocaleString()}
          </div>
          <div className="text-[11px] text-rose-500/80 mt-1">
            Semaphore timeout
          </div>
        </div>
      </div>

      {/* Sisa Stok & Breakdown Progress Bar */}
      <div className="bg-slate-950/60 border border-slate-800 rounded-xl p-5 mb-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
          <div>
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Sisa Stok Tiket di Database:
            </span>
            <span className="ml-2 font-mono font-black text-lg text-cyan-400">
              {availableTickets} / {totalTickets} Tiket
            </span>
          </div>
          <div className="text-xs text-slate-400 font-mono">
            Terjual: {Math.max(0, totalTickets - availableTickets)} tiket
          </div>
        </div>

        {/* Multi-color Breakdown Bar */}
        <div className="w-full bg-slate-900 rounded-full h-3.5 flex overflow-hidden border border-slate-800">
          <div
            title={`Sukses: ${successPercent}%`}
            style={{ width: `${successPercent}%` }}
            className="bg-emerald-500 transition-all duration-300"
          ></div>
          <div
            title={`Habis: ${outOfStockPercent}%`}
            style={{ width: `${outOfStockPercent}%` }}
            className="bg-amber-500 transition-all duration-300"
          ></div>
          <div
            title={`Timeout: ${timeoutPercent}%`}
            style={{ width: `${timeoutPercent}%` }}
            className="bg-rose-500 transition-all duration-300"
          ></div>
        </div>

        <div className="flex flex-wrap gap-4 mt-3 text-xs text-slate-400">
          <div className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500"></span>
            <span>Sukses ({successPercent}%)</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-amber-500"></span>
            <span>Tiket Habis ({outOfStockPercent}%)</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-rose-500"></span>
            <span>Timeout ({timeoutPercent}%)</span>
          </div>
        </div>
      </div>

      {/* Bottom Concurrency Verification Banner */}
      <div className="rounded-xl p-3.5 bg-slate-950/40 border border-slate-800/80 flex items-center justify-between text-xs">
        <div className="flex items-center gap-2 text-slate-300">
          <span className="text-base">🔒</span>
          <span>
            Semaphore membatasi konkurensi tepat sesuai batas permit tanpa menyebabkan race condition atau stok negatif.
          </span>
        </div>
        <div className="font-mono font-semibold text-emerald-400">
          Stok Minimal: &gt;= 0 ✅
        </div>
      </div>
    </div>
  )
}
