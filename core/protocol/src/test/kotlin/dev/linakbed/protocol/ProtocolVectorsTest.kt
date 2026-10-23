package dev.linakbed.protocol

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * `linak-protocol-vectors.json`(ADR-006, 언어 독립 기준)과 코드가 **양방향으로** 일치하는지 검사한다.
 * 파일에 있는 값이 코드와 다르거나, 코드에 있는 명령이 파일에 없으면 실패한다.
 */
class ProtocolVectorsTest {
    private val vectors: JsonObject =
        Json
            .parseToJsonElement(
                checkNotNull(javaClass.getResource("/linak-protocol-vectors.json")) { "vectors file missing" }.readText(),
            ).jsonObject

    private val JsonElement.string get() = jsonPrimitive.content

    @Test
    fun `GATT UUIDs match LinakGatt`() {
        val gatt = vectors.getValue("gatt").jsonObject

        assertThat(
            mapOf(
                "controlService" to LinakGatt.SERVICE_CONTROL,
                "controlCharacteristic" to LinakGatt.CHAR_CONTROL,
                "sensorService" to LinakGatt.SERVICE_SENSOR,
                "headHeightCharacteristic" to LinakGatt.CHAR_HEAD,
                "feetHeightCharacteristic" to LinakGatt.CHAR_FEET,
            ),
        ).isEqualTo(gatt.mapValues { (_, value) -> UUID.fromString(value.string) })
    }

    @Test
    fun `every command in the file encodes to the listed bytes`() {
        vectorCommands().forEach { (command, hex) ->
            assertWithMessage("bytes for %s", command).that(LinakCommandEncoder.encode(command).toHex()).isEqualTo(hex)
        }
    }

    @Test
    fun `file and code list exactly the same commands, each once`() {
        val fileCommands = vectorCommands().map { it.first }

        assertThat(fileCommands).containsNoDuplicates()
        assertThat(fileCommands).containsExactlyElementsIn(BedCommand.ALL)
    }

    @Test
    fun `sensor calibration in the file matches the default`() {
        val calibration =
            vectors
                .getValue("sensor")
                .jsonObject
                .getValue("calibration")
                .jsonObject

        assertThat(
            SensorCalibration(
                headDivisor = calibration.getValue("headDivisor").jsonPrimitive.int,
                feetDivisor = calibration.getValue("feetDivisor").jsonPrimitive.int,
                scale = calibration.getValue("scale").jsonPrimitive.double,
                speedDivisor = calibration.getValue("speedDivisor").jsonPrimitive.double,
            ),
        ).isEqualTo(SensorCalibration.DEFAULT)
    }

    @Test
    fun `every sensor case decodes as listed`() {
        val cases =
            vectors
                .getValue("sensor")
                .jsonObject
                .getValue("cases")
                .jsonArray
                .map { it.jsonObject }
        assertThat(cases).isNotEmpty()

        cases.forEach { case ->
            val actuator = Actuator.valueOf(case.getValue("actuator").string.uppercase())
            val payload = case.getValue("payload").string
            val reading = LinakSensorDecoder.decode(actuator, payload.hexToBytes(), SensorCalibration.DEFAULT)
            val label = "$actuator payload=\"$payload\""

            if (case["invalid"]?.jsonPrimitive?.booleanOrNull == true) {
                assertWithMessage(label).that(reading).isNull()
                return@forEach
            }
            checkNotNull(reading) { "$label should decode" }
            assertWithMessage("raw of %s", label).that(reading.raw).isEqualTo(case.getValue("raw").jsonPrimitive.int)
            assertWithMessage("cm of %s", label).that(reading.cm).isWithin(TOLERANCE).of(case.getValue("cm").jsonPrimitive.double)
            val expectedSpeed = case.getValue("speedCmPerMin").jsonPrimitive.doubleOrNull
            if (expectedSpeed == null) {
                assertWithMessage("speed of %s", label).that(reading.speedCmPerMin).isNull()
            } else {
                assertWithMessage("speed of %s", label).that(reading.speedCmPerMin).isWithin(TOLERANCE).of(expectedSpeed)
            }
        }
    }

    @Test
    fun `profile timings and write type match the default profile`() {
        val profile = vectors.getValue("profile").jsonObject
        val default = ProtocolProfile.DEFAULT

        assertThat(timing(profile.getValue("moveTiming"))).isEqualTo(default.moveTiming)
        assertThat(timing(profile.getValue("presetTiming"))).isEqualTo(default.presetTiming)
        assertThat(timing(profile.getValue("lightTiming"))).isEqualTo(default.lightTiming)
        assertThat(profile.getValue("writeWithResponse").jsonPrimitive.boolean).isEqualTo(default.writeWithResponse)
    }

    private fun vectorCommands(): List<Pair<BedCommand, String>> =
        vectors.getValue("commands").jsonArray.map { element ->
            val entry = element.jsonObject
            command(entry) to entry.getValue("bytes").string
        }

    private fun command(entry: JsonObject): BedCommand {
        fun slot() = checkNotNull(PresetSlot.fromNumber(entry.getValue("slot").jsonPrimitive.int)) { "bad slot in $entry" }
        return when (val type = entry.getValue("command").string) {
            "move" ->
                BedCommand.Move(
                    Actuator.valueOf(entry.getValue("actuator").string.uppercase()),
                    Direction.valueOf(entry.getValue("direction").string.uppercase()),
                )
            "stop" -> BedCommand.Stop
            "recallPreset" -> BedCommand.RecallPreset(slot())
            "savePreset" -> BedCommand.SavePreset(slot())
            "light" -> BedCommand.Light(LightAction.valueOf(entry.getValue("action").string.uppercase()))
            else -> error("unknown command type in vectors: $type")
        }
    }

    private fun timing(element: JsonElement): CommandTiming {
        val timing = element.jsonObject
        return when (val type = (timing.getValue("type") as JsonPrimitive).content) {
            "oneShot" -> CommandTiming.OneShot
            "repeat" ->
                CommandTiming.Repeat(
                    timing
                        .getValue("intervalMs")
                        .jsonPrimitive.int.milliseconds,
                )
            else -> error("unknown timing type in vectors: $type")
        }
    }

    private companion object {
        // 벡터 파일의 cm 값은 소수점 9자리까지 적는다.
        const val TOLERANCE = 1e-6
    }
}
