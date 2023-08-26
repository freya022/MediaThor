package io.github.freya022.mediathor.record.watcher

import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration

typealias KeyframeIndex = Int
typealias KeyframeHash = String

class Clip(
    val path: Path,
    val group: ClipGroup,
    val createdAt: Instant,
    val duration: Duration,
    val keyframeIndexByHash: Map<KeyframeHash, KeyframeIndex>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Clip

        return path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun toString(): String {
        return "Clip(path=$path)"
    }
}