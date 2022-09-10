# Solar Controller Client

Kotlin native app which communicate with Renogy Rover 40A over a RS232 serial port, using the Rover Modbus protocol.

Licensed under the MIT license.

## Intended use

Intended to be running on a Raspberry PI. The Raspberry PI needs to be connected over USB/RS232 adapter
to a RS232/RJ12 port of Renogy Rover. This program will periodically append newest data to a CSV file,
which you can inspect to see the performance of your solar array.

For exact instructions on how to connect Renogy Rover RS232/RJ12 over an USB adapter to your Raspberry PI, please see
[NodeRenogy](https://github.com/mickwheelz/NodeRenogy).

## Advanced use

TODO document how to use Apache/nginx to serve the CSV files. Maybe the app could dump a nice HTML file with all stats?
Optionally dump into JSON as well. Perhaps integration with Home Assistant? Over MQTT?

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
$ solar-controller-client.kexe /dev/ttyUSB0
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

TODO the example CSV file.

# Further development

Tasks to do:

* finalize the format of the CSV
* Also optionally dump a HTML file which can be served over python3
