# Scorebound

Scorebound ist eine lokal betreibbare, responsive Webanwendung fuer Teams und
Punktestaende. Die Anwendung wird entlang des dokumentierten MVP-Backlogs
schrittweise entwickelt.

Die abgestimmten Anforderungen stehen in
[`docs/product-requirements.md`](docs/product-requirements.md). Der geordnete
Entwicklungs-Backlog wird bis zur Anlage der GitHub Issues in
[`TASKS.md`](TASKS.md) gepflegt.

Das technische Fachmodell und der geplante HTTP-Vertrag sind in
[`docs/domain-model.md`](docs/domain-model.md) und
[`docs/api-contract.md`](docs/api-contract.md) beschrieben. Architektur-
entscheidungen werden unter [`docs/decisions`](docs/decisions) festgehalten.
Die Produktionsbereitstellung auf dem Raspberry Pi ist in
[`docs/raspberry-pi-deployment.md`](docs/raspberry-pi-deployment.md) beschrieben.

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

## Ersten Admin anlegen

Beim ersten Start kann Scorebound den initialen Admin aus zwei
Umgebungsvariablen anlegen:

```powershell
$env:SCOREBOUND_BOOTSTRAP_ADMIN_USERNAME='admin'
$env:SCOREBOUND_BOOTSTRAP_ADMIN_PASSWORD='<temporary password>'
```

Beide Werte muessen gemeinsam gesetzt werden. Das Passwort wird nur als sicherer
Hash gespeichert und muss beim ersten Login geaendert werden. Nach dem Anlegen
des Accounts koennen die Bootstrap-Variablen aus der Laufzeitkonfiguration
entfernt werden. Fuer den spaeteren HTTPS-Betrieb wird zusaetzlich
`SCOREBOUND_SESSION_COOKIE_SECURE=true` gesetzt.

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

Die Oberflaeche startet auf Englisch und kann dauerhaft pro Browser auf Deutsch
umgeschaltet werden. Datums- und Zeitangaben verwenden standardmaessig
`Europe/Berlin`. Eine andere IANA-Zeitzone kann beim Frontend-Build ueber
`VITE_SCOREBOUND_TIME_ZONE` gesetzt werden. Die eigenstaendige dunkle
Display-Oberflaeche ist unter <http://localhost:5173/display> erreichbar.
Ein Account mit der Rolle `Display` meldet sich dort im langlebigen Display-
Modus an. Ein Admin ordnet ihm zuvor mindestens ein Scoreboard zu. Ueber die
Bedienelemente der Wandanzeige lassen sich Fixed-/Rotation-Modus, optionale
Sounds sowie Vollbild, Querformat und Bildschirm-Wake-Lock aktivieren.

## Pruefen

Die Backend-Tests verwenden eine fluechtige In-Memory-Datenbank. Fuer den
Testlauf muss daher keine lokale PostgreSQL-Datenbank gestartet werden. Flyway
wendet dabei dieselben versionierten Migrationen wie in Produktion an; die CI
prueft den Backend-Testlauf zusaetzlich gegen PostgreSQL.

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm ci
npm test
npm run lint
npm run build
npx playwright install chromium
npm run test:e2e
```

Die Playwright-Suite startet ihren eigenen lokalen Vite-Server und prueft die
kritischen Smartphone- und Landscape-Display-Ablaufe in Chromium. Der Browser
muss pro Entwicklungsumgebung nur einmal installiert werden.
