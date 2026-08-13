package com.weich.daptune.core.model

import java.text.Normalizer
import java.util.Locale

fun normalizeRouteName(name: String): String = Normalizer
    .normalize(name, Normalizer.Form.NFKC)
    .trim()
    .replace(RouteNameWhitespace, " ")
    .lowercase(Locale.ROOT)

private val RouteNameWhitespace = Regex("\\s+")
