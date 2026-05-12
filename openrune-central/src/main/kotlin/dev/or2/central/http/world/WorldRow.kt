package dev.or2.central.http.world

data class WorldRow(
    val worldId: Int,
    val flags: String,
    val host: String,
    val activity: String,
    val location: Int,
    val population: Int,
) {
    val properties: Int
        get() = WorldFlag.maskFromCsv(flags)
}
