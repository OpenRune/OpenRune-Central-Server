package dev.or2.punishment



public object PunishmentKind {

    public const val BAN: String = "ban"

    public const val TEMP_BAN: String = "temp_ban"

    public const val MUTE: String = "mute"

    public const val TEMP_MUTE: String = "temp_mute"

    public const val LOCKED: String = "locked"

    public const val KICK: String = "kick"

}



public object PunishmentScope {

    public const val ACCOUNT: String = "account"

    public const val CHARACTER: String = "character"

}



public object PunishmentStatus {

    public const val ACTIVE: String = "active"

    public const val INACTIVE: String = "inactive"

    public const val SQUASHED: String = "squashed"

}

