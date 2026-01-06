# Epidemic UI API – Contract and Architecture

This document describes the **API contract** between the backend (Ktor + Redis Streams) and the UI applications:  
- 🌐 Web application  
- 🖥️ Desktop application (Kotlin Multiplatform / Compose)  

The goal is to enable:  
- visualization of epidemic progression  
- comparison of multiple runs  
- support for both batch and live modes  

---

## 🎯 Architectural Assumptions

1. The UI **does not know about Redis**  
2. The UI **does not compute statistics** – it only displays them  
3. Backend:  
   - aggregates data  
   - computes metrics  
   - exposes REST / WebSocket endpoints (optional)

---

## 🧱 Core Concepts

### Run
A single complete epidemic simulation (experiment)

### Point
A single generation (tick)

### Curve
Epidemic progression over time (I / R / S / D / E)

---

## 🌐 API Endpoints (MVP)

### 1️⃣ List of runs

**GET `/api/runs`**

Used in the UI to:  
- list epidemics  
- select a run  
- compare runs  

**Response**
```json
[
  {
    "runId": "esp32-01-1700001200",
    "deviceId": "esp32-01",
    "startedAt": 1700001200,
    "endedAt": 1700001350,
    "populationSize": 214,
    "duration": 53,
    "peakInfected": 89
  }
]
```

---

### 2️⃣ Details of a single run (timeline)

**GET `/api/runs/{runId}`**

Used in the UI to:  
- render I / R / S charts  
- analyze progression  

**Response**
```json
{
  "runId": "esp32-01-1700001200",
  "meta": {
    "deviceId": "esp32-01",
    "populationSize": 214,
    "startedAt": 1700001200,
    "endedAt": 1700001350
  },
  "timeline": [
    {
      "gen": 0,
      "infected": 46,
      "recovered": 0,
      "susceptible": 168,
      "exposed": 0,
      "dead": 0,
      "lockdown": false,
      "mobilityMultiplier": 1.0,
      "byType": {
         "children": { "infected": 5, "susceptible": 20, "exposed": 0, "recovered": 0, "dead": 0 },
         "adults": { "infected": 30, "susceptible": 100, "exposed": 0, "recovered": 0, "dead": 0 },
         "senior": { "infected": 15, "susceptible": 50, "exposed": 0, "recovered": 0, "dead": 0 }
      }
    }
  ]
}
```

---

### 3️⃣ Run summary (optional)

**GET `/api/runs/{runId}/summary`**

```json
{
  "duration": 53,
  "peakInfected": 89,
  "timeToPeak": 14,
  "finalRecovered": 176,
  "finalDead": 5
}
```

---

## 🔴 Live view (optional)

**WebSocket `/api/runs/{runId}/live`**

Each new generation is sent as:  
```json
{
  "gen": 17,
  "infected": 63,
  "recovered": 121,
  "susceptible": 30,
  "exposed": 5,
  "dead": 2,
  "lockdown": true,
  "mobilityMultiplier": 0.8,
  "byType": {
    "children": { "infected": 3, "susceptible": 10, "exposed": 1, "recovered": 5, "dead": 0 },
    "adults": { "infected": 50, "susceptible": 15, "exposed": 2, "recovered": 110, "dead": 2 }
  }
}
```

Used for:  
- live charts  
- simulation monitoring  

---

## 🧩 Data Models (Kotlin Multiplatform)

The same models can be used in:  
- backend (Ktor)  
- web UI (Kotlin/JS)  
- desktop UI (Compose)  

```kotlin
@Serializable
data class EpidemicPoint(
    val gen: Int,
    val infected: Int,
    val recovered: Int,
    val susceptible: Int,
    val exposed: Int,
    val dead: Int,
    val lockdown: Boolean,
    val mobilityMultiplier: Double,
    val byType: Map<String, EpidemicPointByType> = emptyMap()
)

@Serializable
data class EpidemicPointByType(
    val infected: Int,
    val susceptible: Int,
    val exposed: Int,
    val recovered: Int,
    val dead: Int
)

@Serializable
data class RunMeta(
    val deviceId: String,
    val populationSize: Int,
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

## 🗂️ Data Layer (Redis – reference)

- **Stream** – time-series data of a run  
```
epidemic:device:{deviceId}:run:{runId}
```

- **SET** – index of runs  
```
epidemic:runs
```

- **HASH fields for stream entries** (updated per generation):
```kotlin
private fun prepareBody(epidemicDto: EpidemicDto): Map<String, String> {
    val base = mutableMapOf(
        "generation" to epidemicDto.meta.generation.toString(),
        "runId" to epidemicDto.meta.runId.toString(),
        "timestamp" to epidemicDto.meta.timestamp.toString(), // ISO8601-UTC
        "population" to epidemicDto.state.population.toString(),
        "susceptible" to epidemicDto.state.susceptible.toString(),
        "infected" to epidemicDto.state.infected.toString(),
        "recovered" to epidemicDto.state.recovered.toString(),
        "dead" to epidemicDto.state.dead.toString(),
        "exposed" to epidemicDto.state.exposed.toString(),
        "lockdown" to epidemicDto.state.lockdown.toString(),
        "mobilityMul" to epidemicDto.state.mobilityMultiplier.toString()
    )
    epidemicDto.state.detailedDataByType.forEach { (type, data) ->
        base["byType:$type:infected"] = data.infected.toString()
        base["byType:$type:susceptible"] = data.susceptible.toString()
        base["byType:$type:exposed"] = data.exposed.toString()
        base["byType:$type:recovered"] = data.recovered.toString()
        base["byType:$type:dead"] = data.dead.toString()
    }
    return base
}
```

---

## 🧠 Why this contract?

- Thin UI  
- Backend can change storage without breaking clients  
- Easy CSV export  
- One contract = web + desktop  

---

## 🚀 Next Steps (optional)

- Run comparison (overlay charts)  
- Filtering by parameters  
- CSV / JSON export  
- Batch analysis (Pandas / Kotlin DataFrame)  

---

**Author:** d18

