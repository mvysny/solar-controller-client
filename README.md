# Solar Controller Client

Kotlin native app which communicate with Renogy Rover 40A over a RS232 serial port, using the Rover Modbus protocol.

Supposed to be running on Raspberry PI. Will periodically append newest data to a CSV file.

TODO: work in progress.

For connecting Renogy Rover RS232/RJ12 over an USB adapter to your Raspberry PI, please see
[NodeRenogy](https://github.com/mickwheelz/NodeRenogy).

## Compiling

Build with `./gradlew`. Find the binary in `build/bin/native/releaseExecutable`.

## Running

Pass in the device file name of tty connected to the Renogy, e.g.

```bash
$ solar-controller-client.kexe /dev/ttyUSB0
```
