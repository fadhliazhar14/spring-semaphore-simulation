# Spring Semaphore Simulation

Simulasi sistem penanganan lonjakan trafik (War Ticket) menggunakan algoritma Semaphore di Spring Boot untuk mengontrol konkurensi, mencegah kondisi race condition, serta memvisualisasikan status antrean dan permit secara real-time ke antarmuka React.

## Arsitektur dan Teknologi

- Backend: Java 17+, Spring Boot 3.4 (Spring Web, Spring Data JPA, PostgreSQL Driver)
- Frontend: React 19, Vite, TailwindCSS
- Real-time Communication: Server-Sent Events (SSE)
- Database: PostgreSQL (In-Memory H2 untuk automated tests)

## Prasyarat

- Java Development Kit (JDK) versi 17 atau lebih baru
- Node.js versi 18 atau lebih baru dan npm
- PostgreSQL Server

## Panduan Menjalankan Aplikasi

### 1. Inisialisasi Database

Jalankan skrip inisialisasi basis data yang tersedia di `init.sql` pada instance PostgreSQL lokal:

```bash
psql -U postgres -f init.sql
```

### 2. Konfigurasi dan Menjalankan Backend

Atur variabel lingkungan untuk koneksi database pada sesi terminal, kemudian jalankan Maven wrapper:

**PowerShell:**
```powershell
$env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="ticket_simulation"; $env:DB_USER="postgres"; $env:DB_PASSWORD="password_anda"
cd backend
./mvnw.cmd spring-boot:run
```

**Bash / Linux / macOS:**
```bash
export DB_HOST=localhost DB_PORT=5432 DB_NAME=ticket_simulation DB_USER=postgres DB_PASSWORD=password_anda
cd backend
./mvnw spring-boot:run
```

Backend akan aktif pada alamat `http://localhost:8080`.

### 3. Menjalankan Frontend

Buka terminal baru, masuk ke direktori frontend, lalu pasang dependensi dan jalankan server pengembangan:

```bash
cd frontend
npm install
npm run dev
```

Aplikasi frontend dapat diakses melalui browser pada alamat `http://localhost:5173`.

## Alur Simulasi

1. Buka antarmuka frontend di browser (`http://localhost:5173`).
2. Pada Control Panel, tentukan Nama Event, Stok Tiket Awal, Batas Permit Semaphore, dan Jumlah Request War.
3. Klik tombol "Inisialisasi / Reset Simulasi" untuk membuat state event baru.
4. Klik tombol "Mulai Simulasi War Ticket" untuk mengirimkan request konkurensi.
5. Pantau visualisasi pergerakan slot Semaphore dan baris antrean pada Live Backend Monitor.
6. Evaluasi metrik keberhasilan transaksi pada Result Dashboard.

## Pengujian

Menjalankan seluruh pengujian unit dan integrasi pada backend:

```bash
cd backend
./mvnw test
```
