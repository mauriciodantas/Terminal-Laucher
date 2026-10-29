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
    val THEME_SHAPE =
        "{\"name\": \"<short name, 1 to 3 words, in the user's language>\", \"accent\": \"#rrggbb\", \"background\": \"#rrggbb\", \"secondary\": \"#rrggbb\", " +
            "\"outline\": \"#rrggbb\", \"dock\": \"#rrggbb\", \"folder\": \"#rrggbb\", \"followSystem\": false, " +
            "\"font\": {\"kind\": \"google\", \"family\": \"<Google Fonts family>\"}, \"pixelClock\": true, \"iconEffect\": true, " +
            "\"widgetEffect\": true, \"widgetIntensity\": 0.6, \"crtEffect\": true, \"crtIntensity\": 0.4, \"animation\": \"<animation>\", " +
            "\"showWallpaper\": false, \"wallpaperDim\": 0.5, \"colorAsWallpaper\": false, \"iconShape\": \"<shape>\", \"folderShape\": \"<shape>\", " +
            "\"wallpaper\": {\"svg\": \"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1080 2400'>...</svg>\", \"lockScreen\": false}, " +
            "\"lockWallpaper\": {\"svg\": \"<svg ...>...</svg>\"}, \"note\": \"<one short sentence>\"}\n" +
            "Rules: the accent over the background must read well, a contrast ratio of at least 4.5 (WCAG). " +
            "secondary is a third color for secondary text, hints and lines (default keeps the accent dimmed); " +
            "it must read over the background too, at least 3.0, and works best a third of the color wheel away from the accent. " +
            "outline is the search bar border, dock the dock background, folder the folder background; " +
            "any color may be \"default\" to follow the accent. The font is an exact Google Fonts family name " +
            "(monospaced ones such as JetBrains Mono, Space Mono, IBM Plex Mono, VT323 or Share Tech Mono suit the look best), " +
            "or {\"kind\": \"default\"} for the terminal font. pixelClock keeps the pixel font for the clock. " +
            "iconEffect draws app icons in the accent color; widgetEffect tints widgets; crtEffect turns on the animation over the whole screen and crtIntensity says how strong it is; " +
            "animation picks which one: ${ThemeSpecs.ANIMATIONS.joinToString(", ")} (monitor is the CRT look with scanlines; the others are particles), or \"none\" for no animation. " +
            "Pick the animation that belongs to the concept instead of always the monitor: snow for a winter scene, petals for a spring or a Japanese garden, stars for a night sky, rain for a neon alley or a storm. " +
            "When none of them fits, write your own with customAnimation instead of animation: " +
            "{\"kind\": \"particles\", \"shape\": \"circle|petal|line|star|svg\", \"color\": \"#rrggbb\", \"count\": 60, \"size\": 6, \"speed\": 0.08, " +
            "\"angle\": 0, \"sway\": 0.02, \"spin\": 0, \"twinkle\": 0, \"opacity\": 0.8}. " +
            "count is up to ${ThemeSpecs.MAX_PARTICLES}, size 2 to 64 (dp), speed in screens per second (0 to 1.5), angle the direction in degrees " +
            "(0 falls, 90 goes left, 180 rises, 270 goes right), sway how far a particle drifts to the side (0 to 0.2), spin its turning in degrees per second, " +
            "twinkle how much its light comes and goes (0 to 1). shape svg also takes \"svg\": a single small <svg> of at most ${ThemeSpecs.MAX_SPRITE_CHARS} characters with a viewBox, " +
            "shapes only. Embers rising (angle 180, circle, orange), fireflies (twinkle, sway), leaves, confetti or bubbles are good uses. Never write a Lottie file. " +
            "intensities go from 0.05 to 1. showWallpaper shows the system wallpaper behind the home screen, dimmed by wallpaperDim (0 to 1); " +
            "colorAsWallpaper sets the background color as the system wallpaper. Set followSystem only when asked to follow the light and dark mode. " +
            "iconShape and folderShape pick the shape app icons and folders are cut to, one of: ${ThemeSpecs.ICON_SHAPES.joinToString(", ")}. " +
            "Choose them as part of the concept instead of leaving the default: hexagon or diamond for sci-fi, flower or cloudy for something soft, " +
            "sharpSquare or square for brutalist or terminal looks, squircle for clean ones, heart, leaf or teardrop for playful or nature themes. " +
            "BE CREATIVE: a request is a mood, a place, a feeling or a story, not only a palette. Pick an unexpected, specific idea " +
            "(a rainy neon alley, a deep-sea research station, an 80s arcade, a paper-cut forest at dawn) and carry it through every field at once: " +
            "the colors, the font that suits the era or material, the effects, the shapes, the name and the wallpaper. " +
            "Avoid the obvious choice (pure black with green, a plain blue-to-purple gradient) unless that is exactly what was asked. " +
            "wallpaper: draw one for EVERY theme, since a theme without a picture feels unfinished; leave it out only when the user asks for no wallpaper, " +
            "keeps the current one or wants a plain color. Add lockWallpaper (a second scene of the same world, for example the same place at another hour) " +
            "when the user mentions the lock screen or asks for more than one picture; lockScreen true reuses the home picture there. " +
            "How to draw it, as ONE self-contained SVG in the same reply: xmlns set, viewBox='0 0 1080 2400' (a portrait phone screen), " +
            "only shapes, paths, gradients, patterns, clipPaths and masks; no <image>, links, external fonts, <text>, scripts, CSS @import, <foreignObject> or <filter> " +
            "(the renderer ignores filters, so blur, glow and grain must be built from shapes); at most 30000 characters. " +
            "It must NOT look like a flat clip-art or a Paint drawing. Build it in depth, in layers from back to front: " +
            "(1) a background filled with a linearGradient or radialGradient of 3 or more stops that is not a flat color, in tones of the theme background; " +
            "(2) a far layer such as a sky, a horizon, distant mountains or city lights, in low-contrast colors, to suggest atmosphere; " +
            "(3) a middle layer with the main subject, using overlapping shapes with different fill-opacity, gradient fills and thin strokes of a lighter tone for edges and highlights; " +
            "(4) glow made from stacked radialGradients that fade to stop-opacity 0, and soft haze or fog as wide translucent shapes; " +
            "(5) texture: a pattern of tiny dots or lines, scattered small circles as stars, dust, rain or sparks (60 or more, varied in size and opacity); " +
            "(6) a vignette, a radialGradient from transparent in the center to dark at the edges. " +
            "Use curves (C, Q, S in paths) and organic variation instead of rectangles and perfect circles; vary sizes, angles and spacing; " +
            "use a limited palette of 4 to 6 colors from the theme, with one light accent for highlights. " +
            "The theme background is the dominant color and the picture stays calm and dark enough behind the top third and the middle, " +
            "where the clock, icons and text are drawn over it; put the detail and the subject in the lower third and at the edges. " +
            "Write the attributes with single quotes so the SVG fits in the JSON string as it is. " +
            "With a wallpaper set showWallpaper true and wallpaperDim about 0.3. Never write image links or base64: only SVG. " +
            "The launcher looks like a terminal: the accent color draws all the text, lines and icons over the background color."

    val THEME_RULES =
        "Design a theme for the launcher from the user's description. " +
            "Reply with JSON only, no other text, in this shape (leave out a field to keep it as it is):\n" + THEME_SHAPE

    /** The catalog of free-license image sources the guide prompt points to; it is `docs/imagens-licenca-aberta.csv` of this repository. */
    const val IMAGE_SOURCES_URL = "https://raw.githubusercontent.com/mauriciodantas/Terminal-Laucher/16-dev/docs/imagens-licenca-aberta.csv"

    /**
     * For a chat that can open links: a real picture beats a drawing. Only the guide prompt has it, since
     * a model behind the in-app command may have no internet.
     */
    val REAL_IMAGES_RULES =
        "WALLPAPER, IF YOU CAN BROWSE THE INTERNET (open links, search the web or call APIs): this replaces the SVG wallpaper rules and " +
            "the rule against image links above. A real photo or artwork looks far better than a drawing, so do NOT draw an SVG; if you cannot browse, ignore this section and draw the SVG.\n" +
            "1. Open the catalog of free-license sources: $IMAGE_SOURCES_URL (CSV; one row per site, with the licence, whether commercial use is allowed, " +
            "an example request and, in como_obter_a_imagem, how to get the direct picture link).\n" +
            "2. Use only rows whose papel_de_parede is ideal or bom and whose uso_comercial starts with Sim, in the order of prioridade; " +
            "skip \"nao\" and, unless nothing else fits, \"limitado\". Pick the source that suits the idea (space: NASA; art and paintings: The Met, Art Institute of Chicago, Cleveland; " +
            "places, history, architecture: Wikimedia Commons; nature and animals: iNaturalist or Wikimedia Commons). If a source fails or finds nothing, try the next.\n" +
            "3. Search it for the concept of the theme, in English. Prefer a portrait or tall picture at least 1080 px wide, with a calm area in the top third and the middle " +
            "(the clock, icons and text are drawn there) and the detail at the bottom and the edges. If no portrait one fits, a landscape or square picture is fine: " +
            "the phone crops it to fill the screen around its center, so choose one whose subject is in the middle. " +
            "If the exact thing asked for (bats, ghosts, a given animal) has no suitable picture, take a picture of the same mood or place and let the animation or the colors carry the rest.\n" +
            "4. The link must be one you really received from that API or page: a direct https link to a .jpg, .png or .webp (not a web page, a search page or a viewer), " +
            "of at most 3 MB. Open it to check that it loads as an image and read its size; a large PNG is often over 3 MB, so prefer a JPEG or a smaller size. " +
            "Use the size the source itself returns, as the catalog says (on Wikimedia Commons the thumburl of the API with iiurlwidth, or the upload.wikimedia.org link of the file; " +
            "do not build thumbnail links by hand, only standard sizes exist). Send an identifiable User-Agent when the catalog asks for one. " +
            "Never invent, guess or rebuild a link from memory, and never use pictures from sources outside the catalog (Google Images, Pinterest, stock sites, social networks).\n" +
            "5. Write it as \"wallpaper\": {\"url\": \"<direct link>\"}; \"lockScreen\": true may go in the same object to use it on the lock screen too. " +
            "Set showWallpaper true and wallpaperDim between 0.35 and 0.6 so the text stays readable (more when the subject is in the middle of the screen). " +
            "Leave out \"lockWallpaper\" unless the user wants a second picture or asks for one for the lock screen (then {\"url\": \"<link>\"}, another picture of the same world). " +
            "A customAnimation replaces \"animation\": leave \"animation\" out then.\n" +
            "6. Take the colors from the picture: a dark background from its darkest tone, an accent from its most vivid or lightest tone, still with a contrast of at least 4.5.\n" +
            "7. Put the credit in \"note\": for each picture its title, author and licence (the licence of the item, not of the site), as short as you can, one sentence per picture. " +
            "Use only items whose licence allows reuse, and give the credit when it asks for one.\n" +
            "8. If no picture fits, fall back to the SVG wallpaper."

    /**
     * The prompt of the theme guide, for any AI chat: the rules and the shape of a theme, how to use real
     * pictures when the chat can browse, and a last line the user replaces with what they want.
     */
    val THEME_MASTER_PROMPT =
        THEME_RULES + "\n\n" + REAL_IMAGES_RULES + "\n\nTHE THEME I WANT (replace this line with the mood or place, the colors, the font, the animation and whether it has a wallpaper):\n"

    /**
     * The theme of a reply and its note, or null when the reply holds none or it could not be read.
     * [AiMode.THEME] replies with the theme itself; [AiMode.CREATE] wraps it in "theme" when it was asked for one.
     */
    fun theme(reply: String): Pair<ThemeSpec, String>? {
        val o = AiPrompts.json(reply) ?: AiPrompts.json(AiPrompts.repairSvg(reply)) ?: return null
        val t = o.optJSONObject("theme") ?: o
        // Base64 the AI writes would be made up: what stays is the SVG it draws, which the phone turns into a PNG
        // embedded in the theme, and the link of a real picture (an AI that browses, see [REAL_IMAGES_RULES]), which
        // the import screen shows before the phone downloads it. A wallpaper it adds is shown even if it left showWallpaper off.
        val read = ThemeSpecs.fromJson(t)
        fun ThemeImage?.kept() = this is ThemeImage.Svg || this is ThemeImage.Url
        val wallpaper = read.wallpaper?.takeIf { it.image.kept() }
        val spec = read.copy(
            // A Lottie file is a file from someone else: an AI only describes particles.
            customAnimation = read.customAnimation as? CustomAnimation.Particles,
            wallpaper = wallpaper,
            lockWallpaper = read.lockWallpaper?.takeIf { it.kept() },
            showWallpaper = if (wallpaper != null) true else read.showWallpaper,
        )
        if (spec.isEmpty || ThemeSpecs.unreadable(spec, null)) return null
        return spec to (o.optString("note").ifBlank { t.optString("note") }).trim()
    }

    /** The theme an [AiMode.CREATE] reply holds instead of commands ("ia criar um tema de halloween"), or null. */
    fun createdTheme(reply: String): Pair<ThemeSpec, String>? = if (AiPrompts.json(reply)?.optJSONObject("theme") != null) theme(reply) else null

    /** Added to the rules of "ia criar": a theme can be asked for instead of commands. */
    val CREATE_TAIL =
        "\n" +
            "When the user asks for a theme, a look, colors, a font or effects for the launcher instead of a command, " +
            "write no actions or aliases: reply with JSON only, {\"theme\": <theme>, \"note\": \"<one short sentence>\"}, " +
            "where <theme> has this shape:\n" + THEME_SHAPE
}
