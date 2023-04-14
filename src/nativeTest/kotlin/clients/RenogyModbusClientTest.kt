package clients

import utils.*
import kotlin.test.Test
import kotlin.test.expect
import kotlin.test.fail

class RenogyModbusClientTest {
    @Test
    fun readRegister000ANormalResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll("010302181e324c")
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0A, 0x02)
        buffer.expectWrittenBytes("0103000a0001a408")
        expect("181e") { response.toHex() }
    }

    @Test
    fun readRegister000AErrorResponse() {
        val buffer = Buffer()
        buffer.toReturn.addAll("018302c0f1")
        val client = RenogyModbusClient(buffer)
        try {
            client.readRegister(0x0A, 0x02)
            fail("Expected to fail with clients.RenogyException")
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
        buffer.toReturn.addAll("010310202020204d5434383330202020202020ee98")
        val client = RenogyModbusClient(buffer)
        val response = client.readRegister(0x0C, 16)
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
        expect("""{"systemInfo":{"maxVoltage":24,"ratedChargingCurrent":40,"ratedDischargingCurrent":40,"productType":"Controller","productModel":"RENOGY ROVER","softwareVersion":"v1.2.3","hardwareVersion":"v4.5.6","serialNumber":"1501FFFF"},"powerStatus":{"batterySOC":100,"batteryVoltage":25.6,"chargingCurrentToBattery":2.3,"batteryTemp":23,"controllerTemp":23,"loadVoltage":0.0,"loadCurrent":0.0,"loadPower":0,"solarPanelVoltage":60.2,"solarPanelCurrent":4.2,"solarPanelPower":252},"dailyStats":{"batteryMinVoltage":25.0,"batteryMaxVoltage":28.0,"maxChargingCurrent":10.0,"maxDischargingCurrent":10.0,"maxChargingPower":240,"maxDischargingPower":240,"chargingAh":100,"dischargingAh":100,"powerGenerationWh":0,"powerConsumptionWh":0},"historicalData":{"daysUp":20,"batteryOverDischargeCount":1,"batteryFullChargeCount":20,"totalChargingBatteryAH":2000,"totalDischargingBatteryAH":2000,"cumulativePowerGenerationWH":2000,"cumulativePowerConsumptionWH":2000},"status":{"streetLightOn":false,"streetLightBrightness":0,"chargingState":"MpptChargingMode","faults":["ControllerTemperatureTooHigh"]}}""") {
            dummyRenogyData.toJson(false)
        }
    }

    @Test
    fun testReadDailyStats() {
        val buffer = Buffer()
        // The 4th and 5th bytes 0070H indicate the current day's min. battery voltage: 0070H * 0.1 = 112 * 0.1 = 11.2V
        // The 6th and 7th bytes 0084H indicate the current day's max. battery voltage: 0084H * 0.1 = 132 * 0.1 = 13.2V
        // The 8th and 9th bytes 00D8H indicate the current day's max. charging current: 00D8H * 0.01 = 216 * 0.01 = 2.16V
        // then max discharge current: 0
        // then max charging power: 10
        // max discharging power: 0
        // 0608H are the current day's charging amp-hrs (decimal 1544AH);
        // 0810H are the current day's discharging amp-hrs (decimal 2064AH)
        buffer.toReturn.addAll("0103140070008400d80000000a00000608081000700084ebde")
        val client = RenogyModbusClient(buffer)
        val dailyStats = client.getDailyStats()
        buffer.expectWrittenBytes("0103010b000ab5f3")
        expect(
            DailyStats(11.2f, 13.2f, 2.16f, 0f, 10.toUShort(), 0.toUShort(), 1544.toUShort(), 2064.toUShort(), 112.toUShort(), 132.toUShort())
        ) { dailyStats }
    }
}

val dummySystemInfo = SystemInfo(24, 40, 40, ProductType.Controller, "RENOGY ROVER", "v1.2.3", "v4.5.6", "1501FFFF")
val dummyPowerStatus = PowerStatus(100.toUShort(), 25.6f, 2.3f, 23, 23, 0f, 0f, 0.toUShort(), 60.2f, 4.2f, (60.2f * 4.2f).toInt().toUShort())
val dummyDailyStats = DailyStats(25.0f, 28.0f, 10.0f, 10.0f, 240.toUShort(), 240.toUShort(), 100.toUShort(), 100.toUShort(), 0.toUShort(), 0.toUShort())
val dummyHistoricalData = HistoricalData(20.toUShort(), 1.toUShort(), 20.toUShort(), 2000.toUInt(), 2000.toUInt(), 2000.toUInt(), 2000.toUInt())
val dummyStatus = RenogyStatus(false, 0.toUByte(), ChargingState.MpptChargingMode, setOf(
    ControllerFaults.ControllerTemperatureTooHigh))
val dummyRenogyData = RenogyData(
    dummySystemInfo,
    dummyPowerStatus,
    dummyDailyStats,
    dummyHistoricalData,
    dummyStatus
)
