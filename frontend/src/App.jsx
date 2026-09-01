import React, { useState, useEffect, useRef, useMemo } from 'react'
import ControlPanel from './components/ControlPanel'
import LiveMonitor from './components/LiveMonitor'
import ResultDashboard from './components/ResultDashboard'

/**
 * Daftar instance dipecah dari satu string agar bisa diubah dari UI tanpa build ulang.
 * Papan selalu mengambil aliran SSE dari instance pertama; snapshotnya sama dari instance mana
 * pun karena seluruh keadaan slot ada di Redis, bukan di heap salah satu instance.
 */
function parseBackends(raw) {
  return raw
    .split(',')
    .map((url) => url.trim().replace(/\/$/, ''))
    .filter(Boolean)
}


export default function App() {
  const [config, setConfig] = useState({
    eventName: 'Coldplay War Ticket Simulation 2026',
    totalTickets: 100,
    semaphorePermits: 5,
    requestCount: 300,
    slowMotion: true,
    thinkTimeMs: 400,
    paymentSuccessPercent: 90,
    maxAttempts: 5,
    retryDelayMs: 300,
    backends: 'http://localhost:8080,http://localhost:8081',
    waveCount: 100,
    restockAmount: 50
  })

  const [currentEventId, setCurrentEventId] = useState(null)
  const [isInitializing, setIsInitializing] = useState(false)
  const [isRunning, setIsRunning] = useState(false)
  const [isConnected, setIsConnected] = useState(false)
  const [logs, setLogs] = useState([])

  const [status, setStatus] = useState({
    availableTickets: 100,
    totalTickets: 100,
    activePermits: 0,
    totalPermits: 5,
    totalRequests: 0,
    successRequests: 0,
    failedOutOfStock: 0,
    failedRejected: 0,
    failedPayment: 0,
    abandoned: 0,
    gaveUp: 0,
    sessionLeaseMs: 0,
    message: 'Aplikasi siap',
    slots: [],
    reportedBy: null,
    instances: []
  })

  // Jejak aktivitas terakhir. Simulasi dianggap masih berjalan selama ada slot terpakai atau
  // jumlah percobaan masih bertambah; keduanya dibaca dari snapshot, bukan ditebak dengan timer.
  const activityRef = useRef({ attempts: 0, changedAt: 0 })
  // Ditandai saat tombol ditekan, dibaca oleh efek di bawah pada snapshot berikutnya. Sekadar
  // penanda, bukan stempel waktu, supaya jam tidak pernah dibaca di luar efek.
  const pendingLaunchRef = useRef(false)

  const backends = useMemo(() => parseBackends(config.backends), [config.backends])
  const primary = backends[0] || 'http://localhost:8080'

  const eventSourceRef = useRef(null)

  // Append new log item
  const addLog = (text) => {
    const time = new Date().toLocaleTimeString('id-ID', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
    setLogs((prev) => [{ time, text }, ...prev.slice(0, 49)])
  }

  /**
   * Menurunkan keadaan "sedang berjalan" dari simulasi itu sendiri.
   *
   * Sebelumnya keadaan ini dimatikan dua detik setelah request penembakan dikirim, padahal yang
   * dua detik itu cuma lama pengirimannya. Perangnya sendiri bisa berlangsung menit-menitan, jadi
   * isian dan tombol hidup lagi di tengah simulasi dan mengubahnya di situ tidak berpengaruh
   * apa-apa terhadap simulasi yang sedang jalan — hanya membingungkan.
   *
   * Ada dua tanda kehidupan, dan keduanya perlu. Slot terpakai berarti ada sesi yang sedang
   * berjalan. Cacah percobaan yang masih bertambah berarti masih ada pembeli yang mencoba ulang
   * meski belum satu pun kebagian slot. Ambang diamnya mengikuti jeda coba ulang, karena pada
   * jeda yang panjang cacahnya memang wajar tidak berubah selama beberapa detik.
   */
  useEffect(() => {
    const attempts = status.totalRequests || 0
    const now = Date.now()
    // Penembakan yang baru dipesan diperlakukan sebagai aktivitas, karena percobaan pertamanya
    // belum sempat tercatat di snapshot ini.
    if (attempts !== activityRef.current.attempts || pendingLaunchRef.current) {
      activityRef.current = { attempts, changedAt: now }
      pendingLaunchRef.current = false
    }

    const quietMs = Math.max(1500, config.retryDelayMs * 2)
    const busy =
      (status.activePermits || 0) > 0
      || now - activityRef.current.changedAt < quietMs

    setIsRunning((prev) => {
      if (prev === busy) return prev
      if (!busy) addLog('Simulasi selesai, seluruh sesi sudah berakhir.')
      return busy
    })
  }, [status, config.retryDelayMs])

  // Setup Server-Sent Events (SSE)
  useEffect(() => {
    function connectSse() {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }

      const sse = new EventSource(`${primary}/api/simulation/stream`)
      eventSourceRef.current = sse

      sse.onopen = () => {
        setIsConnected(true)
        addLog('Terhubung ke Server-Sent Events (SSE) Stream.')
      }

      sse.addEventListener('INIT', (event) => {
        setIsConnected(true)
        addLog(`SSE Init: ${event.data}`)
      })

      // Snapshot keadaan, 10 kali per detik. Sengaja tidak menulis log: isinya sama terus.
      sse.addEventListener('STATUS_UPDATE', (event) => {
        try {
          setStatus(JSON.parse(event.data))
        } catch (e) {
          console.error('Failed to parse SSE payload', e)
        }
      })

      // Satu baris per peristiwa. Dipisah dari snapshot supaya peristiwa tidak ikut membawa
      // ongkos penyusunan snapshot.
      sse.addEventListener('ACTIVITY', (event) => {
        addLog(event.data)
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
  }, [primary])

  // Inisialisasi / Reset Simulasi
  const handleInit = async () => {
    try {
      setIsInitializing(true)
      const res = await fetch(`${primary}/api/simulation/init`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventName: config.eventName,
          totalTickets: config.totalTickets,
          semaphorePermits: config.semaphorePermits,
          thinkTimeMs: config.slowMotion ? config.thinkTimeMs : 0,
          paymentSuccessPercent: config.paymentSuccessPercent
        })
      })

      if (!res.ok) {
        throw new Error(`HTTP error! status: ${res.status}`)
      }

      const data = await res.json()
      setCurrentEventId(data.id)
      addLog(`Event "${data.name}" berhasil dibuat (ID: ${data.id}, Stok: ${data.totalTickets}).`)

      // Fetch snapshot status
      const statusRes = await fetch(`${primary}/api/simulation/status`)
      if (statusRes.ok) {
        const s = await statusRes.json()
        setStatus(s)
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

    // Ditandai sibuk sejak tombol ditekan. Snapshot pertama yang membuktikan simulasi berjalan
    // baru tiba seperseratus detik kemudian, dan tanpa penanda ini tombolnya sempat hidup lagi.
    pendingLaunchRef.current = true
    setIsRunning(true)

    // Request dibagi rata ke seluruh instance. Inilah yang membuat batas konkurensi bisa diuji:
    // kalau batasnya hidup di heap masing-masing instance, dua instance akan menjalankan 2N
    // request bersamaan. Karena batasnya ada di Redis, jumlahnya tetap N.
    try {
      await fireRequests(config.requestCount, 'Gelombang awal')
    } catch (err) {
      addLog(`Error saat traffic: ${err.message}`)
    }
  }

  /**
   * Menembakkan sejumlah pembeli ke seluruh instance yang benar-benar menjawab.
   *
   * Instance yang tercantum tetapi mati harus disingkirkan lebih dulu, bukan dibiarkan kebagian
   * jatah. Sebelumnya jatahnya tetap dibagi rata ke semua alamat yang tertulis, sehingga saat satu
   * instance mati separuh pembeli lenyap tanpa jejak dan angka di papan terlihat seperti salah
   * hitung padahal yang salah adalah jumlah pembeli yang benar-benar berangkat.
   */
  const fireRequests = async (count, label) => {
    const probes = await Promise.allSettled(
      backends.map((base) =>
        fetch(`${base}/api/simulation/status`).then((res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
          return base
        })
      )
    )

    const live = backends.filter((_, i) => probes[i].status === 'fulfilled')
    const dead = backends.filter((_, i) => probes[i].status === 'rejected')
    if (dead.length > 0) {
      addLog(`Instance tidak menjawab, dilewati: ${dead.join(', ')}`)
    }
    if (live.length === 0) {
      addLog('Tidak ada instance yang menjawab. Tidak ada pembeli yang berangkat.')
      return
    }

    const share = Math.floor(count / live.length)
    const shares = live.map((_, i) => (i === 0 ? count - share * (live.length - 1) : share))
    addLog(`${label}: ${count} pembeli ke ${live.length} instance (${shares.join(' + ')}).`)

    const results = await Promise.allSettled(
      live.map((base, i) =>
        fetch(
          `${base}/api/simulation/traffic?requestCount=${shares[i]}`
            + `&maxAttempts=${config.maxAttempts}&retryDelayMs=${config.retryDelayMs}`,
          { method: 'POST' }
        ).then((res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`)
        })
      )
    )

    const lost = results.reduce((sum, r, i) => (r.status === 'rejected' ? sum + shares[i] : sum), 0)
    if (lost > 0) {
      addLog(`${lost} pembeli gagal berangkat karena instance-nya berhenti menjawab.`)
    }
  }

  const handleInjectWave = () => fireRequests(config.waveCount, 'Gelombang tambahan')

  const handleRestock = async () => {
    try {
      const res = await fetch(
        `${primary}/api/simulation/restock?amount=${config.restockAmount}`,
        { method: 'POST' }
      )
      addLog(await res.text())
    } catch (err) {
      addLog(`Gagal menambah stok: ${err.message}`)
    }
  }

  return (
    <div className="wrap">
      <header className="masthead">
        <div>
          <h1>Papan Observasi Semaphore</h1>
          <p>
            Batas konkurensi ditegakkan di Redis, bukan di heap satu instance. Slot punya nomor,
            pemilik, dan tenggat, sehingga yang biasanya hanya berupa angka bisa diamati bergerak.
          </p>
        </div>
        <div className="stampbar">
          <span className={`stamp ${isConnected ? 'live' : 'down'}`}>
            {isConnected ? 'SSE tersambung' : 'SSE terputus'}
          </span>
          <span className="stamp">Spring Boot 3.4 · Redis · React</span>
        </div>
      </header>

      <ControlPanel
        config={config}
        setConfig={setConfig}
        onInit={handleInit}
        onStartTraffic={handleStartTraffic}
        isInitializing={isInitializing}
        isRunning={isRunning}
        currentEventId={currentEventId}
        instances={status.instances}
        onInjectWave={handleInjectWave}
        onRestock={handleRestock}
      />

      <LiveMonitor status={status} logs={logs} />

      <ResultDashboard status={status} />

      <footer className="foot">Simulasi Semaphore Terdistribusi &copy; 2026</footer>
    </div>
  )
}
