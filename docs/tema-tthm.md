# Como criar um tema do Terminal Launcher (.tthm)

Guia para um agente de IA gerar um tema que o Terminal Launcher importa. Tudo aqui segue o código em `lawnchair/src/app/lawnchair/command/ThemeSpec.kt` (`ThemeSpecs.encode`, `decode`, `fromJson`).

## 1. Como o launcher usa as cores

O launcher imita um terminal. A cor de **destaque** (`accent`) desenha todo o texto, as linhas e os ícones, e o **fundo** (`background`) fica atrás de tudo. O par destaque/fundo decide se o tema é legível. As outras cores são detalhes.

## 2. O arquivo

| Item | Valor |
|---|---|
| Extensão | `.tthm` |
| MIME | `application/x-terminal-theme` |
| Conteúdo | Um objeto JSON em UTF-8 |
| Tamanho máximo | 64 KiB |
| Nome do arquivo | O nome do tema, com caracteres fora de letras, dígitos, `_` e `-` trocados por `_` (ex.: `Neon_noturno.tthm`) |

O usuário importa o arquivo abrindo-o no celular (o launcher responde a `VIEW`/`SEND` com esse MIME ou com a extensão `.tthm`) ou em Configurações > Aparência > Temas salvos.

## 3. Estrutura

```json
{
  "format": "terminal-theme",
  "version": 1,
  "name": "Neon noturno",
  "accent": "#33ff66",
  "background": "#07090a",
  "secondary": "#ff66cc",
  "outline": "default",
  "dock": "#0d1210",
  "folder": "default",
  "followSystem": false,
  "font": { "kind": "google", "family": "Space Mono" },
  "pixelClock": true,
  "iconEffect": true,
  "widgetEffect": true,
  "widgetIntensity": 0.6,
  "crtEffect": true,
  "crtIntensity": 0.4,
  "showWallpaper": false,
  "wallpaperDim": 0.5,
  "colorAsWallpaper": false
}
```

### Campos obrigatórios

- `"format": "terminal-theme"`: sem ele o arquivo é recusado ("not a theme").
- `"version"`: use `1`. Se omitido, vale 1; se maior que 1, o arquivo é recusado.
- Pelo menos um campo de aparência além de `name`. Um tema que não muda nada é descartado.

### Todos os outros campos são opcionais

Um campo ausente (ou `null`) fica como está no aparelho quando o tema é aplicado. Assim, `{"format":"terminal-theme","version":1,"background":"#000000"}` muda só o fundo. Para um tema completo e previsível, preencha todos.

## 4. Referência dos campos

| Campo | Tipo | O que faz |
|---|---|---|
| `name` | texto | Nome do tema, usado no comando `tema <nome>` e no nome do arquivo. Uma linha, até 32 caracteres, sem caracteres de controle. Prefira 1 a 3 palavras. |
| `accent` | cor | Cor do texto, linhas e ícones. |
| `background` | cor | Cor de fundo da tela inicial. |
| `secondary` | cor | Texto secundário, dicas e linhas. `"default"` mantém o destaque esmaecido. |
| `outline` | cor | Contorno da barra de pesquisa. |
| `dock` | cor | Fundo do dock. |
| `folder` | cor | Fundo das pastas. |
| `followSystem` | booleano | Segue o modo claro/escuro do sistema. Só ligue se isso for pedido. |
| `font` | objeto ou texto | Fonte da interface (ver seção 6). |
| `pixelClock` | booleano | Mantém a fonte pixelada no relógio. |
| `iconEffect` | booleano | Pinta os ícones dos apps na cor de destaque. |
| `widgetEffect` | booleano | Tinge os widgets. |
| `widgetIntensity` | número 0.05–1 | Força do tingimento dos widgets. |
| `crtEffect` | booleano | Linhas de varredura estilo monitor CRT. |
| `crtIntensity` | número 0.05–1 | Força do efeito CRT. |
| `showWallpaper` | booleano | Mostra o papel de parede do sistema atrás da tela inicial. |
| `wallpaperDim` | número 0–1 | Quanto escurecer o papel de parede (com `showWallpaper`). |
| `colorAsWallpaper` | booleano | Define a cor de fundo também como papel de parede do sistema. |

O tema **nunca** altera a tela de bloqueio. Não há campo para isso.

## 5. Cores

Formatos aceitos:

- `"#rrggbb"` (recomendado). Também aceita `"#rgb"` e `"#aarrggbb"`; o canal alfa é descartado e a cor fica opaca. O `#` é opcional e maiúsculas são aceitas.
- `"default"`: segue o destaque (ou o padrão do app; no fundo, o quase-preto `#07090a`).
- `"system"`: cor de destaque do sistema (Material You).
- `"wallpaper"`: cor principal do papel de parede.

Ao gravar, o launcher sempre escreve `#rrggbb` em minúsculas.

## 6. Fonte

| Forma | Exemplo |
|---|---|
| Google Fonts | `{"kind": "google", "family": "JetBrains Mono"}` |
| Fonte do sistema | `{"kind": "system", "family": "sans-serif"}` |
| Fonte padrão (IBM Plex Mono) | `{"kind": "default"}` |
| Atalho em texto (vira Google Fonts) | `"font": "VT323"` |

Regras:

- `family` deve ser o nome **exato** da família no Google Fonts.
- Só letras, dígitos, espaço, `-`, `_` e `.`, até 64 caracteres. Outro nome invalida a fonte (o campo é ignorado).
- Fontes monoespaçadas combinam melhor com o visual: JetBrains Mono, Space Mono, IBM Plex Mono, VT323, Share Tech Mono, Fira Code, Source Code Pro.
- Fontes importadas de arquivo `.ttf` não vão no tema.

## 7. Números

- Intensidades e `wallpaperDim` vão de 0 a 1. Um valor acima de 1 é lido como porcentagem (`60` vira `0.6`), mas escreva sempre de 0 a 1.
- `widgetIntensity` e `crtIntensity` têm mínimo de 0.05.

## 8. Booleanos

Use `true`/`false` do JSON. As strings `"true"`/`"false"` também são aceitas; qualquer outra coisa é ignorada.

## 9. Contraste (o mais importante)

O launcher mede o contraste WCAG entre as cores fixas (`#...`). Cores `system` e `wallpaper` não podem ser medidas de antemão.

| Par | Mínimo recomendado | Abaixo de 3.0 | Abaixo de 1.5 |
|---|---|---|---|
| `accent` × `background` | **4.5** | aplicado com aviso | considerado ilegível (um tema gerado pela IA do app é recusado) |
| `secondary` × `background` | **3.0** | aplicado com aviso | considerado ilegível |

Se `background` for `"default"`, o fundo usado no cálculo é `#07090a`.

Fórmula (a mesma de `ThemeSpecs.contrast`):

```python
def lum(hex_):
    h = hex_.lstrip('#')
    def ch(v):
        s = int(v, 16) / 255
        return s / 12.92 if s <= 0.03928 else ((s + 0.055) / 1.055) ** 2.4
    r, g, b = ch(h[0:2]), ch(h[2:4]), ch(h[4:6])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)

print(contrast('#33ff66', '#07090a'))  # ~14.9
```

Dicas de design:

- Fundo escuro com destaque claro e saturado é o visual clássico (verde fósforo, âmbar, ciano).
- Fundo claro funciona, mas o destaque precisa ser bem escuro.
- `secondary` fica melhor a cerca de um terço do círculo cromático do destaque (ex.: destaque verde, secundária magenta ou laranja).
- `dock` e `folder` costumam ser um tom levemente mais claro (fundo escuro) ou mais escuro (fundo claro) que o `background`.
- `crtEffect` acima de 0.6 fica pesado; 0.3–0.5 é o ponto confortável.

## 10. Tolerância a erros

- Valores desconhecidos ou inválidos são ignorados um a um, sem derrubar o tema.
- Campos extras (ex.: `"note"`) são ignorados.
- O arquivo não pode ser maior que 64 KiB.

## 11. Checklist antes de entregar

1. `format` é `"terminal-theme"` e `version` é `1`.
2. `name` tem 1 a 3 palavras, até 32 caracteres.
3. Todas as cores são `#rrggbb`, `default`, `system` ou `wallpaper`.
4. Contraste `accent`/`background` ≥ 4.5 e `secondary`/`background` ≥ 3.0 (calcule).
5. `font.family` existe exatamente assim no Google Fonts.
6. Intensidades entre 0.05 e 1; `wallpaperDim` entre 0 e 1.
7. `followSystem` só `true` se o usuário pediu.
8. JSON válido, salvo como `<Nome>.tthm`.

## 12. Exemplos

Âmbar retrô:

```json
{
  "format": "terminal-theme",
  "version": 1,
  "name": "Âmbar",
  "accent": "#ffb000",
  "background": "#0a0700",
  "secondary": "#4fc3f7",
  "outline": "default",
  "dock": "#151005",
  "folder": "#151005",
  "font": { "kind": "google", "family": "VT323" },
  "pixelClock": true,
  "iconEffect": true,
  "widgetEffect": true,
  "widgetIntensity": 0.5,
  "crtEffect": true,
  "crtIntensity": 0.4,
  "showWallpaper": false,
  "colorAsWallpaper": true
}
```

Papel claro (fundo claro, texto escuro):

```json
{
  "format": "terminal-theme",
  "version": 1,
  "name": "Papel",
  "accent": "#1b1b1b",
  "background": "#f4f1ea",
  "secondary": "#8a3b12",
  "outline": "default",
  "dock": "#e8e3d8",
  "folder": "#e8e3d8",
  "font": { "kind": "google", "family": "IBM Plex Mono" },
  "pixelClock": false,
  "iconEffect": true,
  "widgetEffect": false,
  "crtEffect": false,
  "showWallpaper": false
}
```

Só troca o fundo (tema parcial):

```json
{ "format": "terminal-theme", "version": 1, "name": "Preto puro", "background": "#000000" }
```
