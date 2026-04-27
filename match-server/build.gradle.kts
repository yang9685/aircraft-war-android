plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("edu.hitsz.aircraftwar.server.MatchServerApp")
}
