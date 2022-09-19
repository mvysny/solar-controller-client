# Solar Controller Client

Kotlin native app which communicate with Renogy Rover 40A over a RS232 serial port, using the Rover Modbus protocol.

Licensed under the MIT license.

## Intended use

Intended to be running on a Raspberry PI. The Raspberry PI needs to be connected over USB/RS232 adapter
to a RS232/RJ12 port of Renogy Rover. This program will periodically show the newest data on screen,
or optionally append the newest data to a CSV file,
which you can inspect to see the performance of your solar array.

For exact instructions on how to connect Renogy Rover RS232/RJ12 over an USB adapter to your Raspberry PI, please see
[NodeRenogy](https://github.com/mickwheelz/NodeRenogy).

## Advanced use

Use Grafana and the CSV plugin to read the CSV file and show charging data in charts.

TODO example of charts.

# Compiling

1. Install Java JDK 11+: `sudo apt install openjdk-11-jdk`
2.  You don't need to install Gradle itself - the `gradlew` script will download Gradle and all
    necessary files automatically, you only need to have an internet access.
3. Build with `./gradlew`. Find the binary in `build/bin/native/releaseExecutable`.
4. Copy the binary to your Raspberry PI.

Kotlin/Native at the moment doesn't support building on arm64: you'll get
"Could not find :kotlin-native-prebuilt-linux-aarch64:1.7.10" error if you try. See the
[getting 'unknown host target: linux aarch64'](https://discuss.kotlinlang.org/t/kotlin-native-getting-unknown-host-target-linux-aarch64-on-raspberry-pi-3b-ubuntu-21-04-aarch64/22874)
forum and also [KT-42445](https://youtrack.jetbrains.com/issue/KT-42445) for more details.

Therefore, you can not build this project on the Raspberry PI itself - you'll need to build this project
on an x86-64 machine (Intel/AMD) via a process called "cross-compiling" (that is, compiling a binary which runs on a CPU with an architecture different to the one performing the build).
The cross-compiling itself is handled automatically by the Kotlin plugin behind the scenes, there's nothing you need to do.
You only need to remember to build the project on a x86 machine.

You can use any major operating system to build this project. I'm using Ubuntu Linux x86-64 OS, however this
project builds on Windows and MacOS as well.

To compile for Raspberry PI, build on your host machine with:

* `./gradlew -Parm` for 64-bit OS
* `./gradlew -Parm32` for 32-bit OS

For other target platforms please see [Kotlin/Native Targets](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets).

# Running

Pass in the device file name of tty connected to the Renogy, e.g.

```bash
$ solar-controller-client.kexe /dev/ttyUSB0 --status
```

That will cause the app will only print status and quit. To continuously poll the device for data, run

```bash
$ solar-controller-client.kexe /dev/ttyUSB0 --csv log.csv --utc
```

The program will overwrite `status.json` file with the new data polled from the device;
the program will also start appending the information to `log.csv` so that you have historic data.

The status JSON example:
```json
{
    "systemInfo": {
        "maxVoltage": 24,
        "ratedChargingCurrent": 40,
        "ratedDischargingCurrent": 40,
        "productType": "Controller",
        "productModel": "RENOGY ROVER",
        "softwareVersion": "v1.2.3",
        "hardwareVersion": "v4.5.6",
        "serialNumber": "1501FFFF"
    },
    "powerStatus": {
        "batterySOC": 100,
        "batteryVoltage": 25.6,
        "chargingCurrentToBattery": 2.3,
        "batteryTemp": 23,
        "controllerTemp": 23,
        "loadVoltage": 0.0,
        "loadCurrent": 0.0,
        "loadPower": 0,
        "solarPanelVoltage": 60.2,
        "solarPanelCurrent": 4.2,
        "solarPanelPower": 252
    },
    "dailyStats": {
        "batteryMinVoltage": 25.0,
        "batteryMaxVoltage": 28.0,
        "maxChargingCurrent": 10.0,
        "maxDischargingCurrent": 10.0,
        "maxChargingPower": 240,
        "maxDischargingPower": 240,
        "chargingAmpHours": 100,
        "dischargingAmpHours": 100,
        "powerGeneration": 0.0,
        "powerConsumption": 0.0
    },
    "historicalData": {
        "daysUp": 20,
        "batteryOverDischargeCount": 1,
        "batteryFullChargeCount": 20,
        "totalChargingBatteryAH": 2000,
        "totalDischargingBatteryAH": 2000,
        "cumulativePowerGenerationWH": 2000.0,
        "cumulativePowerConsumptionWH": 2000.0
    },
    "status": {
        "streetLightOn": false,
        "streetLightBrightness": 0,
        "chargingState": "MpptChargingMode",
        "faults": [
            "ControllerTemperatureTooHigh"
        ]
    }
}
```

The CSV file contains the same fields, in a tabular form:
```csv
"DateTime","BatterySOC","BatteryVoltage","ChargingCurrentToBattery","BatteryTemp","ControllerTemp","SolarPanelVoltage","SolarPanelCurrent","SolarPanelPower","Daily.BatteryMinVoltage","Daily.BatteryMaxVoltage","Daily.MaxChargingCurrent","Daily.MaxChargingPower","Daily.ChargingAmpHours","Daily.PowerGeneration","Stats.DaysUp","Stats.BatteryOverDischargeCount","Stats.BatteryFullChargeCount","Stats.TotalChargingBatteryAH","Stats.CumulativePowerGenerationWH","ChargingState","Faults"
"2022-09-19T13:48:53Z",73,24.19,4.23,23,18,42.04,2.44,102,24.19,24.19,4.23,102,0.00,0.00,1,0,0,0,0.00,"MpptChargingMode",""
"2022-09-19T13:49:03Z",93,28.11,4.76,21,21,56.71,2.36,133,24.19,28.11,4.76,133,0.01,0.37,1,0,0,0,0.37,"MpptChargingMode",""
```

You can install Grafana and the Grafana CSV plugin, to visualize the CSV file as
a nice set of charts.

> WARNING: CSV file will get big over time: 0,5mb logged per day, 180mb file over a year.
> Grafana WILL spend lots of CPU to parse the CSV file. Only use CSV for initial testing;
> don't use for regular use.

## Sqlite

The CSV file tends to grow quite quickly. If you intend to use this tool with Grafana,
it's far better to output the data to the sqlite database. This requires the `sqlite3`
program installed, simply install it via `sudo apt install sqlite3`.

```bash
$ solar-controller-client.kexe /dev/ttyUSB0 --sqlite renogystats.db
```

The database looks like the following:
```
$ sqlite3 log.db "select * from log" -csv
1663607551,95,28.293612,0.0,20,19,55.478622,0.0,0,28.293612,28.293612,0.0,0,0.0,0.0,1,0,0,0,0.0,MpptChargingMode,
1663607561,78,26.666447,0.0,19,22,40.515778,0.0,0,26.666447,28.293612,0.0,0,0.0,0.0,1,0,0,0,0.0,MpptChargingMode,
1663607571,92,28.128708,0.0,23,19,41.469673,0.0,0,26.666447,28.293612,0.0,0,0.0,0.0,1,0,0,0,0.0,MpptChargingMode,
```

The following columns are available in the `log` database table:
```
0|DateTime|integer|1||1
1|BatterySOC|integer|1||0
2|BatteryVoltage|real|1||0
3|ChargingCurrentToBattery|real|1||0
4|BatteryTemp|int|1||0
5|ControllerTemp|int|1||0
6|SolarPanelVoltage|real|1||0
7|SolarPanelCurrent|real|1||0
8|SolarPanelPower|int|1||0
9|Daily_BatteryMinVoltage|real|1||0
10|Daily_BatteryMaxVoltage|real|1||0
11|Daily_MaxChargingCurrent|real|1||0
12|Daily_MaxChargingPower|int|1||0
13|Daily_ChargingAmpHours|real|1||0
14|Daily_PowerGeneration|real|1||0
15|Stats_DaysUp|int|1||0
16|Stats_BatteryOverDischargeCount|int|1||0
17|Stats_BatteryFullChargeCount|int|1||0
18|Stats_TotalChargingBatteryAH|int|1||0
19|Stats_CumulativePowerGenerationWH|real|1||0
20|ChargingState|text|0||0
21|Faults|text|0||0
```

Use the [Sqlite Grafana plugin](https://grafana.com/grafana/plugins/frser-sqlite-datasource/)
to allow Grafana to read the Sqlite database.

## Dummy Renogy Device

Use `dummy` instead
of the device name. This will create a dummy renogy device and poll data off it:

```bash
$ solar-controller-client.kexe dummy
```
