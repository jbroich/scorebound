# Scorebound

Scorebound ist als lokal betreibbare, responsive Webanwendung fuer Teams und
Punktestaende geplant. Das Repository enthaelt derzeit ausschliesslich die
technischen Projektgrundgerueste. Fachfunktionen sind noch nicht implementiert.

Die abgestimmten Anforderungen stehen in
[`docs/product-requirements.md`](docs/product-requirements.md). Der geordnete
Entwicklungs-Backlog wird bis zur Anlage der GitHub Issues in
[`TASKS.md`](TASKS.md) gepflegt.

## Struktur

- `backend/`: Java 21, Spring Boot 4.1, Maven und PostgreSQL
- `frontend/`: React, TypeScript und Vite

Backend und Frontend laufen waehrend der Entwicklung getrennt. Ob sie spaeter
gemeinsam ausgeliefert werden, ist noch nicht entschieden.

## Voraussetzungen

- Java 21
- Node.js 22 oder neuer
- eine erreichbare PostgreSQL-Installation

Maven muss nicht global installiert sein. Das Backend enthaelt den Maven Wrapper.

## Datenbank konfigurieren

Das Backend verwendet eine bereits vorhandene PostgreSQL-Instanz. Die Verbindung
wird lokal ueber `SCOREBOUND_DB_URL`, `SCOREBOUND_DB_USERNAME` und
`SCOREBOUND_DB_PASSWORD` konfiguriert. Zugangsdaten werden nicht im Repository
gespeichert.

Ohne gesetzte Variablen gelten fuer die Entwicklung die Platzhalter
`jdbc:postgresql://localhost:5432/scorebound`, Benutzer `scorebound` und Passwort
`scorebound`. Diese Werte koennen an den vorhandenen Container angepasst werden.

## Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Das Backend startet standardmaessig unter <http://localhost:8080>.

## Frontend

```powershell
cd frontend
npm install
npm run dev
```

Vite zeigt die lokale URL beim Start an, standardmaessig
<http://localhost:5173>.

## Pruefen

Die Backend-Tests verwenden eine fluechtige In-Memory-Datenbank. Fuer den
Testlauf muss daher keine lokale PostgreSQL-Datenbank gestartet werden.

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm ci
npm run lint
npm run build
```
