-- ==============================================================================
-- Database Initialization Script for Spring Semaphore Simulation
-- ==============================================================================

-- 1. Create Database (Run this on default 'postgres' database)
CREATE DATABASE ticket_simulation;

-- 2. Connect to the newly created database (if running through psql)
\c ticket_simulation;

-- 3. Create table for TicketEvent
CREATE TABLE IF NOT EXISTS ticket_events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    total_tickets INT NOT NULL,
    available_tickets INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Insert initial seed data
INSERT INTO ticket_events (name, total_tickets, available_tickets, created_at, updated_at)
VALUES ('Simulasi Konser Semaphore 2026', 100, 100, NOW(), NOW());
