package app.lawnchair.widgets.lottie

import android.content.Context
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory

/**
 * Built-in default animation for Lottie widgets when no custom .json/.lottie file is imported yet.
 * Shows a retro terminal monitor border with a prompt chevron (>) and a blinking block cursor.
 */
object DefaultWidgetAnimation {

    const val NAME = "default_terminal_prompt.json"

    val JSON: String = """
{
  "v": "5.5.7",
  "fr": 30,
  "ip": 0,
  "op": 60,
  "w": 200,
  "h": 200,
  "nm": "Terminal Prompt",
  "ddd": 0,
  "assets": [],
  "layers": [
    {
      "ddd": 0,
      "ind": 1,
      "ty": 4,
      "nm": "Cursor",
      "sr": 1,
      "ks": {
        "o": {
          "a": 1,
          "k": [
            { "t": 0, "s": [100], "h": 1 },
            { "t": 15, "s": [0], "h": 1 },
            { "t": 30, "s": [100], "h": 1 },
            { "t": 45, "s": [0], "h": 1 },
            { "t": 60, "s": [100] }
          ]
        },
        "r": { "a": 0, "k": 0 },
        "p": { "a": 0, "k": [116, 100, 0] },
        "a": { "a": 0, "k": [0, 0, 0] },
        "s": { "a": 0, "k": [100, 100, 100] }
      },
      "ao": 0,
      "shapes": [
        {
          "ty": "gr",
          "it": [
            {
              "ty": "rc",
              "d": 1,
              "s": { "a": 0, "k": [18, 32] },
              "p": { "a": 0, "k": [0, 0] },
              "r": { "a": 0, "k": 2 }
            },
            {
              "ty": "fl",
              "c": { "a": 0, "k": [1, 1, 1, 1] },
              "o": { "a": 0, "k": 100 },
              "r": 1,
              "bm": 0
            },
            {
              "ty": "tr",
              "p": { "a": 0, "k": [0, 0] },
              "a": { "a": 0, "k": [0, 0] },
              "s": { "a": 0, "k": [100, 100] },
              "r": { "a": 0, "k": 0 },
              "o": { "a": 0, "k": 100 },
              "sk": { "a": 0, "k": 0 },
              "sa": { "a": 0, "k": 0 }
            }
          ]
        }
      ],
      "ip": 0,
      "op": 60,
      "st": 0,
      "bm": 0
    },
    {
      "ddd": 0,
      "ind": 2,
      "ty": 4,
      "nm": "Chevron",
      "sr": 1,
      "ks": {
        "o": { "a": 0, "k": 100 },
        "r": { "a": 0, "k": 0 },
        "p": { "a": 0, "k": [80, 100, 0] },
        "a": { "a": 0, "k": [0, 0, 0] },
        "s": { "a": 0, "k": [100, 100, 100] }
      },
      "ao": 0,
      "shapes": [
        {
          "ty": "gr",
          "it": [
            {
              "ty": "sh",
              "ks": {
                "a": 0,
                "k": {
                  "i": [[0, 0], [0, 0], [0, 0]],
                  "o": [[0, 0], [0, 0], [0, 0]],
                  "v": [[-14, -20], [10, 0], [-14, 20]],
                  "c": false
                }
              }
            },
            {
              "ty": "st",
              "c": { "a": 0, "k": [1, 1, 1, 1] },
              "o": { "a": 0, "k": 100 },
              "w": { "a": 0, "k": 7 },
              "lc": 2,
              "lj": 2
            },
            {
              "ty": "tr",
              "p": { "a": 0, "k": [0, 0] },
              "a": { "a": 0, "k": [0, 0] },
              "s": { "a": 0, "k": [100, 100] },
              "r": { "a": 0, "k": 0 },
              "o": { "a": 0, "k": 100 },
              "sk": { "a": 0, "k": 0 },
              "sa": { "a": 0, "k": 0 }
            }
          ]
        }
      ],
      "ip": 0,
      "op": 60,
      "st": 0,
      "bm": 0
    },
    {
      "ddd": 0,
      "ind": 3,
      "ty": 4,
      "nm": "Screen Border",
      "sr": 1,
      "ks": {
        "o": {
          "a": 1,
          "k": [
            { "t": 0, "s": [70] },
            { "t": 30, "s": [95] },
            { "t": 60, "s": [70] }
          ]
        },
        "r": { "a": 0, "k": 0 },
        "p": { "a": 0, "k": [100, 100, 0] },
        "a": { "a": 0, "k": [0, 0, 0] },
        "s": { "a": 0, "k": [100, 100, 100] }
      },
      "ao": 0,
      "shapes": [
        {
          "ty": "gr",
          "it": [
            {
              "ty": "rc",
              "d": 1,
              "s": { "a": 0, "k": [156, 126] },
              "p": { "a": 0, "k": [0, 0] },
              "r": { "a": 0, "k": 16 }
            },
            {
              "ty": "st",
              "c": { "a": 0, "k": [1, 1, 1, 1] },
              "o": { "a": 0, "k": 100 },
              "w": { "a": 0, "k": 5 },
              "lc": 2,
              "lj": 2
            },
            {
              "ty": "tr",
              "p": { "a": 0, "k": [0, 0] },
              "a": { "a": 0, "k": [0, 0] },
              "s": { "a": 0, "k": [100, 100] },
              "r": { "a": 0, "k": 0 },
              "o": { "a": 0, "k": 100 },
              "sk": { "a": 0, "k": 0 },
              "sa": { "a": 0, "k": 0 }
            }
          ]
        }
      ],
      "ip": 0,
      "op": 60,
      "st": 0,
      "bm": 0
    }
  ]
}
    """.trimIndent()

    val bytes: ByteArray by lazy { JSON.toByteArray(Charsets.UTF_8) }

    fun load(context: Context): LottieComposition? = LottieCompositionFactory.fromJsonStringSync(JSON, "default_widget_animation").value
}
