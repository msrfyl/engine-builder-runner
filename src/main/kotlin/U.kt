import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml


object U {
    fun pair(key: String, obj: Any): MutableMap<String, Any> = mutableMapOf(Pair(key, obj))

    fun DtcYaml(): Yaml {
        val options = DumperOptions()
        options.indent = 2
        options.isPrettyFlow = true
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        return Yaml(options)
    }
}