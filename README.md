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
$ solar-controller-client.kexe /dev/ttyUSB0
```

At the moment the app will only print status and quit. Development is ongoing.

# Further development

Tasks to do:

* Add command-line parameters:
  * `--status` will simply print status and quit
  * default mode will periodically watch controller and add rows to `log.csv`; it will also dump current state into `state.csv`.
  * `--logfile` appends status to file other than the default `log.csv`
  * `--statefile` overwrites status to file other than the default `state.csv`
  * `--pollinginterval` in seconds: how frequently to poll the controller for data, defaults to 10
  * `--help`, `-h` prints this help
  * Required parameter: the serial device name to listen on

TODO:

* finalize the format of the CSV
* mimic JSON from https://github.com/mickwheelz/NodeRenogy
* Use kotlinx-datetime to format date+time
* Respond to signals and CTRL+C and terminate cleanly, closing the pipe
* Also optionally dump a HTML file which can be served over nginx
