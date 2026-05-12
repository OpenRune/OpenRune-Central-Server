package dev.or2.central.http.world

enum class WorldFlag(
    val mask: Int,
    val runeliteTypeName: String? = null,
) {
    MEMBERS(0x1, "MEMBERS"),
    QUICKCHAT(0x2),
    PVPWORLD(0x4, "PVP"),
    LOOTSHARE(0x8),
    DEDICATEDACTIVITY(0x10),
    BOUNTYWORLD(0x20, "BOUNTY"),
    PVPARENA(0x40, "PVP_ARENA"),
    HIGHLEVELONLY_1500(0x80, "SKILL_TOTAL"),
    SPEEDRUN(0x100, "QUEST_SPEEDRUNNING"),
    EXISTINGPLAYERSONLY(0x200),
    EXTRAHARDWILDERNESS(0x400, "HIGH_RISK"),
    DUNGEONEERING(0x800),
    INSTANCE_SHARD(0x1000),
    RENTABLE(0x2000),
    LASTMANSTANDING(0x4000, "LAST_MAN_STANDING"),
    NEW_PLAYERS(0x8000),
    BETA_WORLD(0x10000, "BETA_WORLD"),
    STAFF_IP_ONLY(0x20000),
    HIGHLEVELONLY_2000(0x40000),
    HIGHLEVELONLY_2400(0x80000),
    VIPS_ONLY(0x100000),
    HIDDEN_WORLD(0x200000),
    LEGACY_ONLY(0x400000, "LEGACY_ONLY"),
    EOC_ONLY(0x800000, "EOC_ONLY"),
    BEHIND_PROXY(0x1000000),
    NOSAVE_MODE(0x2000000, "NOSAVE_MODE"),
    TOURNAMENT_WORLD(0x4000000, "TOURNAMENT"),
    FRESHSTART(0x8000000, "FRESH_START_WORLD"),
    HIGHLEVELONLY_1750(0x10000000),
    DEADMAN(0x20000000, "DEADMAN"),
    SEASONAL(0x40000000, "SEASONAL"),
    EXTERNAL_PARTNER_ONLY(0x80000000.toInt());

    companion object {
        fun combine(vararg flags: WorldFlag): Int = flags.fold(0) { acc, f -> acc or f.mask }

        private val runeliteOrderedFlags: List<WorldFlag> =
            listOf(
                MEMBERS,
                PVPWORLD,
                BOUNTYWORLD,
                PVPARENA,
                HIGHLEVELONLY_1500,
                SPEEDRUN,
                EXTRAHARDWILDERNESS,
                LASTMANSTANDING,
                BETA_WORLD,
                LEGACY_ONLY,
                EOC_ONLY,
                NOSAVE_MODE,
                TOURNAMENT_WORLD,
                FRESHSTART,
                DEADMAN,
                SEASONAL,
            )

        fun runeliteTypeNamesFromMask(mask: Int): List<String> =
            runeliteOrderedFlags.mapNotNull { flag ->
                if ((flag.mask and mask) == 0) {
                    null
                } else {
                    flag.runeliteTypeName
                }
            }

        fun maskFromCsv(csv: String): Int {
            val trimmed = csv.trim()
            if (trimmed.isEmpty()) {
                return 0
            }
            return trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.fold(0) { acc, token ->
                val key = token.uppercase()
                val flag =
                    entries.find { it.name == key }
                        ?: entries.find { it.runeliteTypeName?.uppercase() == key }
                        ?: throw IllegalArgumentException("Unknown worlds flag: $token")
                acc or flag.mask
            }
        }
    }
}
