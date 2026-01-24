package dev.openrune.central.tools

import dev.openrune.central.crypto.Ed25519
import java.io.File

/**
 * Prints a matching world-server private key and central-server public key.
 *
 * Run:
 *   ./gradlew run -DmainClass=dev.openrune.central.tools.KeyGenKt
 *
 * Or from IDE: run this main().
 */
fun main(args: Array<String>) {
    val kp = Ed25519.generateKeyPair()

    // Optional: write directly into YAML configs (avoid parsing stdout in Gradle).
    // args[0] = central-server.yml path, args[1] = game.yml path
    if (args.size >= 2) {
        val centralServerYml = File(args[0])
        val gameYml = File(args[1])

        require(centralServerYml.exists()) { "Missing ${centralServerYml.path}" }
        require(gameYml.exists()) { "Missing ${gameYml.path}" }

        fun upsertQuotedYamlValue(file: File, key: String, value: String) {
            val original = file.readText()

            // Replace: key: "value"
            val quoted = Regex("""(?m)^(\s*${Regex.escape(key)}\s*:\s*)"(?:[^"\\]|\\.)*"\s*$""")
            if (quoted.containsMatchIn(original)) {
                file.writeText(original.replace(quoted) { mr -> "${mr.groupValues[1]}\"$value\"" })
                return
            }

            // Replace: key: value
            val unquoted = Regex("""(?m)^(\s*${Regex.escape(key)}\s*:\s*)(\S+)\s*$""")
            if (unquoted.containsMatchIn(original)) {
                file.writeText(original.replace(unquoted) { mr -> "${mr.groupValues[1]}\"$value\"" })
                return
            }

            // Append if missing.
            file.writeText(original.trimEnd() + "\n$key: \"$value\"\n")
        }

        upsertQuotedYamlValue(centralServerYml, "authPublicKey", kp.publicKey)
        upsertQuotedYamlValue(gameYml, "worldKey", kp.privateKey)

        println("Wrote authPublicKey to ${centralServerYml.name} and worldKey to ${gameYml.name}")
        return
    }

    println("CENTRAL (put in config.yml worlds[].authPublicKey):")
    println(kp.publicKey)
    println()
    println("WORLD SERVER (keep secret; use to sign private API requests):")
    println(kp.privateKey)
    println()
    println("YAML snippet:")
    println("authPublicKey: \"${kp.publicKey}\"")
    println("# world server config:")
    println("# authPrivateKey: \"${kp.privateKey}\"")
}

