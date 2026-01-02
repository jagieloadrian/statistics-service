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
    "deviceId": "esp32-01",
    "runId": 12,
    "generation": 37,
    "timestamp": 1700001234
  },
  "params": {
    "populationSize": 214,
    "infectionProb": 0.32,
    "infectionTtlMin": 2,
    "infectionTtlMax": 5
  },
  "state": {
    "susceptible": 102,
    "infected": 67,
    "recovered": 45
  }
}
```