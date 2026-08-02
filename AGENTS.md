# Scorebound - Projektanweisungen

Diese Datei gilt fuer das gesamte Repository.

## Projektziel

Scorebound soll eine allgemein nutzbare Anwendung fuer Teams und Punktestaende
werden. Der erste Schwerpunkt ist eine lokal betriebene, responsive Webanwendung
fuer Desktop-Rechner. Konkrete Fachfunktionen werden erst umgesetzt, wenn sie
ausdruecklich beauftragt und fachlich geklaert wurden.

## Aktueller technischer Rahmen

- Monorepo mit getrennten Ordnern fuer Backend und Frontend
- `backend/`: Java 21, Spring Boot, Maven Wrapper und PostgreSQL
- `frontend/`: React, TypeScript und Vite
- Projektname: Scorebound
- Git wird vom Projekteigentuemer eingerichtet
- Fuer die Entwicklung wird eine bereits vorhandene PostgreSQL-Instanz verwendet.

Die Entscheidung, ob das gebaute Frontend spaeter durch Spring Boot ausgeliefert
wird, ist noch offen. Bis dahin werden Backend und Frontend als getrennte
Entwicklungsprojekte behandelt.

## Arbeitsregeln

- Der Projekteigentuemer entwickelt das Backend selbst. Backend-Code darf nur
  geschrieben, erweitert oder veraendert werden, wenn er dies fuer eine konkrete
  Aufgabe ausdruecklich verlangt.
- Insbesondere keine Entities, Repositories, Services, Controller, DTOs,
  Datenbankmigrationen, Sicherheitskonfigurationen oder Backend-Tests
  vorwegnehmen.
- Auch naheliegende Backend-Funktionen nicht vorsorglich implementieren. Bei
  Fragen zum Backend beraten oder analysieren, aber ohne ausdruecklichen
  Aenderungsauftrag keine Dateien veraendern.
- Keine Fachlogik, Beispiel-Domaenenobjekte oder fertigen Oberflaechen ohne
  ausdruecklichen Auftrag implementieren.
- Bei unklarem Funktionsumfang zuerst Rueckfragen stellen.
- Bestehende Aenderungen des Projekteigentuemers nicht ueberschreiben.
- Java-Code und technische Bezeichner auf Englisch schreiben.
- Neue Abhaengigkeiten nur mit einem konkreten Bedarf hinzufuegen.
- Keine Secrets, lokalen Datenbankdaten, Build-Ausgaben oder `node_modules`
  versionieren.
- Nach Aenderungen die jeweils betroffenen Builds oder Tests ausfuehren.
- Desktop ist primaer; spaetere Oberflaechen sollen trotzdem responsive und
  mit Tastatur bedienbar sein.
- Keine geschuetzten Namen, Wappen oder Grafiken bekannter Franchises verwenden.

## Standardbefehle

Backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
npm run lint
npm run build
```
