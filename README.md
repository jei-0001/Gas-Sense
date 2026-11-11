# Gas Sense

**An IoT-based LPG monitoring system using gas thresholds, pressure rate-of-change, and sensor fusion.**

Gas Sense is a prototype residential and small-commercial safety system designed to detect possible propane/LPG leaks and alert users. It combines gas concentration readings with pressure and environmental measurements to classify hazards, track tank usage, and trigger a local response when a critical condition is detected.

## Features

- **Multi-sensor monitoring:** Combines gas, pressure, temperature, humidity, and tank-weight measurements.
- **Threshold and trend detection:** Classifies gas concentration and watches for rapid pressure drops that may indicate a leak.
- **Sensor fusion:** Uses multiple readings to cross-check hazard states and help reduce false alarms.
- **Automatic shutoff:** Activates a solenoid valve when the system classifies a condition as critical.
- **Mobile and SMS alerts:** Sends hazard notifications and presents live sensor status.
- **Tank and usage tracking:** Estimates remaining capacity and records usage history.

## Hardware

The prototype materials describe the following components:

- ESP32 microcontroller
- MQ-6 gas sensor
- Pressure transducer
- Load cell with HX711 amplifier
- DHT22 temperature and humidity sensor
- Solenoid shutoff valve
- LCD display

The exact pressure transducer model and wiring may vary by prototype revision. Refer to the applicable wiring diagram and hardware notes before assembling the device.

## Hazard classification

The project presentation describes these gas concentration bands:

| State | Gas concentration | Response |
| --- | --- | --- |
| Safe | Below 2,100 PPM | Continue monitoring |
| Warning | 2,100–5,250 PPM | Send an alert |
| Critical | Above 5,250 PPM, or a rapid pressure drop | Alert and activate the shutoff valve |

The thresholds are prototype configuration values, not a substitute for certified gas-safety equipment or professional installation. Validate sensor calibration and local safety requirements before any real-world use.

## Evaluation

The reported prototype evaluation lists 92% detection accuracy, 0.97 seconds for gas detection, 1.47 seconds for valve shutoff, and 9.48 seconds for SMS notification. These are project-reported results; testing conditions and deployment performance may differ.

## Project status

Gas Sense is a research prototype. The project materials identify offline alert redundancy, additional gas-specific sensors, iOS support, and real-world deployment validation as areas for future work.
