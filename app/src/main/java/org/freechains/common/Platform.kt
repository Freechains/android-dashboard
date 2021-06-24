package org.freechains.common

import com.goterl.lazysodium.SodiumAndroid

val lazySodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid())

var fsRoot: String? = null
