# Epidemic Contract

## What is epidemic?
It's a small project for developer board like esp32 or rpi pico 2w which simulate in simple way epidemic in small group of people. 
Starting with between 100-300 people and 10-30 % of infected people. 
Here's the source code: (input link to the specific module of repo)

## How it will work? 

Microcontroller will send data every 30-60 second to the path `/v1/api/stats/epidemic`
with data which this server could work.

Example body:
```json
{
  "meta": {
    "deviceId": "RPI_Pico_2w_1",
    "runId": 5,
    "timestamp": "2026-01-05T15:38:14", //ISO-8601 UTC
    "generation": 32
  },
  "state": {
    "infected": 2,
    "infectedPct": 0.76,
    "recovered": 44,
    "deadPct": 1.52,
    "susceptible": 45,
    "lockdown": false,
    "byType": {
      "0": {
        "infected": 0,
        "exposed": 0,
        "dead": 0,
        "recovered": 8,
        "susceptible": 18
      },
      "1": {
        "infected": 2,
        "exposed": 0,
        "dead": 1,
        "recovered": 16,
        "susceptible": 13
      },
      "2": {
        "infected": 0,
        "exposed": 1,
        "dead": 3,
        "recovered": 20,
        "susceptible": 14
      }
    },
    "population": 263,
    "exposed": 1,
    "dead": 4,
    "mobilityMul": 1.0
  }
}
```

field `byType` means state of maturity of person:
0 - Child, 1 - Mature, 2 - Senior