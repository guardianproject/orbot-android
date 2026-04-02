import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.GradleException
import java.net.URI
import java.io.File

abstract class UpdateBridgeConfig : DefaultTask() {

    @get:Input
    abstract val enabledForVariant: Property<Boolean>

    @get:Input
    abstract val gitLogUnixTimestamp: Property<String>

    @get:Input
    abstract val gitStatusOutput: Property<String>

    @get:OutputDirectory
    val assetsDir: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    fun run() {
        if (!enabledForVariant.get()) return

        // configuration cache safe (gradle provider based) git status check
        val status = gitStatusOutput.get().trim()
        if (status.isNotEmpty())
            throw GradleException("ERROR: Working tree has uncommitted changes.\nCommit all changes (including updated bridges) before a release build.\n\n$status")

        println("\n\n\uD83C\uDF0D checking to see if Tor Project's bridge config and Guardian Project's dnstt config have been updated for this release...")
        val assetsFolder = assetsDir.get().asFile
        if (!assetsFolder.exists()) assetsFolder.mkdirs()

        val outputFile = File(assetsFolder, "builtin-bridges.json")
        val lastCommitUnix = gitLogUnixTimestamp.getOrElse("0").trim().toLongOrNull() ?: 0L
        val oneDaySeconds = 86400L
        val isStale = (System.currentTimeMillis() / 1000) - lastCommitUnix > oneDaySeconds

        if (!outputFile.exists() || isStale) {
            println("\uD83E\uDD2E bridge configuration is stale...")
            val bridgeUri = "https://bridges.torproject.org/moat/circumvention/builtin"
            println("\uD83E\uDDC5 checking tor bridge config@$bridgeUri...")

            try {
                downloadFile(bridgeUri, outputFile)

                val countries = listOf(
                    "ae",
                    "af",
                    "bd",
                    "cn",
                    "co",
                    "global",
                    "id",
                    "ir",
                    "kw",
                    "pk",
                    "qa",
                    "ru",
                    "sy",
                    "tr",
                    "ug",
                    "uz"
                )
                countries.forEach { country ->
                    val url =
                        "https://raw.githubusercontent.com/dnstt-xyz/dnstt_xyz_app/refs/heads/main/assets/dns/$country.json"
                    println("\uD83D\uDD0E checking dnstt config@@$url...")
                    val dest = File(assetsFolder, "dns-$country.json")
                    downloadFile(url, dest)
                }
            } catch (e: Exception) {
                throw GradleException("ERROR: Failed to fetch bridges: ${e.message}")
            }
            throw GradleException("\uD83C\uDF81 bridge files updated, please commit them and rerun the release build...")
        }
        println("\uD83C\uDF80 bridge and dnstt configurations are good, continuing with release...")
    }

    private fun downloadFile(url: String, destination: File) {
        URI(url).toURL().openStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
