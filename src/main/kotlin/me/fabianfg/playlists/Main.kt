package me.fabianfg.playlists

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import me.fungames.jfortniteparse.fileprovider.DefaultFileProvider
import me.fungames.jfortniteparse.ue4.locres.FnLanguage
import me.fungames.jfortniteparse.ue4.objects.core.i18n.FText
import me.fungames.jfortniteparse.ue4.objects.core.misc.FGuid
import me.fungames.jfortniteparse.ue4.versions.Ue4Version
import java.io.File

private val json = Json(JsonConfiguration.Stable.copy(prettyPrint = true, ignoreUnknownKeys = true))

@Serializable
private class Config(val installPath : String, val aesKey : String, val language : FnLanguage)

@ExperimentalUnsignedTypes
fun getPakFilePathEpicLauncher() : String? {
    val file = File("${System.getenv("PROGRAMDATA")}\\Epic\\UnrealEngineLauncher\\LauncherInstalled.dat")
    if (file.exists() && file.canRead()) {
        val installedR = runCatching { json.parse(LauncherInstalled.serializer(), file.readText()) }
        installedR.onFailure { println("Invalid launcher installation") }
        val installed = installedR.getOrNull() ?: return null
        val game = installed.InstallationList.firstOrNull { it.AppName == "Fortnite" }
        if (game != null) {
            println("Fortnite Installation detected: ${game.InstallLocation}")
            return game.InstallLocation + "\\FortniteGame"
        } else {
            println("Fortnite is not installed")
        }
    } else {
        println("Failed to find Epic Games Launcher Installation")
    }
    return null
}

@Serializable
private data class LauncherInstalled(
    val InstallationList : List<InstallItem>
)

@Serializable
private data class InstallItem(
    val InstallLocation : String,
    val AppName : String,
    val AppVersion : String
)

@ExperimentalUnsignedTypes
private fun loadConfig() = runCatching {
    json.parse(Config.serializer(), File("config.json").readText())
}.getOrElse {
    val pakPath = getPakFilePathEpicLauncher() ?: ""
    val config = Config(pakPath, "", FnLanguage.EN)
    File("config.json").writeText(json.stringify(Config.serializer(), config))
    config
}

fun loadFilesToLookFor() = runCatching {
    File("search.txt").readLines()
}.getOrElse {
    File("search.txt").createNewFile()
    emptyList()
}

@ExperimentalUnsignedTypes
fun main() {
    val config = loadConfig()

    if (config.aesKey.trim().length != 66) {
        println("Please enter a valid aes key in config.json")
        return
    }
    if (config.installPath.isEmpty()) {
        println("Couldn't get installation dir, please enter manually in config.json")
        return
    }
    val search = loadFilesToLookFor().map { it.trim().toLowerCase() }
    if (search.isEmpty()) {
        println("search.txt is empty. Please add playlist code names to search for separated by new lines")
        return
    }
    val provider = DefaultFileProvider(File(config.installPath), Ue4Version.GAME_UE4_25)
    val sub = provider.submitKey(FGuid.mainGuid, config.aesKey.trim())
    if (sub == 0) {
        println("The aes key '${config.aesKey}' doesn't work with any pak file, please check it")
        return
    } else {
        println("Mounted $sub pak files")
    }
    val locres = provider.loadLocres(config.language)
    if (locres != null) {
        println("Loaded locres for language ${config.language.name}")
    } else {
        println("Failed to load locres for language ${config.language.name}")
    }
    println()
    val found = provider.files().filter { search.contains(it.key.substringAfterLast('/').substringBeforeLast('.')) }
    found.forEach { entry ->
        val pkg = provider.loadGameFile(entry.value)
        val playlist = pkg?.exportMap?.firstOrNull { it.classIndex.name.contains("FortPlaylistAthena", true) }
        if (playlist != null) {
            val export = playlist.exportObject.value.baseObject
            val displayName = export.getOrNull<FText>("UIDisplayName")?.textForLocres(locres)
            if (displayName != null) {
                println("${playlist.objectName.text} --> $displayName")
            } else {
                println("${playlist.objectName.text} didn't have UIDisplayName property")
            }
        } else {
            println("${entry.value.getNameWithoutExtension()} was not a playlist")
        }
    }
}