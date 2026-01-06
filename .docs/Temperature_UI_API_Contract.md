# Epidemic UI API – Temperature & Humidity Extension

This document extends the existing **Epidemic UI API contract** with continuous **environmental data** (temperature and optional humidity) collected from a separate microcontroller.

The extension is fully compatible with the original architectural assumptions and keeps the UI thin and storage-agnostic.

---

## 🎯 Architectural Goals

1. UI applications **do not know about Redis**
2. UI applications **do not compute statistics**
3. Temperature data:
   - is **continuous** (not divided into runs)
   - is always associated with a `deviceId`
4. Backend:
   - stores raw measurements
   - aggregates data for UI needs
   - correlates temperature data with runs using timestamps

---

## 🧱 Core Concepts

### TemperaturePoint
A single environmental measurement (temperature, optional humidity)

### TemperatureSeries
A time-series of temperature points for a given device and time range

### TemperatureSummary
Aggregated temperature statistics for the duration of a specific timeline run

---

## 📦 Data Transfer Objects (Kotlin Multiplatform)

### Temperature DTO (raw measurement)

```kotlin
@Serializable
data class TemperatureDto(
    val status: String,
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val timestamp: LocalDateTime,
    val temperature: Double,
    val humidity: Double? = null
)
```

### TemperaturePoint (UI / API model)

```kotlin
@Serializable
data class TemperaturePoint(
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val timestamp: LocalDateTime,
    val temperature: Double,
    val humidity: Double? = null
)
```

### TemperatureSeries

```kotlin
@Serializable
data class TemperatureSeries(
    val deviceId: String,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val from: LocalDateTime,
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val to: LocalDateTime,
    val points: List<TemperaturePoint>
)
```

### RunTemperatureSummary

```kotlin
@Serializable
data class RunTemperatureSummary(
    val deviceId: String,
    val avgTemperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val avgHumidity: Double? = null,
    val minHumidity: Double? = null,
    val maxHumidity: Double? = null
)
```

---

## 🌐 API Endpoints – Temperature

### 1️⃣ List devices with temperature data

**GET `/api/temperature/devices`**

Used by the UI to:
- list available sensors
- select a device

**Response**
```json
[
  {
    "deviceId": "esp32-01",
    "firstSeen": "2024-11-10T08:12:00Z",
    "lastSeen": "2024-11-10T14:55:12Z"
  }
]
```

---

### 2️⃣ Temperature time-series for a device

**GET `/api/temperature/devices/{deviceId}`**

Query parameters:
- `from` – ISO8601 timestamp
- `to` – ISO8601 timestamp
- `resolution` (optional): `raw | 1m | 5m | 1h`

**Example**
```http
GET /api/temperature/esp32-01?from=2024-11-10T08:00:00Z&to=2024-11-10T12:00:00Z
```

**Response**
```json
{
  "deviceId": "esp32-01",
  "from": "2024-11-10T08:00:00Z",
  "to": "2024-11-10T12:00:00Z",
  "points": [
    {
      "deviceId": "esp32-01",
      "timestamp": "2024-11-10T08:00:00Z",
      "temperature": 21.4,
      "humidity": 43.2
    }
  ]
}
```

> The `resolution` parameter allows backend-side downsampling for large datasets.

---

### 3️⃣ Temperature summary 

**GET `/api/temperature/devices/{deviceId}/summary`**

Backend behavior:
- uses `startedAt` and `endedAt` of the collected data
- aggregates temperature data in that time range

**Response**
```json
{
  "deviceId": "esp32-01",
  "avgTemperature": 22.1,
  "minTemperature": 20.3,
  "maxTemperature": 24.0,
  "avgHumidity": 41.2,
  "minHumidity": 30.0,
  "maxHumidity": 50.0
}
```

Used in the UI to:
- display environmental context of a run
- compare temperature outcomes 

---

## 🔴 Live Temperature (Optional)

**WebSocket `/api/temperature/{deviceId}/live`**

Each new measurement is sent as:

```json
{
  "timestamp": "2024-11-10T14:55:12Z",
  "temperature": 22.8,
  "humidity": 40.9
}
```

Used for:
- live charts
- monitoring dashboards

---

## 🗂️ Redis – Reference Storage Layer

### Keys

```text
temperature:runs
temperature:device:{deviceId}:run
```

Although the key name contains `run`, temperature data is treated as a **continuous stream**.

### Redis Stream Entry Mapping

```kotlin
fun prepareTemperatureBody(dto: TemperatureDto): Map<String, String> =
    mutableMapOf(
        "deviceId" to dto.deviceId,
        "timestamp" to dto.timestamp.toString(),
        "temperature" to dto.temperature.toString(),
        "status" to dto.status
    ).apply {
        dto.humidity?.let {
            put("humidity", it.toString())
        }
    }
```

The backend is responsible for:
- converting Redis stream entries into `TemperaturePoint`
- aggregating data by time buckets
- correlating temperature data with runs

---

## 🧠 Why This Extension Fits the Architecture

- UI remains thin and passive
- No Redis knowledge leaks to clients
- One shared contract for Web and Desktop UIs
- Easy correlation between epidemic dynamics and environment
- Ready for CSV / JSON export and offline analysis

---

## 🚀 Future Extensions

- Alerts (e.g. temperature thresholds affecting runs)
- Additional sensors (CO₂, PM2.5, pressure) using the same pattern
- Batch analysis (Pandas / Kotlin DataFrame)

---

**Author:** d18

