package org.freechains.android.dashboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.freechains.common.Addr_Port
import org.freechains.common.fsRoot
import java.io.File

@Serializable
data class Local (
    val peers : MutableList<String> = mutableListOf()
)

fun Local.toJson () : String {
    val json = Json { prettyPrint=true }
    return json.encodeToString(Local.serializer(), this)
}

fun String.fromJsonToLocal () : Local {
    val json = Json { prettyPrint=true }
    return json.decodeFromString(Local.serializer(), this)
}

fun Local.fsSave () {
    File(fsRoot!! + "/local.json").writeText(this.toJson())
}

fun Local_fsLoad () : Local {
    return File(fsRoot!! + "/local.json").readText().fromJsonToLocal()
}

fun Local_fsExists () : Boolean {
    return File(fsRoot!! + "/local.json").exists()
}
