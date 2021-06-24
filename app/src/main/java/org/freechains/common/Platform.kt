package org.freechains.common

import com.goterl.lazysodium.*

val lazySodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid())

var fsRoot: String? = null
