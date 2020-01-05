package $package$

/**
 * Example config
 * @param httpServerHost listen http host
 * @param httpServerPort listen http port
 * @param slackAppConfig slack app config
 */
case class AppConfig(
    httpServerHost: String = "0.0.0.0",
    httpServerPort: Int = 8080,
    slackAppConfig: SlackAppConfig,
    databaseDir: String = "data"
)
