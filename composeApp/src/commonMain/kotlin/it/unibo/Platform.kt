package it.unibo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform