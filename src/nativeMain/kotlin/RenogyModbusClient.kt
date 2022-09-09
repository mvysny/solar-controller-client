import crc.CRC16Modbus

class RenogyModbusClient(val io: IO, val deviceAddress: Byte = 0x01) {
    init {
        require(deviceAddress in 0..0xf7) { "$deviceAddress: Device address must be 0x01..0xf7, 0x00 is a broadcast address to which all slaves respond but do not return commands" }
    }

    /**
     * Performs the ReadRegister call and returns the data returned. Internal, don't use.
     */
    fun readRegister(startAddress: UShort, noOfReadWords: UShort): ByteArray {
        require(noOfReadWords in 0x1.toUShort()..0x7D.toUShort()) { "$noOfReadWords: must be 0x0001..0x007D" }

        // prepare request
        val request = byteArrayOf(deviceAddress, 0x03, startAddress.hibyte, startAddress.lobyte, noOfReadWords.hibyte, noOfReadWords.lobyte, 0, 0)
        val crc = CRC16Modbus()
        crc.update(request, 0, 6)
        val crcBytes = crc.crcBytes
        request[6] = crcBytes[0]
        request[7] = crcBytes[1]
        io.write(request)

        // read response
        val response = io.readBytes(3)
        if (response[0] != deviceAddress) {
            throw RenogyException("Invalid response: expected deviceAddress $deviceAddress but got ${response[0]}")
        }
        if (response[1] == 0x83.toByte()) {
            // error response. First verify checksum.
            crc.reset()
            crc.update(response)
            verifyChecksum(crc.crcBytes, io.readBytes(2))
            throw RenogyException.fromCode(response[2])
        }
        if (response[1] != 0x03.toByte()) {
            throw RenogyException("Unexpected response code: expected 3 but got ${response[1]}")
        }
        // normal response. Read the data.
        val dataLength = response[2].toUByte()
        require(dataLength in 1.toUByte()..0xFA.toUByte()) { "$dataLength: must be 0x01..0xFA" }
        val data = io.readBytes(dataLength.toInt())
        // verify the CRC
        crc.reset()
        crc.update(response)
        crc.update(data)
        verifyChecksum(crc.crcBytes, io.readBytes(2))

        require(dataLength.toUShort() == (noOfReadWords * 2.toUShort()).toUShort()) { "$dataLength: the call was expected to return ${noOfReadWords * 2.toUShort()} bytes" }

        // all OK. Return the response
        return data
    }

    private fun verifyChecksum(actual: ByteArray, expected: ByteArray) {
        require(expected.size == 2) { "${expected.toHex()}: must be of size 2" }
        require(actual.size == 2) { "${actual.toHex()}: must be of size 2" }
        if (actual[0] != expected[0] || actual[1] != expected[1]) {
            throw RenogyException("Checksum mismatch: expected ${expected.toHex()} but got ${actual.toHex()}")
        }
    }

    /**
     * Retrieves the [SystemInfo] from the device.
     */
    fun getSystemInfo(): SystemInfo {
        var result = readRegister(0x0A.toUShort(), 2.toUShort())
        val maxVoltage = result[0].toInt()
        val ratedChargingCurrent = result[1].toInt()

        result = readRegister(0x0B.toUShort(), 2.toUShort())
        val ratedDischargingCurrent = result[0].toInt()
        val productType = result[1]

        result = readRegister(0x0C.toUShort(), 16.toUShort())
        val productModel = result.toAsciiString().trim()

        // software version/hardware version
        result = readRegister(0x0014.toUShort(), 8.toUShort())
        val softvareVersion = "V${result[1]}.${result[2]}.${result[3]}"
        val hardwareVersion = "V${result[5]}.${result[6]}.${result[7]}"

        // serial number
        result = readRegister(0x0018.toUShort(), 4.toUShort())
        val serialNumber = result.toHex()

        return SystemInfo(maxVoltage,ratedChargingCurrent, ratedDischargingCurrent, productType, productModel, softvareVersion, hardwareVersion, serialNumber)
    }

    /**
     * Retrieves the dynamic current status of the device, e.g. current voltage on
     * the solar panels.
     */
    fun getStatus() {

    }
}

fun ByteArray.toAsciiString() = map { it.toInt().toChar() } .toCharArray().concatToString()

/**
 * The static system information: hw/sw version, specs etc.
 * @property maxVoltage max. voltage supported by the system: 12V/24V/36V/48V/96V; 0xFF=automatic recognition of system voltage
 * @property ratedChargingCurrent rated charging current in A: 10A/20A/30A/45A/60A
 * @property ratedDischargingCurrent rated discharging current, 10A/20A/30A/45A/60A
 * @property productType product type, 0=controller, 1=inverter
 * @property productModel the controller's model
 * @property softwareVersion Vmajor.minor.bugfix
 * @property hardwareVersion Vmajor.minor.bugfix
 * @property serialNumber e.g. `1501FFFF`
 */
data class SystemInfo(
    val maxVoltage: Int,
    val ratedChargingCurrent: Int,
    val ratedDischargingCurrent: Int,
    val productType: Byte,
    val productModel: String,
    val softwareVersion: String,
    val hardwareVersion: String,
    val serialNumber: String
) {
    override fun toString(): String {
        return "SystemInfo(maxVoltage=$maxVoltage V, ratedChargingCurrent=$ratedChargingCurrent A, ratedDischargingCurrent=$ratedDischargingCurrent A, productType=$productType, productModel=$productModel, softwareVersion=$softwareVersion, hardwareVersion=$hardwareVersion)"
    }
}

class RenogyException(message: String) : Exception(message) {
    companion object {
        fun fromCode(code: Byte): RenogyException = when(code) {
            0x01.toByte() -> RenogyException("0x01: Function code not supported")
            0x02.toByte() -> RenogyException("0x02: PDU start address is not correct or PDU start address + data length")
            0x03.toByte() -> RenogyException("0x03: Data length in reading or writing register is too large")
            0x04.toByte() -> RenogyException("0x04: Client fails to read or write register")
            0x05.toByte() -> RenogyException("0x05: Data check code sent by server is not correct")
            else -> RenogyException("$code: Unknown")
        }
    }
}
