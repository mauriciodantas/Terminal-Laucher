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
            "\"lockWallpaper\": {\"svg\": \"<svg ...>...</svg>\"}, " +
            "\"glance\": {\"show\": true, \"animation\": \"<${GlanceSpecs.ANIMATIONS.joinToString("|")}>\", \"tint\": true, \"shortcuts\": true, \"chats\": true, " +
            "\"targets\": {\"agenda\": true, \"weather\": true, \"alarm\": true, \"reminders\": true, \"bluetooth\": true}, \"autoRotate\": true, " +
            "\"look\": {\"preset\": \"<${GlanceSpecs.PRESETS.joinToString("|")}>\", \"align\": \"<${GlanceSpecs.ALIGNS.joinToString("|")}>\", \"density\": \"<${GlanceSpecs.DENSITIES.joinToString("|")}>\", " +
            "\"header\": true, \"date\": true, \"status\": true, \"clockFormat\": \"<${GlanceSpecs.CLOCK_FORMATS.joinToString("|")}>\", \"dateFormat\": \"<${GlanceSpecs.DATE_FORMATS.joinToString("|")}>\", " +
            "\"seconds\": false, \"dateCaps\": true, \"clockSize\": 100, \"textSize\": 100, \"border\": true, \"borderWidth\": 1, \"radius\": 8, " +
            "\"titleBar\": \"<${GlanceSpecs.TITLE_BARS.joinToString("|")}>\", \"glow\": true, \"dim\": 62, \"urgent\": \"<${GlanceSpecs.URGENT_COLORS.joinToString("|")}>\", " +
            "\"statusItems\": {\"sound\": true, \"storage\": true, \"ram\": true}, \"hiddenShortcuts\": [\"<${GlanceSpecs.SHORTCUTS.joinToString("|")}>\"]}}, " +
            "\"note\": \"<one short sentence>\"}\n" +
            "Rules: the accent over the background must read well, a contrast ratio of at least 4.5 (WCAG). " +
            "secondary is a third color for secondary text, hints and lines (default keeps the accent dimmed); " +
            "it must read over the background too, at least 3.0, and works best a third of the color wheel away from the accent, unless the picture or the concept has a limited palette: then take it from that palette. " +
            "outline is the search bar border, dock the dock background, folder the folder background; " +
            "any color may be \"default\" to follow the accent. The font is an exact Google Fonts family name " +
            "(monospaced ones such as JetBrains Mono, Space Mono, IBM Plex Mono, VT323 or Share Tech Mono suit the look best), " +
            "or {\"kind\": \"default\"} for the terminal font. pixelClock keeps the pixel font for the clock. " +
            "iconEffect draws app icons in the accent color; widgetEffect tints widgets; crtEffect turns on the animation over the whole screen and crtIntensity says how strong it is; " +
            "animation picks which one: ${ThemeSpecs.ANIMATIONS.joinToString(", ")} (monitor is the CRT look with scanlines; the others are particles), or \"none\" for no animation. " +
            "Animation is optional, and no animation is often the best answer: add one only when it adds something real to the concept and you would be proud of it, " +
            "never just because the field exists. A calm theme, a photo that already has movement or mood, or a legible minimal look is better still. " +
            "The same goes for every moving part of the theme: the screen animation, the effects (crtEffect, widgetEffect), the animation of the At a Glance panel and its Lottie. " +
            "When you do add one, pick the animation that belongs to the concept instead of always the monitor: snow for a winter scene, petals for a spring or a Japanese garden, stars for a night sky, rain for a neon alley or a storm. " +
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
            "glance is the At a Glance panel at the top of the home screen (the weather, the next event, the alarm, with a row of shortcuts under it). " +
            "You decide what suits the concept, and every field is optional. show false hides the whole panel: do it when it would fight the picture " +
            "(a wallpaper whose subject sits at the top, a poster, a minimal or empty look); otherwise leave it shown. " +
            "animation is the readout beside its big value: ${GlanceSpecs.ANIMATIONS.joinToString(", ")}. " +
            "Change it only if another one clearly suits the concept (sonar for the deep sea, wave for an oscilloscope or music, bars for an equalizer, radar for sci-fi) " +
            "or off when it would be noise; leaving it out, so it stays as the user has it, is a good answer too. " +
            "shortcuts false removes the row of utility shortcuts and chats false the row of chat shortcuts, for a cleaner look. " +
            "look is how the panel is drawn, and it is where a theme can make the panel its own. preset is a ready-made style: " +
            "terminal (square border, solid title bar), outline (thicker rounded border, title as plain text), open (no border or header, compact rows), " +
            "centered (everything centered, rounded panel) and minimal (only the clock, the weekday and the target, to the right). " +
            "Pick a preset first, then refine with the other fields only where the concept asks for it. " +
            "align ${GlanceSpecs.ALIGNS.joinToString("/")} places the readout lines; density ${GlanceSpecs.DENSITIES.joinToString("/")} sets the room the target row takes. " +
            "header, date and status show or hide those lines (the panel may still hide some when the screen is short). " +
            "clockFormat ${GlanceSpecs.CLOCK_FORMATS.joinToString("/")} and dateFormat ${GlanceSpecs.DATE_FORMATS.joinToString("/")} (full is a long date, short an abbreviated one, numeric 02/10/2026, weekday only the day name); " +
            "seconds adds seconds to the clock, dateCaps writes the date in capitals. clockSize ${GlanceSpecs.CLOCK_SIZE.first}-${GlanceSpecs.CLOCK_SIZE.last} and textSize ${GlanceSpecs.TEXT_SIZE.first}-${GlanceSpecs.TEXT_SIZE.last} are percents of the default size. " +
            "border turns the frame on or off, borderWidth ${GlanceSpecs.BORDER_WIDTH.first}-${GlanceSpecs.BORDER_WIDTH.last} dp, radius ${GlanceSpecs.RADIUS.first}-${GlanceSpecs.RADIUS.last} dp rounds the corners, " +
            "titleBar ${GlanceSpecs.TITLE_BARS.joinToString("/")} (filled is a solid bar, outline only the text), glow is the soft light behind the clock, " +
            "dim ${GlanceSpecs.DIM.first}-${GlanceSpecs.DIM.last} is how strong the secondary text is against the accent (lower for quiet, higher for legible), " +
            "urgent ${GlanceSpecs.URGENT_COLORS.joinToString("/")} is the color of an event about to start, so pick one that stands out from the accent and the background. " +
            "statusItems choose what the status line lists (sound profile, storage, memory), and hiddenShortcuts (${GlanceSpecs.SHORTCUTS.joinToString(", ")}) removes buttons from the shortcut row. " +
            "Match the look to the concept: sharp and square (terminal, open) for brutalist, hacker or industrial ideas; rounded with outline or centered for soft, nature or playful ones; " +
            "minimal when the wallpaper is busy or its subject sits near the top; compact density for dense, technical looks; glow on for neon and CRT ideas and off for paper or flat ones. " +
            "Every field is optional and each one you add should be there for a reason, since leaving the panel as the user has it is a good answer too. " +
            "targets switch off kinds of information and autoRotate stops the panel from turning through them: leave them out unless the user asks. " +
            "tint true paints a Lottie animation in the accent color. \"lottie\" (an animation of its own) is only for a chat that can browse, see below; never write the file. " +
            "BE CREATIVE: a request is a mood, a place, a feeling or a story, not only a palette. Pick an unexpected, specific idea " +
            "(a rainy neon alley, a deep-sea research station, an 80s arcade, a paper-cut forest at dawn) and carry it through the fields that serve it: " +
            "the colors, the font that suits the era or material, the shapes, the name and the wallpaper, and an effect or an animation only where it truly adds to the idea (not every field has to change). " +
            "Avoid the obvious choice (pure black with green, a plain blue-to-purple gradient) unless that is exactly what was asked. " +
            "COMPOSE THE THEME AS ONE PIECE: the mood of the request is kept, but the wallpaper, colors, font, shapes, animation, particles and the At a Glance panel must look like they belong to one picture. " +
            "Decide the wallpaper first, then build every other field from it: the background from its darkest tone, the accent from its light or glow, the secondary, outline, dock and folder from tones, tints and shades that are already in it, " +
            "and a particle color, a font and shapes that fit its material and era (a lithograph, a neon street, a watercolor). " +
            "Do not add a hue the picture does not have, such as orange or purple over a black and white print, only because the concept names it (Halloween, for example, can be a mood of dark and dread instead of the orange and purple colors). " +
            "A picture with one dominant hue calls for a near-monochrome palette with a single accent. Before replying, look at the whole at once: if one field would look out of place beside the wallpaper, change that field, not the wallpaper. " +
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
            "places, history, architecture: Wikimedia Commons; nature and animals: iNaturalist or Wikimedia Commons). " +
            "Do not stop at the first source: the catalog has many, and the picture of a theme almost always exists in one of them. " +
            "A source has failed when it answers with an error (429 too many requests, 403, a timeout), returns nothing, or returns only results that have nothing to do with the idea " +
            "(scanned book pages, PDFs, maps, diagrams, logos); then go to the next row of the catalog at once. " +
            "On a 429 or a timeout wait a few seconds and try that same source once more, with fewer and slower requests, before leaving it; never hammer it. " +
            "Museum collections (The Met, Art Institute of Chicago, Cleveland, Rijksmuseum, SMK, Minneapolis) are the most reliable ones for a mood: " +
            "prints, lithographs, engravings and paintings of night, fog, storms, dreams or fear are often dark and fit a theme better than a photo; search them by words of the mood " +
            "(night, moonlight, nightmare, haunted, storm, fog) and of the art (lithograph, engraving, noir). A keyword search is literal, so try 3 or 4 different words before leaving a source.\n" +
            "3. Search it for the concept of the theme, in English. Prefer a portrait or tall picture at least 1080 px wide, with a calm area in the top third and the middle " +
            "(the clock, icons and text are drawn there) and the detail at the bottom and the edges. If no portrait one fits, a landscape or square picture is fine: " +
            "the phone crops it to fill the screen around its center, so choose one whose subject is in the middle. " +
            "The picture must be dark where text goes: light text and icons are drawn over the top third and the middle, so reject pictures that are bright or busy there " +
            "(a bright sky, a stained-glass ceiling, white walls); night scenes, dark interiors and deep colors work best. " +
            "Look at the picture before choosing it (download it and open it, or at least read the title and description), and when you cannot, prefer the ones the source describes as night, dark or low-key. " +
            "A scan of a print or a page has a pale paper margin and sometimes a caption: it is fine when the phone's crop to the center leaves them out or the dim covers them, " +
            "but reject it when a large white area would sit under the clock. A resolution a little under 1080 px wide is acceptable for a dark, soft picture when nothing sharper fits. " +
            "If the exact thing asked for (bats, ghosts, a given animal) has no suitable picture, take a picture of the same mood or place and let the animation or the colors carry the rest.\n" +
            "4. The link must be one you really received from that API or page: a direct https link to a .jpg, .png or .webp (not a web page, a search page or a viewer), " +
            "of at most 3 MB; a query string such as ?utm_source=... after the file name is fine. Open it to check that it loads as an image and read its size; a large PNG is often over 3 MB, so prefer a JPEG or a smaller size. " +
            "Use the size the source itself returns, as the catalog says (on Wikimedia Commons the thumburl of the API with iiurlwidth, or the upload.wikimedia.org link of the file; " +
            "do not build thumbnail links by hand, only standard sizes exist). Send an identifiable User-Agent when the catalog asks for one. " +
            "Never invent, guess or rebuild a link from memory, and never use pictures from sources outside the catalog (Google Images, Pinterest, stock sites, social networks).\n" +
            "5. Write it as \"wallpaper\": {\"url\": \"<direct link>\"}; \"lockScreen\": true may go in the same object to use it on the lock screen too. " +
            "Set showWallpaper true and wallpaperDim (0 to 1) so the text stays readable: 0.35 for a dark picture, 0.5 for a mid-tone one, 0.65 to 0.75 for a bright one " +
            "(a bright picture you could not avoid), and a little more when the subject is in the middle of the screen. " +
            "Leave out \"lockWallpaper\" unless the user wants a second picture or asks for one for the lock screen (then {\"url\": \"<link>\"}, another picture of the same world). " +
            "A customAnimation replaces \"animation\": leave \"animation\" out then; crtEffect true is what turns the animation on, crtIntensity says how strong.\n" +
            "6. Choose the picture first and take every color from it, as one palette: a dark background from its darkest tone (keep its slight tint, warm or cool, instead of pure black), " +
            "an accent from its lightest or most vivid tone (the glow of a window, the moon, the paper), still with a contrast of at least 4.5, and secondary, outline, dock and folder as shades, tints or " +
            "neighbors of those same tones. A black and white or sepia picture gets a near-monochrome palette with one accent; never put orange, purple, green or any hue over it that it does not have. " +
            "Particles, the font, iconShape, folderShape and the panel (urgent color, glow, preset) follow the picture too: grain and dust for a print, sparks for a fire, drops for rain, shapes and a typeface of its era and material. " +
            "When the picture is at odds with the colors you had in mind for the concept, the picture wins.\n" +
            "7. Put the credit in \"note\": for each picture its title, author and licence (the licence of the item, not of the site), as short as you can, one sentence per picture. " +
            "Use only items whose licence allows reuse, and give the credit when it asks for one. A picture of a real place or object is fine for a theme about a film, a book or a mood.\n" +
            "8. The SVG wallpaper is the last resort, not the plan. Draw it only after you tried at least three different sources of the catalog (and, for each, more than one search word) " +
            "and each one failed or had nothing that fits; when that happens, say in \"note\" which sources you tried and why they failed, so the user knows it was not a choice. " +
            "Never drop to the SVG because of one error, one empty search or a rate limit, and never swap a picture you were asked for (a real photo, a painting) for a drawing without trying again.\n" +
            "9. The At a Glance panel may also have a Lottie animation of its own, glance.lottie: {\"url\": \"<direct link to a .json file>\"}, with glance.animation left out. " +
            "Use it only when one would really suit the concept (a pulsing heart, a spinning gear, a glowing orb), as a small, simple, roughly square animation of at most 500 KB, " +
            "and only a file you opened and checked: a direct link to the raw .json, a licence that allows reuse (public domain, CC0, MIT or similar, shown on the page or in the repository), " +
            "that parses as JSON with \"layers\". Prefer one drawn in a single color with glance.tint true. Never invent or guess a link. Put its credit in \"note\" too. If you find none that truly adds something, do not force one: use a built-in animation only if it suits, or leave the panel's animation alone."

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
