import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.util.logging.Logger

class ConfigRunner private constructor(
    private val port: String,
    private val target: String,
    private val runnerDatabase: RunnerDatabase? = null,
    private val servletContextPath: String? = null,
    private val logging: RunnerLogging? = null,
    private val sslPort: String? = null,
    private var additionalSetting: File? = null
) {
    private val logger: Logger = Logger.getLogger("engine-builder-config")
    private var targetDir: String = ""

    init {
        val dir = target.split("/").toMutableList()
        dir.removeLast()
        targetDir = dir.joinToString("/")
    }

    data class Builder(val port: String) {
        private var db: RunnerDatabase? = null
        private var contextPath: String? = null
        private var logging: RunnerLogging? = null
        private var sslPort: String? = null
        private var additionalSetting: File? = null
        fun setDataSource(i: RunnerDatabase) = apply { db = i }
        fun setServletContextPath(i: String) = apply { contextPath = i }
        fun setLogging(i: RunnerLogging) = apply { logging = i }
        fun setLogging(i: Boolean) = apply {
            if (i) {
                logging = RunnerLogging()
            }
        }

        fun setSslPort(i: String?) = apply { sslPort = i }
        fun addAdditionalSetting(i: File) = apply { additionalSetting = i }

        fun build(targetPath: String) {
            ConfigRunner(
                port, targetPath, db,
                servletContextPath = contextPath,
                logging = logging,
                sslPort = sslPort,
                additionalSetting = additionalSetting
            ).buildRunnerConfig()
        }

    }

    private fun buildRunnerConfig() {
        logger.info("build runner configuration [$targetDir] [$target]")
        val mapConfig: MutableMap<String, Any> = mutableMapOf()
        val fileTemp = File(target)
        if (fileTemp.exists()) {
            fileTemp.delete()
        }

        if (!File(targetDir).exists()) {
            File(targetDir).mkdirs()
        }

        val mapServer: MutableMap<String, Any> = mutableMapOf()
        if (sslPort != null) {
            mapServer["port"] = sslPort
            mapServer["http"] = U.pair("port", port)
        } else {
            mapServer["port"] = port
            mapServer["http"] = U.pair("port", 0)
        }

        servletContextPath?.let {
            mapServer["servlet"] = U.pair("context-path", "/$it")
        }

        val mapSpring: MutableMap<String, Any> = mutableMapOf()
        runnerDatabase?.let { db ->
            mapSpring["datasource"] = db.toMapDb()
            mapSpring["jpa"] = db.toMapJpa()
        }
        mapSpring["main"] = U.pair("allow-circular-references", "true")

        mapConfig["server"] = mapServer
        mapConfig["spring"] = mapSpring
        logging?.let {
            mapConfig["logging"] = it.toMapLog()
        }

        try {
            additionalSetting?.let { add ->
                val read = U.DtcYaml().load<Map<String, Any>>(FileInputStream(add))
                val configPlus = (mapConfig.asSequence() + read.asSequence())
                    .distinct().groupBy({ it.key }, { it.value })
                    .mapValues { m ->
                        val map: MutableMap<String, Any> = mutableMapOf()
                        m.value.forEach { i ->
                            map += i as Map<String, Any>
                        }
                        map
                    }
                U.DtcYaml().dump(configPlus, PrintWriter(File(target)))
            } ?: U.DtcYaml().dump(mapConfig, PrintWriter(File(target)))
        } catch (e: Exception) {
            logger.info("failed read file additional setting")
            logger.info(e.localizedMessage)
        }


    }
}

class RunnerDatabase(
    val ip: String, val port: String, val name: String, val username: String, val password: String,
    val type: String = "mysql", val showSql: Boolean = false, val openInView: Boolean = false,
    val formatSql: Boolean = true, val ddlAuto: String = "update"

) {
    fun toMapDb(): MutableMap<String, Any> {
        val map: MutableMap<String, Any> = mutableMapOf()
        when (type) {
            "sqlserver" -> {
                map["url"] = "jdbc:sqlserver://$ip:$port;databaseName=$name;encrypt=true;trustServerCertificate=true;"
            }

            "postgresql" -> {
                map["url"] = "jdbc:postgresql://$ip:$port/$name"
            }

            else -> {
                map["driver-class-name"] = "com.mysql.cj.jdbc.Driver"
                map["url"] = "jdbc:mysql://$ip:$port/$name?createDatabaseIfNotExist=true&serverTimezone=Asia/Jakarta"
            }
        }
        map["username"] = username
        map["password"] = password
        return map
    }

    fun toMapJpa(): MutableMap<String, Any> {
        val map: MutableMap<String, Any> = mutableMapOf()
        map["open-in-view"] = openInView
        map["show-sql"] = showSql
//        map["format_sql"] = formatSql
        map["properties"] = U.pair(
            "hibernate", mapOf(
//            Pair("show-sql", showSql),
                Pair("format_sql", formatSql),
            )
        )
        map["hibernate"] = U.pair("ddl-auto", ddlAuto)
        return map
    }
}

class RunnerLogging(
    val root: String = "INFO",
    val path: String = "logs/app",
    val pattern: String = "%d{yyyy-MM-dd}.%i",
    val maxFileSize: String = "1MB",
    val totalSizeCap: String = "1MB"
) {
    private val pathLog = if (path.endsWith(".log")) path else "$path.log"
    fun toMapLog(): MutableMap<String, Any> {
        val mapLog: MutableMap<String, Any> = mutableMapOf()
        mapLog["level"] = U.pair("root", root)
        mapLog["file"] = U.pair("name", pathLog)
        mapLog["logback.rollingpolicy"] = mutableMapOf(
            Pair("file-name-pattern", "$root-$pattern.log"),
            Pair("max-file-size", maxFileSize),
            Pair("total-size-cap", totalSizeCap)
        )
        return mapLog
    }

}

