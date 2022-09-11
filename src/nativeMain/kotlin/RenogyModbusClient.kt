@file:OptIn(ExperimentalUnsignedTypes::class)

import kotlinx.serialization.Serializable
import utils.*

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
        var result = readRegister(0x0A.toUShort(), 4.toUShort())
        val maxVoltage = result[0].toInt()
        val ratedChargingCurrent = result[1].toInt()
        val ratedDischargingCurrent = result[2].toInt()
        val productTypeNum = result[3]
        val productType = ProductType.values().firstOrNull { it.modbusValue == productTypeNum }

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
        // @todo in modbus spec examples, there's no chargingCurrentToBattery, and
        // batteryTemp is a WORD at 0x102 and controllerTemp is a WORD at 0x103 - check!
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

    /**
     * Returns the historical data summary.
     */
    fun getHistoricalData(): HistoricalData {
        val result = readRegister(0x0115.toUShort(), 22.toUShort())
        val daysUp: UShort = result.getUShortHiLoAt(0)
        val batteryOverDischargeCount: UShort = result.getUShortHiLoAt(2)
        val batteryFullChargeCount: UShort = result.getUShortHiLoAt(4)
        val totalChargingBatteryAH: UInt = result.getUIntHiLoAt(6)
        val totalDischargingBatteryAH: UInt = result.getUIntHiLoAt(10)
        val cumulativePowerGenerationWH: Float = result.getUIntHiLoAt(14).toFloat() / 10
        val cumulativePowerConsumptionWH: Float = result.getUIntHiLoAt(18).toFloat() / 10
        return HistoricalData(daysUp, batteryOverDischargeCount, batteryFullChargeCount, totalChargingBatteryAH, totalDischargingBatteryAH, cumulativePowerGenerationWH, cumulativePowerConsumptionWH)
    }

    /**
     * Returns the current charging status and any current faults.
     */
    fun getStatus(): RenogyStatus {
        val result = readRegister(0x120.toUShort(), 6.toUShort())
        val streetLightOn = (result[0].toUByte() and 0x80.toUByte()) != 0.toUByte()
        val streetLightBrightness = result[0].toUByte() and 0x7F.toUByte()
        val chargingState = ChargingState.fromModbus(result[1].toUByte())
        val faultBits = result.getUIntHiLoAt(2)
        val faults = ControllerFaults.fromModbus(faultBits)
        return RenogyStatus(streetLightOn, streetLightBrightness, chargingState, faults)
    }

    fun getAllData(cachedSystemInfo: SystemInfo? = null): RenogyData = RenogyData(
        cachedSystemInfo ?: getSystemInfo(),
        getPowerStatus(),
        getDailyStats(),
        getHistoricalData(),
        getStatus()
    )

    companion object {
        private val COMMAND_READ_REGISTER: Byte = 0x03
    }
}

/**
 * @param streetLightOn Whether the street light is on or off
 * @param streetLightBrightness street light brightness value, 0..100 in %
 * @param chargingState charging state (if known)
 * @param faults current faults, empty if none.
 */
@Serializable
data class RenogyStatus(
    val streetLightOn: Boolean,
    val streetLightBrightness: UByte,
    val chargingState: ChargingState?,
    val faults: Set<ControllerFaults>
)

enum class ChargingState(val value: UByte) {
    ChargingDeactivated(0.toUByte()),
    ChargingActivated(1.toUByte()),
    MpptChargingMode(2.toUByte()),
    EqualizingChargingMode(3.toUByte()),
    BoostChargingMode(4.toUByte()),
    FloatingChargingMode(5.toUByte()),
    /**
     * Current limiting (overpower)
     */
    CurrentLimiting(6.toUByte()),
    ;
    companion object {
        fun fromModbus(value: UByte): ChargingState? =
            values().firstOrNull { it.value == value }
    }
}

enum class ControllerFaults(val bit: Int) {
    CircuitChargeMOSShort(30),
    AntiReverseMOSShort(29),
    SolarPanelReverselyConnected(28),
    SolarPanelWorkingPointOverVoltage(27),
    SolarPanelCounterCurrent(26),
    PhotovoltaicInputSideOverVoltage(25),
    PhotovoltaicInputSideShortCircuit(24),
    PhotovoltaicInputOverpower(23),
    AmbientTemperatureTooHigh(22),
    ControllerTemperatureTooHigh(21),
    LoadOverpowerOrLoadOverCurrent(20),
    LoadShortCircuit(19),
    BatteryUnderVoltageWarning(18),
    BatteryOverVoltage(17),
    BatteryOverDischarge(16),
    ;

    private fun isPresent(modbusValue: UInt): Boolean {
        val probe = 1.shl(bit)
        return (modbusValue.toInt() and probe) != 0
    }

    companion object {
        fun fromModbus(modbusValue: UInt): Set<ControllerFaults> =
            values().filter { it.isPresent(modbusValue) } .toSet()
    }
}

/**
 * Historical data summary
 * @param daysUp Total number of operating days
 * @param batteryOverDischargeCount Total number of battery over-discharges
 * @param batteryFullChargeCount Total number of battery full-charges
 * @param totalChargingBatteryAH Total charging amp-hrs of the battery
 * @param totalDischargingBatteryAH Total discharging amp-hrs of the battery
 * @param cumulativePowerGenerationWH cumulative power generation in WH
 * @param cumulativePowerConsumptionWH cumulative power consumption in WH
 */
@Serializable
data class HistoricalData(
    val daysUp: UShort,
    val batteryOverDischargeCount: UShort,
    val batteryFullChargeCount: UShort,
    val totalChargingBatteryAH: UInt,
    val totalDischargingBatteryAH: UInt,
    val cumulativePowerGenerationWH: Float,
    val cumulativePowerConsumptionWH: Float
)

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
 * @param powerConsumption Power consumption of the current day, WH
 */
@Serializable
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
@Serializable
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
 * @property productType product type
 * @property productModel the controller's model
 * @property softwareVersion Vmajor.minor.bugfix
 * @property hardwareVersion Vmajor.minor.bugfix
 * @property serialNumber serial number, 4 bytes formatted as a hex string, e.g. `1501FFFF`,
 * indicating it's the 65535th (hexadecimal FFFFH) unit produced in Jan. of 2015.
 */
@Serializable
data class SystemInfo(
    val maxVoltage: Int,
    val ratedChargingCurrent: Int,
    val ratedDischargingCurrent: Int,
    val productType: ProductType?,
    val productModel: String,
    val softwareVersion: String,
    val hardwareVersion: String,
    val serialNumber: String
) {
    override fun toString(): String {
        return "SystemInfo(maxVoltage=$maxVoltage V, ratedChargingCurrent=$ratedChargingCurrent A, ratedDischargingCurrent=$ratedDischargingCurrent A, productType=$productType, productModel=$productModel, softwareVersion=$softwareVersion, hardwareVersion=$hardwareVersion, serialNumber=$serialNumber)"
    }
}

enum class ProductType(val modbusValue: Byte) {
    Controller(0),
    Inverter(1),
    ;
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

/**
 * Contains all data which can be pulled from the Renogy device.
 */
@Serializable
data class RenogyData(
    val systemInfo: SystemInfo,
    val powerStatus: PowerStatus,
    val dailyStats: DailyStats,
    val historicalData: HistoricalData,
    val status: RenogyStatus
) {
    fun toJson(prettyPrint: Boolean = true): String =
        utils.toJson(serializer(), this, prettyPrint)
}
