import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

private val prettyPrintJson = Json { prettyPrint = true }

fun <T> toJson(serializer: SerializationStrategy<T>, value: T, prettyPrint: Boolean): String {
    val json: Json = if (prettyPrint) prettyPrintJson else Json
    return json.encodeToString(serializer, value)
}
