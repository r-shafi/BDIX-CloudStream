version = 1

android {
    namespace = "net.ddns.shafi"
}

cloudstream {
    description = "SRZone Live TV in Bangladesh"
    authors = listOf("r-shafi")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf(
        "Live"
    )
    language = "bn"

    iconUrl = "http://srzone.live/uploads/system_logo/srzone-logo.png"
}
