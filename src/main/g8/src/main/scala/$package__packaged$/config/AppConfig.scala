package $package$.config

/**
 * Example config
 */
case class AppConfig(
    httpServerHost: String = "0.0.0.0",
    httpServerPort: Int = 8080,
    slackAppConfig: SlackAppConfig,
    databaseDir: String = "data"
)
