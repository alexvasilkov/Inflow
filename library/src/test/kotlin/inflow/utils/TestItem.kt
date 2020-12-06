package inflow.utils

import inflow.LoadedAt

data class TestItem(override val loadedAt: Long) : LoadedAt
