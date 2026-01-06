# Temperature Collector Contract

## What is temperature collector?
It's a small project for developer board like esp32 or rpi pico 2w which
collect data by microcontrollers about data and maybe humidity (depends on device)
Here's the source code: (input link to the specific module of repo)

## How it will work? 

Microcontroller will send data every 30-60 second to the path `/v1/api/stats/collect/temperature`
with data which this server could work.

Example body:
```json
{
    "deviceId": "RPI_Pico_2w_1",
    "status": "up",
    "timestamp": "2026-01-05T15:38:14", //ISO-8601 UTC
    "temperature": 11.8,
    "humidity": 25.62 // or null 
  }
```