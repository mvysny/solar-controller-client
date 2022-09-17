import RenogyException.Companion.fromCode
import kotlinx.serialization.Serializable
import utils.toHex

interface RenogyClient {
    /**
     * Retrieves the [SystemInfo] from the device.
     */
    fun getSystemInfo(): SystemInfo

    /**
     * Retrieves all current data from a Renogy device. Usually [SystemInfo] is only
     * fetched once and then cached; it can be passed in as [cachedSystemInfo]
     * to avoid repeated retrieval.
     * @param cachedSystemInfo if not null, this information will not be fetched.
     * @throws RenogyException if the data retrieval fails
     */
    fun getAllData(cachedSystemInfo: SystemInfo? = null): RenogyData
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
 * @param batteryFullChargeCount Total number of battery full-charges.
 * @param totalChargingBatteryAH Total charging amp-hrs of the battery. mavi: does this refer to the nominal battery voltage (e.g. 24V) or actual battery voltage (e.g. 27V)?
 * @param totalDischargingBatteryAH Total discharging amp-hrs of the battery. mavi: probably only applicable to inverters, 0 for controller.
 * @param cumulativePowerGenerationWH cumulative power generation in WH. Probably only applies to controller, will be 0 for inverter.
 * @param cumulativePowerConsumptionWH cumulative power consumption in WH. mavi: probably only applicable to inverters, 0 for controller.
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
 * @param maxChargingCurrent Max. charging current of the current day, A. Probably applies to controller only. Does this refer to battery nominal voltage (e.g. 24V)?
 * @param maxDischargingCurrent Max. discharging current of the current day, A. mavi: probably only applies to inverter; will be 0 for controller. Probably refers to battery nominal voltage (e.g. 24V) or actual battery voltage?
 * @param maxChargingPower Max. charging power of the current day, W. mavi: probably only applies to controller; will be 0 for inverter.
 * @param maxDischargingPower Max. discharging power of the current day, W. mavi: probably only applies to inverter; will be 0 for controller.
 * @param chargingAmpHours Charging amp-hrs of the current day, AH. mavi: probably only applies to controller; will be 0 for inverter. Does this refer to nominal battery voltage (24V)?
 * @param dischargingAmpHours Discharging amp-hrs of the current day, AH. mavi: probably only applies to inverter; will be 0 for controller. Does this refer to nominal battery voltage (24V)?
 * @param powerGeneration Power generation of the current day, WH. Probably only applies to controller.
 * @param powerConsumption Power consumption of the current day, WH. Probably only applies to inverter.
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
        utils.toJson(this, prettyPrint)
}
