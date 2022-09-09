@file:OptIn(ExperimentalUnsignedTypes::class)

import crc.CRC16Modbus
import crc.crcOf

class RenogyModbusClient(val io: IO, val deviceAddress: Byte = 0x01) {
    init {
        require(deviceAddress in 0..0xf7) { "$deviceAddress: Device address must be 0x01..0xf7, 0x00 is a broadcast address to which all slaves respond but do not return commands" }
    }

    /**
     * Performs the ReadRegister call and returns the data returned. Internal, don't use.
     */
    fun readRegister(startAddress: UShort, noOfReadBytes: UShort): ByteArray {
        val noOfReadWords = (noOfReadBytes / 2.toUShort()).toUShort()
        require(noOfReadWords in 0x1.toUShort()..0x7D.toUShort()) { "$noOfReadWords: must be 0x0001..0x007D" }

        // prepare request
        val request = byteArrayOf(deviceAddress, COMMAND_READ_REGISTER, startAddress.hibyte, startAddress.lobyte, noOfReadWords.hibyte, noOfReadWords.lobyte, 0, 0)
        val crc = CRC16Modbus()
        crc.update(request, 0, 6)
        request.setUShortAt(6, crc.crc) // for CRC, low byte is sent first, then the high byte.
        io.write(request)

        // read response
        val responseHeader = io.readBytes(3)
        if (responseHeader[0] != deviceAddress) {
            throw RenogyException("Invalid response: expected deviceAddress $deviceAddress but got ${responseHeader[0]}")
        }
        if (responseHeader[1] == 0x83.toByte()) {
            // error response. First verify checksum.
            verifyCRC(crcOf(responseHeader), io.readBytes(2))
            throw RenogyException.fromCode(responseHeader[2])
        }
        if (responseHeader[1] != 0x03.toByte()) {
            throw RenogyException("Unexpected response code: expected 3 but got ${responseHeader[1]}")
        }
        // normal response. Read the data.
        val dataLength = responseHeader[2].toUByte()
        if (dataLength !in 1.toUByte()..0xFA.toUByte()) {
            throw RenogyException("$dataLength: dataLength must be 0x01..0xFA")
        }
        val data = io.readBytes(dataLength.toInt())
        // verify the CRC
        verifyCRC(crcOf(responseHeader, data), io.readBytes(2))

        require(dataLength.toUShort() == noOfReadBytes) { "$dataLength: the call was expected to return $noOfReadBytes bytes" }

        // all OK. Return the response
        return data
    }

    private fun verifyCRC(actual: UShort, expected: ByteArray) {
        require(expected.size == 2) { "${expected.toHex()}: must be of size 2" }
        // for CRC, low byte is sent first, then the high byte.
        val expectedUShort = expected.getUShortAt(0)
        if (actual != expectedUShort) {
            throw RenogyException("Checksum mismatch: expected ${expectedUShort.toHex()} but got ${actual.toHex()}")
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
     * Retrieves the current status of the device, e.g. current voltage on
     * the solar panels.
     */
    fun getPowerStatus(): PowerStatus {
        val result = readRegister(0x0100.toUShort(), 20.toUShort())
        val batterySOC = result.getUShortHiLoAt(0)
        val batteryVoltage = result.getUShortHiLoAt(2).toFloat() / 10
        val chargingCurrentToBattery = result.getUShortHiLoAt(4).toFloat() / 100
        val batteryTemp = result[7].toInt()
        val controllerTemp = result[6].toInt()
        val loadVoltage = result.getUShortHiLoAt(8).toFloat() / 10
        val loadCurrent = result.getUShortHiLoAt(10).toFloat() / 100
        val loadPower = result.getUShortHiLoAt(12)
        val solarPanelVoltage = result.getUShortHiLoAt(14).toFloat() / 10
        val solarPanelCurrent = result.getUShortHiLoAt(16).toFloat() / 100
        val solarPanelPower = result.getUShortHiLoAt(18)
        return PowerStatus(batterySOC, batteryVoltage, chargingCurrentToBattery, batteryTemp, controllerTemp, loadVoltage, loadCurrent, loadPower, solarPanelVoltage, solarPanelCurrent, solarPanelPower)
    }

    /**
     * Returns the daily statistics.
     */
    fun getDailyStats(): DailyStats {
        val result = readRegister(0x010B.toUShort(), 20.toUShort())
        val batteryMinVoltage: Float = result.getUShortHiLoAt(0).toFloat() / 10
        val batteryMaxVoltage: Float = result.getUShortHiLoAt(2).toFloat() / 10
        val maxChargingCurrent: Float = result.getUShortHiLoAt(4).toFloat() / 100
        val maxDischargingCurrent: Float = result.getUShortHiLoAt(6).toFloat() / 100
        val maxChargingPower: UShort = result.getUShortHiLoAt(8)
        val maxDischargingPower: UShort = result.getUShortHiLoAt(10)
        val chargingAmpHours: UShort = result.getUShortHiLoAt(12)
        val dischargingAmpHours: UShort = result.getUShortHiLoAt(14)
        val powerGeneration: Float = result.getUShortHiLoAt(16).toFloat() / 10
        val powerConsumption: Float = result.getUShortHiLoAt(18).toFloat() / 10
        return DailyStats(batteryMinVoltage, batteryMaxVoltage, maxChargingCurrent, maxDischargingCurrent, maxChargingPower, maxDischargingPower, chargingAmpHours, dischargingAmpHours, powerGeneration, powerConsumption)
    }

    companion object {
        private val COMMAND_READ_REGISTER: Byte = 0x03
    }
}

/**
 * The daily statistics.
 * @param batteryMinVoltage Battery's min. voltage of the current day, V
 * @param batteryMaxVoltage Battery's max. voltage of the current day, V
 * @param maxChargingCurrent Max. charging current of the current day, A
 * @param maxDischargingCurrent Max. discharging current of the current day, A
 * @param maxChargingPower Max. charging power of the current day, W
 * @param maxDischargingPower Max. discharging power of the current day, W
 * @param chargingAmpHours Charging amp-hrs of the current day, AH
 * @param dischargingAmpHours Discharging amp-hrs of the current day, AH
 * @param powerGeneration Power generation of the current day, WH
 * @param powerConsumption Power generation of the current day, WH
 */
data class DailyStats(
    val batteryMinVoltage: Float,
    val batteryMaxVoltage: Float,
    val maxChargingCurrent: Float,
    val maxDischargingCurrent: Float,
    val maxChargingPower: UShort,
    val maxDischargingPower: UShort,
    val chargingAmpHours: UShort,
    val dischargingAmpHours: UShort,
    val powerGeneration: Float,
    val powerConsumption: Float
) {
    override fun toString(): String {
        return "DailyStats(batteryMinVoltage=$batteryMinVoltage V, batteryMaxVoltage=$batteryMaxVoltage V, maxChargingCurrent=$maxChargingCurrent A, maxDischargingCurrent=$maxDischargingCurrent A, maxChargingPower=$maxChargingPower W, maxDischargingPower=$maxDischargingPower W, chargingAmpHours=$chargingAmpHours AH, dischargingAmpHours=$dischargingAmpHours AH, powerGeneration=$powerGeneration WH, powerConsumption=$powerConsumption WH)"
    }
}

/**
 * @param batterySOC Current battery capacity value (state of charge), 0..100%
 * @param batteryVoltage battery voltage in V
 * @param chargingCurrentToBattery charging current (to battery), A
 * @param batteryTemp battery temperature in °C
 * @param controllerTemp controller temperature in °C
 * @param loadVoltage Street light (load) voltage in V
 * @param loadCurrent Street light (load) current in A
 * @param loadPower Street light (load) power, in W
 * @param solarPanelVoltage solar panel voltage, in V
 * @param solarPanelCurrent Solar panel current (to controller), in A
 * @param solarPanelPower charging power, in W
 */
data class PowerStatus(
    val batterySOC: UShort,
    val batteryVoltage: Float,
    val chargingCurrentToBattery: Float,
    val batteryTemp: Int,
    val controllerTemp: Int,
    val loadVoltage: Float,
    val loadCurrent: Float,
    val loadPower: UShort,
    val solarPanelVoltage: Float,
    val solarPanelCurrent: Float,
    val solarPanelPower: UShort
) {
    override fun toString(): String {
        return "PowerStatus(batterySOC=$batterySOC%, batteryVoltage=$batteryVoltage V, chargingCurrentToBattery=$chargingCurrentToBattery A, batteryTemp=$batteryTemp°C, controllerTemp=$controllerTemp°C, loadVoltage=$loadVoltage V, loadCurrent=$loadCurrent A, loadPower=$loadPower W, solarPanelVoltage=$solarPanelVoltage V, solarPanelCurrent=$solarPanelCurrent A, solarPanelPower=$solarPanelPower W)"
    }
}

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
        return "SystemInfo(maxVoltage=$maxVoltage V, ratedChargingCurrent=$ratedChargingCurrent A, ratedDischargingCurrent=$ratedDischargingCurrent A, productType=$productType, productModel=$productModel, softwareVersion=$softwareVersion, hardwareVersion=$hardwareVersion, serialNumber=$serialNumber)"
    }
}

/**
 * Thrown when Renogy returns a failure.
 * @param code the error code as received from Renogy. See [fromCode] for a list of
 * defined error codes. May be null if thrown because the response was mangled.
 */
class RenogyException(message: String, val code: Byte? = null) : Exception(message) {
    companion object {
        fun fromCode(code: Byte): RenogyException {
            val message = when(code) {
                0x01.toByte() -> "Function code not supported"
                0x02.toByte() -> "PDU start address is not correct or PDU start address + data length"
                0x03.toByte() -> "Data length in reading or writing register is too large"
                0x04.toByte() -> "Client fails to read or write register"
                0x05.toByte() -> "Data check code sent by server is not correct"
                else -> "Unknown"
            }
            return RenogyException("0x${code.toHex()}: $message", code)
        }
    }
}
