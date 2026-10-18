package app.lawnchair.command

/** The prompts of the modes only the launcher has ("ia pastas", "ia tema") and the reading of what they answer. */
object LauncherAiPrompts {

    private const val MAX_FOLDER_APPS = 600

    /** The apps still outside a folder (all of them with force), and the folders the user already has. */
    fun drawer(context: AiContext): String = buildString {
        val inFolders = context.host.folders.flatMap { it.second }.toSet()
        if (context.host.forceFolders) {
            append("ALL APPS: ")
            appendLine(context.apps.distinct().take(MAX_FOLDER_APPS).joinToString(", "))
        } else {
            append("APPS WITHOUT A FOLDER: ")
            appendLine(context.apps.filter { it !in inFolders }.distinct().take(MAX_FOLDER_APPS).joinToString(", "))
        }
        if (context.host.folders.isNotEmpty()) {
            appendLine(
                if (context.host.forceFolders) {
                    "EXISTING FOLDERS (you may reorganize them):"
                } else {
                    "EXISTING FOLDERS (keep them; their apps stay where they are):"
                },
            )
            context.host.folders.forEach { (name, apps) -> appendLine("- " + name + ": " + apps.joinToString(", ")) }
        }
    }.trim()

    const val FOLDERS_RULES =
        "Sort the apps without a folder into app drawer folders that make sense to this user, by what the apps are for " +
            "(for example social, work, photos, money, travel, games, tools). Follow the user's request when it says how to group them. " +
            "Use app names exactly as listed, each app once. A new folder holds at least 2 apps; aim for 4 to 12 folders, " +
            "and leave out an app that fits no folder rather than forcing it in. To add apps to an existing folder, use its exact name. " +
            "Folder names are short (one or two words) and in the language of the user's request. " +
            "Reply with JSON only, no other text: " +
            "{\"folders\": [{\"name\": \"<folder>\", \"apps\": [\"<app>\", \"<app>\"]}], \"note\": \"<one short sentence>\"}"

    const val FOLDERS_FORCE_RULES =
        "Reorganize ALL the apps into app drawer folders that make sense to this user, by what the apps are for " +
            "(for example social, work, photos, money, travel, games, tools), moving apps between the existing folders where it helps. " +
            "Follow the user's request when it says how to group them. Use app names exactly as listed, each app once. " +
            "List the whole content of every folder you keep or create: an existing folder you name gets exactly the apps you list, " +
            "so keep its name to keep it. An existing folder you leave out keeps only the apps you did not move, and is deleted once empty. " +
            "A new folder holds at least 2 apps; aim for 4 to 12 folders, and leave out an app that fits no folder rather than forcing it in. " +
            "Folder names are short (one or two words) and in the language of the user's request. " +
            "Reply with JSON only, no other text: " +
            "{\"folders\": [{\"name\": \"<folder>\", \"apps\": [\"<app>\", \"<app>\"]}], \"note\": \"<one short sentence>\"}"

    fun currentTheme(context: AiContext): String = context.host.theme?.let { "CURRENT THEME (start from it when the request asks for changes to it): " + ThemeSpecs.encode(it.copy(name = ""), pretty = false) + "\n\n" }.orEmpty()

    /** The fields of a theme and how to fill them, for [AiMode.THEME] and for a theme asked of [AiMode.CREATE]. */
    const val THEME_SHAPE =
        "{\"name\": \"<short name, 1 to 3 words, in the user's language>\", \"accent\": \"#rrggbb\", \"background\": \"#rrggbb\", \"secondary\": \"#rrggbb\", " +
            "\"outline\": \"#rrggbb\", \"dock\": \"#rrggbb\", \"folder\": \"#rrggbb\", \"followSystem\": false, " +
            "\"font\": {\"kind\": \"google\", \"family\": \"<Google Fonts family>\"}, \"pixelClock\": true, \"iconEffect\": true, " +
            "\"widgetEffect\": true, \"widgetIntensity\": 0.6, \"crtEffect\": true, \"crtIntensity\": 0.4, " +
            "\"showWallpaper\": false, \"wallpaperDim\": 0.5, \"colorAsWallpaper\": false, " +
            "\"wallpaper\": {\"svg\": \"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1080 2400'>...</svg>\", \"lockScreen\": false}, " +
            "\"lockWallpaper\": {\"svg\": \"<svg ...>...</svg>\"}, \"note\": \"<one short sentence>\"}\n" +
            "Rules: the accent over the background must read well, a contrast ratio of at least 4.5 (WCAG). " +
            "secondary is a third color for secondary text, hints and lines (default keeps the accent dimmed); " +
            "it must read over the background too, at least 3.0, and works best a third of the color wheel away from the accent. " +
            "outline is the search bar border, dock the dock background, folder the folder background; " +
            "any color may be \"default\" to follow the accent. The font is an exact Google Fonts family name " +
            "(monospaced ones such as JetBrains Mono, Space Mono, IBM Plex Mono, VT323 or Share Tech Mono suit the look best), " +
            "or {\"kind\": \"default\"} for the terminal font. pixelClock keeps the pixel font for the clock. " +
            "iconEffect draws app icons in the accent color; widgetEffect tints widgets; crtEffect adds scanlines; " +
            "intensities go from 0.05 to 1. showWallpaper shows the system wallpaper behind the home screen, dimmed by wallpaperDim (0 to 1); " +
            "colorAsWallpaper sets the background color as the system wallpaper. Set followSystem only when asked to follow the light and dark mode. " +
            "wallpaper and lockWallpaper: include them ONLY when the user explicitly asks for a wallpaper or a picture " +
            "(wallpaper, papel de parede, fondo de pantalla, imagem de fundo, lock screen...); otherwise leave both out. " +
            "You draw the picture yourself as ONE self-contained SVG in the same reply: xmlns set, viewBox='0 0 1080 2400' (a portrait phone screen), " +
            "only shapes, paths, gradients, patterns and filters; no <image>, links, external fonts, <text>, scripts, CSS @import or <foreignObject>; " +
            "at most 20000 characters. Make it a real illustration of what was asked (a scene or motifs with depth, not a flat color), " +
            "built on the theme background as its dominant color and calm behind the top third and the middle, where the clock, icons and text are drawn over it. " +
            "Write the attributes with single quotes so the SVG fits in the JSON string as it is. " +
            "lockScreen true puts the same picture on the lock screen; lockWallpaper is a different picture just for it. " +
            "Use them only when the user asks for the lock screen or for more than one wallpaper. " +
            "With a wallpaper set showWallpaper true and wallpaperDim about 0.3. Never write image links or base64: only SVG. " +
            "The launcher looks like a terminal: the accent color draws all the text, lines and icons over the background color."

    const val THEME_RULES =
        "Design a theme for the launcher from the user's description. " +
            "Reply with JSON only, no other text, in this shape (leave out a field to keep it as it is):\n" + THEME_SHAPE

    /**
     * The theme of a reply and its note, or null when the reply holds none or it could not be read.
     * [AiMode.THEME] replies with the theme itself; [AiMode.CREATE] wraps it in "theme" when it was asked for one.
     */
    fun theme(reply: String): Pair<ThemeSpec, String>? {
        val o = AiPrompts.json(reply) ?: AiPrompts.json(AiPrompts.repairSvg(reply)) ?: return null
        val t = o.optJSONObject("theme") ?: o
        // A link or base64 the AI writes would be made up: only the SVG it draws stays, which the phone turns into
        // a PNG embedded in the theme. A wallpaper it adds is shown even if it left showWallpaper off.
        val read = ThemeSpecs.fromJson(t)
        val wallpaper = read.wallpaper?.takeIf { it.image is ThemeImage.Svg }
        val spec = read.copy(
            wallpaper = wallpaper,
            lockWallpaper = read.lockWallpaper as? ThemeImage.Svg,
            showWallpaper = if (wallpaper != null) true else read.showWallpaper,
        )
        if (spec.isEmpty || ThemeSpecs.unreadable(spec, null)) return null
        return spec to (o.optString("note").ifBlank { t.optString("note") }).trim()
    }

    /** The theme an [AiMode.CREATE] reply holds instead of commands ("ia criar um tema de halloween"), or null. */
    fun createdTheme(reply: String): Pair<ThemeSpec, String>? = if (AiPrompts.json(reply)?.optJSONObject("theme") != null) theme(reply) else null

    /** Added to the rules of "ia criar": a theme can be asked for instead of commands. */
    const val CREATE_TAIL =
        "\n" +
            "When the user asks for a theme, a look, colors, a font or effects for the launcher instead of a command, " +
            "write no actions or aliases: reply with JSON only, {\"theme\": <theme>, \"note\": \"<one short sentence>\"}, " +
            "where <theme> has this shape:\n" + THEME_SHAPE
}
