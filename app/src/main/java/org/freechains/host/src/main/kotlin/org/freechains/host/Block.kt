package org.freechains.host

import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.freechains.common.HKey

typealias Hash = String

enum class State {
    MISSING, BLOCKED, LINKED, HIDDEN, ACCEPTED, ALL
}

fun State.toString_ () : String {
    return when (this) {
        State.ALL -> "all"
        State.ACCEPTED -> "accepted"
        State.HIDDEN -> "hidden"
        State.LINKED -> "linked"
        State.BLOCKED -> "blocked"
        State.MISSING -> "missing"
    }
}

fun String.toState () : State {
    return when (this) {
        "all"      -> State.ALL
        "accepted" -> State.ACCEPTED
        "hidden"   -> State.HIDDEN
        "linked"   -> State.LINKED
        "blocked"  -> State.BLOCKED
        "missing"  -> State.MISSING
        else       -> error("bug found (2)")
    }
}

        @Serializable
data class Like (
    val n    : Int,     // +1: like, -1: dislike
    val hash : Hash     // target post hash
)

@Serializable
data class Signature (
    val hash : String,    // signature
    val pub  : HKey       // of pubkey (if "", assumes pub of chain)
)

@Serializable
data class Payload (            // payload metadata
    val crypt   : Boolean,      // is encrypted (method depends on chain)
    val hash    : Hash          // hash of contents
)

@Serializable
data class Immut (
    val time    : Long,         // author's timestamp
    val pay     : Payload,      // payload metadata
    val prev    : Hash?,        // previous author's post (null if anonymous // gen if first post)
    val like    : Like?,        // point to liked post
    val backs   : Array<Hash>   // back links (happened-before blocks)
)

@Serializable
data class Block_ (
        val hash   : Hash,
        val local  : Long,
        val time   : Long,
        val pay    : Payload,
        val like   : Like?,
        val sign   : Signature?,
        val prev   : Hash?,
        val backs  : Array<Hash>,
        val fronts : Array<Hash>
)

@Serializable
data class Block (
    val immut   : Immut,        // things to hash
    val hash    : Hash,         // hash of immut
    val sign    : Signature?
) {
    val local   : Long = getNow()  // used by Simul.kt to evaluate local-immut
}

fun Immut.toJson (): String {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.stringify(Immut.serializer(), this)
}

fun Block.toJson (): String {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.stringify(Block.serializer(), this)
}

fun Block_.toJson (): String {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.stringify(Block_.serializer(), this)
}

fun String.jsonToBlock (): Block {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.parse(Block.serializer(), this)
}

fun String.jsonToBlock_ (): Block_ {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.parse(Block_.serializer(), this)
}

fun Hash.hashSplit () : Pair<Int,String> {
    val (height, hash) = this.split("_")
    return Pair(height.toInt(), hash)
}

fun Hash.toHeight () : Int {
    return this.hashSplit().first
}

fun Hash.hashIsBlock () : Boolean {
    return this.contains('_')   // otherwise is pubkey
}

fun Block.isFrom (pub: HKey) : Boolean {
    return (this.sign!=null && this.sign.pub==pub)
}
