# Epidemic UI API – Kontrakt i Architektura

Ten dokument opisuje **kontrakt API** pomiędzy backendem (Ktor + Redis Streams) a aplikacjami UI:
- 🌐 aplikacja przeglądarkowa
- 🖥️ aplikacja okienkowa (Kotlin Multiplatform / Compose)

Celem jest umożliwienie:
- wizualizacji przebiegu epidemii
- porównywania wielu runów
- pracy zarówno w trybie batch, jak i live

---

## 🎯 Założenia architektoniczne

1. UI **nie zna Redisa**
2. UI **nie liczy statystyk** – tylko je wyświetla
3. Backend:
   - agreguje dane
   - liczy metryki
   - wystawia REST / WebSocket

---

## 🧱 Podstawowe pojęcia

### Run
Jedna pełna symulacja epidemii (eksperyment)

### Point
Jedna generacja (tick)

### Curve
Przebieg epidemii w czasie (I / R / S)

---

## 🌐 Endpointy API (MVP)

### 1️⃣ Lista runów

**GET `/api/runs`**

Zastosowanie w UI:
- lista epidemii
- wybór runu
- porównania

**Response**
```json
[
  {
    "runId": "esp32-01-1700001200",
    "deviceId": "esp32-01",
    "startedAt": 1700001200,
    "endedAt": 1700001350,
    "populationSize": 214,
    "infectionProb": 0.32,
    "duration": 53,
    "peakInfected": 89
  }
]
```

---

### 2️⃣ Szczegóły jednego runu (timeline)

**GET `/api/runs/{runId}`**

Zastosowanie w UI:
- wykres I / R / S
- analiza przebiegu

**Response**
```json
{
  "runId": "esp32-01-1700001200",
  "meta": {
    "deviceId": "esp32-01",
    "populationSize": 214,
    "infectionProb": 0.32,
    "startedAt": 1700001200,
    "endedAt": 1700001350
  },
  "timeline": [
    { "gen": 0, "infected": 46, "recovered": 0, "susceptible": 168 },
    { "gen": 1, "infected": 77, "recovered": 44, "susceptible": 93 },
    { "gen": 2, "infected": 120, "recovered": 121, "susceptible": -27 }
  ]
}
```

---

### 3️⃣ Podsumowanie runu (opcjonalne)

**GET `/api/runs/{runId}/summary`**

```json
{
  "duration": 53,
  "peakInfected": 89,
  "timeToPeak": 14,
  "attackRate": 0.82,
  "finalRecovered": 176
}
```

---

## 🔴 Live view (opcjonalne)

**WebSocket `/api/runs/{runId}/live`**

Każda nowa generacja:
```json
{
  "gen": 17,
  "infected": 63,
  "recovered": 121,
  "susceptible": 30
}
```

Zastosowanie:
- live wykres
- monitoring symulacji

---

## 🧩 Modele danych (Kotlin Multiplatform)

Te same modele mogą być użyte w:
- backendzie (Ktor)
- UI web (Kotlin/JS)
- UI desktop (Compose)

```kotlin
@Serializable
data class EpidemicPoint(
    val gen: Int,
    val infected: Int,
    val recovered: Int,
    val susceptible: Int
)

@Serializable
data class RunMeta(
    val deviceId: String,
    val populationSize: Int,
    val infectionProb: Double,
    val startedAt: Long,
    val endedAt: Long?
)

@Serializable
data class EpidemicRun(
    val runId: String,
    val meta: RunMeta,
    val timeline: List<EpidemicPoint>
)
```

---

## 🗂️ Warstwa danych (Redis – referencja)

- **Stream** – dane czasowe runu
```
epidemic:run:{runId}
```

- **SET** – indeks runów
```
epidemic:runs
```

- **HASH** – metadata runu
```
epidemic:run:{runId}:meta
```

---

## 🧠 Dlaczego taki kontrakt?

- UI jest cienkie
- backend może zmieniać storage
- łatwy eksport do CSV
- jeden kontrakt = web + desktop

---

## 🚀 Kolejne kroki (opcjonalne)

- porównywanie runów (overlay wykresów)
- filtrowanie po parametrach
- eksport CSV / JSON
- analiza batch (Pandas / Kotlin DataFrame)

---

**Autor:** Epidemic Simulation Project

