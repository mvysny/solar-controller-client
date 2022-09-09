# Solar Controller Client

Kotlin native app which communicate with Renogy Rover 40A over a RS232 serial port, using the Rover Modbus protocol.

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

Build with `./gradlew`. Find the binary in `build/bin/native/releaseExecutable`.

To compile for Raspberry PI, you have to compile for ARM. Edit the `build.gradle.kts`
file and change the `val nativeTarget=` to:

* 64-bit OS: `val nativeTarget = linuxArm64("native")`
* 32-bit OS: `val nativeTarget = linuxArm32Hfp("native")`

For other target platforms please see [Kotlin/Native Targets](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets).

## Running

Pass in the device file name of tty connected to the Renogy, e.g.

```bash
$ solar-controller-client.kexe /dev/ttyUSB0
```

## Further development

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
