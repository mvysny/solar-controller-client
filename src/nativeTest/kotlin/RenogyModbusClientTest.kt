import utils.Buffer
import utils.toHex
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class RenogyModbusClientTest {
    @Test
    fun readRegister000ANormalResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 3, 2, 0x18, 0x1E, 0x32, 0x4c))
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0A.toUShort(), 0x02.toUShort())
        buffer.expectWrittenBytes("0103000a0001a408")
        expect("181e") { response.toHex() }
    }

    @Test
    fun readRegister000AErrorResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 0x83.toByte(), 2, 0xc0.toByte(), 0xf1.toByte()))
        val client = RenogyModbusClient(buffer)
        try {
            client.readRegister(0x0A.toUShort(), 0x02.toUShort())
            fail("Expected to fail with RenogyException")
        } catch (e: RenogyException) {
            // okay
            expect("0x02: PDU start address is not correct or PDU start address + data length") {
                e.message
            }
        }
        buffer.expectWrittenBytes("0103000a0001a408")
    }

    @Test
    fun readRegister000CNormalResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll(listOf(1, 3, 0x10, 0x20, 0x20, 0x20, 0x20, 0x4D, 0x54, 0x34, 0x38, 0x33, 0x30, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0xEE.toByte(), 0x98.toByte()))
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0C.toUShort(), 16.toUShort())
        buffer.expectWrittenBytes("0103000c0008840f")
        expect("202020204d5434383330202020202020") { response.toHex() }
    }

    @Test
    fun testControllerFaults() {
        expect(setOf()) { ControllerFaults.fromModbus(0.toUInt()) }
        expect(setOf(ControllerFaults.PhotovoltaicInputSideShortCircuit, ControllerFaults.BatteryOverDischarge)) {
            ControllerFaults.fromModbus(0x01010000.toUInt())
        }
    }

    @Test
    fun toJson() {
        expect("""{"systemInfo":{"maxVoltage":24,"ratedChargingCurrent":40,"ratedDischargingCurrent":40,"productType":"Controller","productModel":"RENOGY ROVER","softwareVersion":"v1.2.3","hardwareVersion":"v4.5.6","serialNumber":"1501FFFF"},"powerStatus":{"batterySOC":100,"batteryVoltage":25.6,"chargingCurrentToBattery":2.3,"batteryTemp":23,"controllerTemp":23,"loadVoltage":0.0,"loadCurrent":0.0,"loadPower":0,"solarPanelVoltage":60.2,"solarPanelCurrent":4.2,"solarPanelPower":252},"dailyStats":{"batteryMinVoltage":25.0,"batteryMaxVoltage":28.0,"maxChargingCurrent":10.0,"maxDischargingCurrent":10.0,"maxChargingPower":240,"maxDischargingPower":240,"chargingAmpHours":100,"dischargingAmpHours":100,"powerGeneration":0.0,"powerConsumption":0.0},"historicalData":{"daysUp":20,"batteryOverDischargeCount":1,"batteryFullChargeCount":20,"totalChargingBatteryAH":2000,"totalDischargingBatteryAH":2000,"cumulativePowerGenerationWH":2000.0,"cumulativePowerConsumptionWH":2000.0},"status":{"streetLightOn":false,"streetLightBrightness":0,"chargingState":"MpptChargingMode","faults":["ControllerTemperatureTooHigh"]}}""") {
            dummyRenogyData.toJson(false)
        }
    }
}

val dummySystemInfo = SystemInfo(24, 40, 40, ProductType.Controller, "RENOGY ROVER", "v1.2.3", "v4.5.6", "1501FFFF")
val dummyPowerStatus = PowerStatus(100.toUShort(), 25.6f, 2.3f, 23, 23, 0f, 0f, 0.toUShort(), 60.2f, 4.2f, (60.2f * 4.2f).toInt().toUShort())
val dummyDailyStats = DailyStats(25.0f, 28.0f, 10.0f, 10.0f, 240.toUShort(), 240.toUShort(), 100.toUShort(), 100.toUShort(), 0f, 0f)
val dummyHistoricalData = HistoricalData(20.toUShort(), 1.toUShort(), 20.toUShort(), 2000.toUInt(), 2000.toUInt(), 2000f, 2000f)
val dummyStatus = RenogyStatus(false, 0.toUByte(), ChargingState.MpptChargingMode, setOf(ControllerFaults.ControllerTemperatureTooHigh))
val dummyRenogyData = RenogyData(dummySystemInfo, dummyPowerStatus, dummyDailyStats, dummyHistoricalData, dummyStatus)
