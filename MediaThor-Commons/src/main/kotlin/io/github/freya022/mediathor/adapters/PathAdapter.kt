package io.github.freya022.mediathor.adapters

import com.google.gson.*
import java.lang.reflect.Type
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object PathAdapter : JsonDeserializer<Path>, JsonSerializer<Path> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Path =
        Path(json.asString)

    override fun serialize(src: Path, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src.absolutePathString())
}