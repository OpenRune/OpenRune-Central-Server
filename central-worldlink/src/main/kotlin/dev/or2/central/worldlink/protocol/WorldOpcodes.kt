package dev.or2.central.worldlink.protocol

object WorldOpcodes {
    /** ASCII "OR21" — world-link magic in WORLD_HELLO. */
    const val MAGIC: Int = 0x4F523231

    const val OP_WORLD_HELLO: Int = 1
    const val OP_HELLO_ACK: Int = 2
    const val OP_HELLO_REJECT: Int = 3

    const val OP_LOGIN: Int = 16
    const val OP_LOGIN_OK: Int = 17
    const val OP_LOGIN_FAIL: Int = 18

    const val OP_PUSH_SUBSCRIBE: Int = 19
    const val OP_PUSH_SUBSCRIBE_ACK: Int = 20

    const val OP_HEARTBEAT: Int = 48
    const val OP_HEARTBEAT_ACK: Int = 49

    const val OP_LOGOUT: Int = 64
    const val OP_LOGOUT_ACK: Int = 65

    const val OP_SERVER_REVOKE_LOGIN: Int = 80
    const val OP_SERVER_MUTE_UPDATE: Int = 81
    const val OP_SERVER_KICK: Int = 82
    const val OP_SERVER_REBOOT: Int = 84
    const val OP_SERVER_BROADCAST: Int = 85
    const val OP_SERVER_DISCORD_ID_SYNC: Int = 86
    const val OP_SERVER_DISPLAY_NAME_SYNC: Int = 87

    const val OP_GAME_DISCORD_LINK_PENDING: Int = 88
    const val OP_GAME_DISCORD_LINK_PENDING_OK: Int = 89
    const val OP_GAME_DISCORD_LINK_PENDING_FAIL: Int = 90
    const val OP_GAME_DISCORD_LINK_INVALIDATE: Int = 91
    const val OP_GAME_DISCORD_LINK_INVALIDATE_ACK: Int = 92

    const val GAME_DISCORD_LINK_PENDING_FAIL_BAD_FRAME: Int = 1
    const val GAME_DISCORD_LINK_PENDING_FAIL_ALREADY_LINKED: Int = 2
    const val GAME_DISCORD_LINK_PENDING_FAIL_DISCORD_NOT_FOUND: Int = 3
    const val GAME_DISCORD_LINK_PENDING_FAIL_UNAVAILABLE: Int = 4
    const val GAME_DISCORD_LINK_USERNAME_MAX_UTF8: Int = 128

    const val OP_WORLD_PM_RELAY: Int = 96
    const val OP_WORLD_FRIEND_ADD: Int = 97
    const val OP_WORLD_FRIEND_DEL: Int = 98
    const val OP_WORLD_IGNORE_ADD: Int = 99
    const val OP_WORLD_IGNORE_DEL: Int = 100
    const val OP_WORLD_CHAT_FILTERS: Int = 101

    const val OP_WORLD_SOCIAL_OK: Int = 102
    const val OP_WORLD_SOCIAL_FAIL: Int = 103
    const val OP_SERVER_PRIVATE_MESSAGE: Int = 104
    const val OP_WORLD_SOCIAL_SYNC: Int = 105
    const val OP_WORLD_SOCIAL_SYNC_OK: Int = 106
    const val OP_WORLD_SOCIAL_SYNC_FAIL: Int = 107
    const val OP_SERVER_FRIEND_PRESENCE: Int = 108

    const val OP_WORLD_CLAN_SYNC: Int = 128
    const val OP_WORLD_CLAN_SYNC_OK: Int = 129
    const val OP_WORLD_CLAN_SYNC_FAIL: Int = 130
    const val OP_WORLD_CLAN_CREATE: Int = 131
    const val OP_WORLD_CLAN_ADD_MEMBER: Int = 132
    const val OP_WORLD_CLAN_LEAVE: Int = 133
    const val OP_WORLD_CLAN_REMOVE_MEMBER: Int = 134
    const val OP_WORLD_CLAN_SET_RANK: Int = 135
    const val OP_WORLD_CLAN_SET_MUTED: Int = 136
    const val OP_WORLD_CLAN_BAN_ADD: Int = 137
    const val OP_WORLD_CLAN_BAN_REMOVE: Int = 138
    const val OP_WORLD_CLAN_SET_PROFILE: Int = 139
    const val OP_WORLD_CLAN_SET_SETTING: Int = 140
    const val OP_WORLD_CLAN_SET_RANK_TITLES: Int = 141
    const val OP_WORLD_CLAN_SET_BOARD_STATE: Int = 142
    const val OP_WORLD_CLAN_APPLICATION_SUBMIT: Int = 143
    const val OP_WORLD_CLAN_APPLICATION_REVIEW: Int = 144
    const val OP_WORLD_CLAN_EVENT_UPSERT: Int = 145
    const val OP_WORLD_CLAN_EVENT_DELETE: Int = 146
    const val OP_WORLD_CLAN_ABDICATION: Int = 147
    const val OP_WORLD_CLAN_BOARD_SYNC: Int = 148
    const val OP_WORLD_CLAN_BOARD_SYNC_OK: Int = 149
    const val OP_WORLD_CLAN_DETAIL: Int = 150
    const val OP_WORLD_CLAN_DETAIL_OK: Int = 151
    const val OP_WORLD_CLAN_RENAME: Int = 152
    const val OP_WORLD_CLAN_INVITE_CREATE: Int = 153
    const val OP_WORLD_CLAN_INVITE_ACCEPT: Int = 154
    const val OP_WORLD_CLAN_OK: Int = 155
    const val OP_WORLD_CLAN_FAIL: Int = 156
    const val OP_SERVER_CLAN_SNAPSHOT: Int = 157
    const val OP_SERVER_CLAN_MEMBERSHIP_CLEARED: Int = 158
    const val OP_SERVER_CLAN_INVITE: Int = 159
    const val OP_SERVER_CLAN_APPLICATION_RESULT: Int = 160
    const val OP_SERVER_CLAN_DISBANDED: Int = 161
    const val OP_SERVER_CLAN_INVALIDATED: Int = 195
    const val OP_SERVER_CLAN_COFFER_BALANCE: Int = 196
    const val OP_WORLD_CLAN_SET_MOTIF: Int = 162
    const val OP_WORLD_CLAN_SET_RECRUIT_REQUIREMENTS: Int = 163
    const val OP_WORLD_CLAN_SET_HALL_SETTINGS: Int = 164
    const val OP_WORLD_CLAN_APPLICATION_VERDICT_ACK: Int = 165

    const val OP_WORLD_FRIENDCHAT_SYNC: Int = 166
    const val OP_WORLD_FRIENDCHAT_SYNC_OK: Int = 167
    const val OP_WORLD_FRIENDCHAT_SYNC_FAIL: Int = 168
    const val OP_WORLD_FRIENDCHAT_RESOLVE: Int = 169
    const val OP_WORLD_FRIENDCHAT_RESOLVE_OK: Int = 170
    const val OP_WORLD_FRIENDCHAT_SET_SETTINGS: Int = 171
    const val OP_WORLD_FRIENDCHAT_SET_RANK: Int = 172
    const val OP_WORLD_FRIENDCHAT_OK: Int = 173
    const val OP_WORLD_FRIENDCHAT_FAIL: Int = 174

    const val OP_WORLD_CLAN_ECONOMY_SYNC: Int = 175
    const val OP_WORLD_CLAN_ECONOMY_SYNC_REPLY: Int = 176
    const val OP_WORLD_CLAN_ECONOMY_PREPARE: Int = 177
    const val OP_WORLD_CLAN_PARTY_DROP_PREPARE: Int = 178
    const val OP_WORLD_CLAN_ECONOMY_RESULT: Int = 179
    const val OP_WORLD_CLAN_ECONOMY_COMMIT: Int = 180
    const val OP_WORLD_CLAN_ECONOMY_ABORT: Int = 181

    const val OP_WORLD_FRIENDCHAT_RESTORE: Int = 182
    const val OP_WORLD_FRIENDCHAT_RESTORE_OK: Int = 183
    const val OP_WORLD_FRIENDCHAT_CLEAR_RESTORE: Int = 184
    const val OP_WORLD_CLAN_ADMIN_MUTATION: Int = 185

    const val OP_WORLD_CLAN_MEMBER_STATS: Int = 186
    const val OP_WORLD_CLAN_GUEST_SELECTION: Int = 187
    const val OP_WORLD_CLAN_GUEST_KICK: Int = 188

    const val OP_WORLD_LIVE_CHAT_JOIN: Int = 189
    const val OP_WORLD_LIVE_CHAT_LEAVE: Int = 190
    const val OP_WORLD_LIVE_CHAT_MESSAGE: Int = 191
    const val OP_WORLD_LIVE_CHAT_RESULT: Int = 192
    const val OP_SERVER_LIVE_CHAT_ROSTER: Int = 193
    const val OP_SERVER_LIVE_CHAT_MESSAGE: Int = 194

    const val OP_WORLD_CLAN_SYSTEM_BROADCAST: Int = 197
    const val OP_WORLD_CLAN_SYSTEM_BROADCAST_ACK: Int = 198
    const val OP_SERVER_CLAN_SYSTEM_BROADCAST: Int = 199

    const val OP_WORLD_FRIENDCHAT_KICK: Int = 200
    const val OP_WORLD_FRIENDCHAT_SET_FIELD: Int = 201
    const val OP_WORLD_FRIENDCHAT_SET_FIELD_OK: Int = 202

    const val OP_WORLD_PVPA_GROUPING: Int = 203
    const val OP_WORLD_PVPA_GROUPING_RESULT: Int = 204
    const val OP_SERVER_PVPA_GROUPING_ROSTER: Int = 205

    const val OP_WORLD_CLAN_APPLICATION_WITHDRAW: Int = 206
    const val OP_SERVER_CLAN_APPLICATION_WITHDRAWN: Int = 207

    const val PROTOCOL_VERSION: Int = 9
    const val MIN_CLIENT_PROTOCOL_VERSION: Int = 2
    const val MAX_CLIENT_PROTOCOL_VERSION: Int = 9

    const val LOGIN_OK_RIGHTS_MAX_BYTES: Int = 4096
    const val LOGIN_FAIL_SCRIPT_LINE_MAX_UTF8_BYTES: Int = 512
    const val TOKEN_BYTES: Int = 32

    const val HELLO_REASON_PROTOCOL: Int = 1
    const val HELLO_REASON_BAD_KEY: Int = 2
    const val HELLO_REASON_UNKNOWN_WORLD: Int = 3
    const val HELLO_REASON_WORLD_DISABLED: Int = 4
    const val HELLO_REASON_RESTART_CLIENT: Int = 5

    const val LOGIN_FAIL_INVALID: Int = 1
    const val LOGIN_FAIL_WORLD_FULL: Int = 2
    const val LOGIN_FAIL_ALREADY_ONLINE: Int = 3
    const val LOGIN_FAIL_WORLD_DISABLED: Int = 4
    const val LOGIN_FAIL_NOT_HANDSHAKEN: Int = 5
    const val LOGIN_FAIL_BAD_FRAME: Int = 6
    const val LOGIN_FAIL_BAD_TOKEN: Int = 7
    const val LOGIN_FAIL_BANNED: Int = 8
    const val LOGIN_FAIL_MUTED: Int = 9
    const val LOGIN_FAIL_LOCKED: Int = 10
    const val LOGIN_FAIL_UPDATE_IN_PROGRESS: Int = 11
    const val LOGIN_FAIL_WORLD_ACCESS: Int = 12
    const val LOGIN_FAIL_WORLD_MIN_LEVEL: Int = 13
    const val LOGIN_FAIL_WORLD_WHITELIST: Int = 14
    const val LOGIN_FAIL_WORLD_RIGHTS: Int = 15
    const val LOGIN_FAIL_ACCOUNT_NAME: Int = 16

    const val SOCIAL_FAIL_USER_NOT_FOUND: Int = 1
    const val SOCIAL_FAIL_SELF_ACTION: Int = 2
    const val SOCIAL_FAIL_ALREADY_FRIEND: Int = 3
    const val SOCIAL_FAIL_ALREADY_IGNORED: Int = 4
    const val SOCIAL_FAIL_NOT_ACCEPTING_PRIVATE: Int = 5
    const val SOCIAL_FAIL_NOT_LOGGED_IN: Int = 6
    const val SOCIAL_FAIL_LIST_FULL: Int = 7
    const val SOCIAL_FAIL_NOT_ALLOWED: Int = 8

    const val CLAN_FAIL_NOT_ALLOWED: Int = 1
    const val CLAN_FAIL_NOT_LOGGED_IN: Int = 2
    const val CLAN_FAIL_CLAN_NOT_FOUND: Int = 3
    const val CLAN_FAIL_ALREADY_IN_CLAN: Int = 4
    const val CLAN_FAIL_MEMBER_NOT_FOUND: Int = 5
    const val CLAN_FAIL_CLAN_FULL: Int = 6
    const val CLAN_FAIL_BANNED: Int = 7
    const val CLAN_FAIL_HIERARCHY: Int = 8
    const val CLAN_FAIL_BAD_RANK: Int = 9
    const val CLAN_FAIL_NAME_INVALID: Int = 10
    const val CLAN_FAIL_NAME_TAKEN: Int = 11
    const val CLAN_FAIL_CONFLICT: Int = 12
    const val CLAN_FAIL_APPLICATIONS_CLOSED: Int = 13
    const val CLAN_FAIL_APPLICATION_EXISTS: Int = 14
    const val CLAN_FAIL_APPLICATION_NOT_FOUND: Int = 15
    const val CLAN_FAIL_REQUIREMENTS: Int = 16
    const val CLAN_FAIL_UNAVAILABLE: Int = 17
    const val CLAN_FAIL_INVITE_NOT_FOUND: Int = 18
    const val CLAN_FAIL_NO_CLAN: Int = 19

    const val FRIEND_CHAT_FAIL_NOT_ALLOWED: Int = 1
    const val FRIEND_CHAT_FAIL_NOT_LOGGED_IN: Int = 2
    const val FRIEND_CHAT_FAIL_CHANNEL_NOT_FOUND: Int = 3
    const val FRIEND_CHAT_FAIL_TARGET_NOT_FOUND: Int = 4
    const val FRIEND_CHAT_FAIL_BAD_RANK: Int = 5
    const val FRIEND_CHAT_FAIL_NAME_INVALID: Int = 6
    const val FRIEND_CHAT_FAIL_CHANNEL_FULL: Int = 7
    const val FRIEND_CHAT_FAIL_UNAVAILABLE: Int = 8
    const val FRIEND_CHAT_FAIL_RANK_REQUIRED: Int = 9
    const val FRIEND_CHAT_FAIL_NOT_JOINED: Int = 10
}
