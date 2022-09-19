@file:OptIn(ExperimentalUnsignedTypes::class)

import utils.*

class RenogyModbusClient(val io: IO, val deviceAddress: Byte = 0x01) : RenogyClient {
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
        io.writeFully(request)

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
    override fun getSystemInfo(): SystemInfo {
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
        val chargingAmpHours: Float = result.getUShortHiLoAt(12).toFloat()
        val dischargingAmpHours: Float = result.getUShortHiLoAt(14).toFloat()
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

    override fun getAllData(cachedSystemInfo: SystemInfo?): RenogyData = RenogyData(
        cachedSystemInfo ?: getSystemInfo(),
        getPowerStatus(),
        getDailyStats(),
        getHistoricalData(),
        getStatus()
    )

    override fun toString(): String =
        "RenogyModbusClient(io=$io, deviceAddress=$deviceAddress)"

    companion object {
        private val COMMAND_READ_REGISTER: Byte = 0x03
    }
}
