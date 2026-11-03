package com.flippermobile.domain.model

enum class Compat { SUPPORTED, PARTIAL, UNSUPPORTED, INFO }

data class InfoItem(val label: String, val value: String, val compat: Compat = Compat.INFO)

data class InfoSection(val title: String, val items: List<InfoItem>)
