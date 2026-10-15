# Gerar comando de API com provedor (.tcmd)

Guia autossuficiente para uma IA gerar um pacote `.tcmd` que **cadastra um provedor de API externo** e cria um ou mais comandos da barra que consultam esse provedor. O usuário importa o arquivo pela tela de importar comandos do Terminal Launcher.

Fonte da verdade no código (módulo `:commands`): `ApiProvider.kt` (provedor, autenticação, validação, JSON), `ApiCall.kt` (`ApiSpec`, formatação da resposta, requisição), `CustomActionStore.kt` (JSON da ação), `CommandPack.kt` (formato do pacote), `PackRules.kt` (regras de importação), `CommandPattern.kt` (padrão), `Variables.kt` (variáveis), `BuiltInCommand.kt` (palavras reservadas).

---

## 1. Como funciona

- O **provedor** guarda onde a API mora (`base`), quais hosts podem receber a requisição e como autenticar. A **chave/segredo nunca vai no arquivo**: o app pede na importação (ou depois, em Configurações › Ações da barra › Avançado › Provedores de API) e guarda cifrada no aparelho.
- O **comando** (ação `kind: "API"`) aponta para o provedor pelo `id` e traz só o **caminho** (`/weather?q={text}`). O app monta a URL, anexa a credencial e faz um **GET**.
- A resposta tem de ser **JSON**. O comando diz o que mostrar dela com um formato (`api.format`), com `{campo}` apontando para caminhos no JSON.
- Sem a chave configurada, a barra mostra o comando com aviso e **CONFIGURAR**, que abre o provedor.

## 2. O que perguntar ao usuário (só o que não der para inferir)

1. Qual serviço/API e qual consulta (ex.: "clima atual do OpenWeatherMap pela cidade").
2. **Palavra do comando** (`letter`) e rótulo.
3. O que o usuário digita depois da palavra: nada, um texto.
4. O que mostrar da resposta (campos).
5. Se o texto tem formato reconhecível para rodar digitado sozinho (opcional, `pattern`).

Pesquise na documentação oficial da API: URL base, endpoint, **tipo de autenticação** (cabeçalho, parâmetro de query, Bearer, Basic), nome do cabeçalho/parâmetro, se a resposta é JSON, a estrutura da resposta (nomes dos campos) e a página onde se obtém a chave.

## 3. Formato do pacote

```json
{
  "format": "terminal-commands",
  "version": 1,
  "actions": [ ... ],
  "aliases": [],
  "providers": [ ... ]
}
```

- `format` exatamente `"terminal-commands"`; `version` ≤ 1. Senão o arquivo é recusado.
- Até 256 KB no arquivo e 200 itens por lista.

## 4. Provedor (`providers[]`)

```json
{
  "id": "owm",
  "name": "OpenWeatherMap",
  "base": "https://api.openweathermap.org",
  "hosts": [],
  "auth": { "type": "key", "name": "appid", "in": "query", "prefix": "" },
  "headers": {},
  "test": "/data/2.5/weather?q=London",
  "help": "https://home.openweathermap.org/api_keys"
}
```

| Campo | Obrigatório | Regra |
|---|---|---|
| `id` | sim | `^[a-z0-9][a-z0-9_-]{0,39}$` (minúsculas, dígitos, `_`, `-`; até 40). Não pode ser `open-meteo` (embutido). |
| `name` | sim | Não vazio. Nome mostrado ao usuário. |
| `base` | sim | URL **https** com host. Os caminhos dos comandos são somados a ela. |
| `hosts` | não | Outros hosts que o provedor usa (só `[a-zA-Z0-9.-]`, sem esquema nem caminho). O host de `base` já conta. A credencial só vai para esses hosts. |
| `auth` | não | Veja abaixo. Ausente = sem autenticação. |
| `headers` | não | Cabeçalhos fixos e **não secretos** (`"Accept-Language": "pt-BR"`). `Authorization` e `Cookie` são proibidos: o provedor inteiro é descartado. |
| `test` | não | Caminho usado por "testar conexão". Vazio testa `base`. Use uma consulta barata que responda 200. |
| `help` | não | Página onde se obtém a chave. Precisa começar com `https://`, senão é descartada. |
| `pack` | — | Não envie (o app zera na importação). |

### Tipos de `auth`

| JSON | Efeito na requisição | Segredo pedido |
|---|---|---|
| `{"type": "none"}` | nada | nenhum |
| `{"type": "key", "name": "X-Api-Key", "in": "header", "prefix": ""}` | cabeçalho `X-Api-Key: <prefix><chave>` | a chave |
| `{"type": "key", "name": "appid", "in": "query", "prefix": ""}` | `?appid=<prefix><chave>` (ou `&appid=`) no fim da URL | a chave |
| `{"type": "key", "name": "Authorization", "in": "header", "prefix": "Token "}` | `Authorization: Token <chave>` | a chave |
| `{"type": "bearer"}` | `Authorization: Bearer <token>` | o token |
| `{"type": "basic", "user": "meu_usuario"}` | `Authorization: Basic base64(user:senha)` | a senha |

- `name` não pode ser vazio em `key`; `user` não pode ser vazio em `basic`.
- `in` aceita `"header"` ou `"query"` (qualquer outro valor vira `header`). `prefix` é opcional (atenção ao espaço final, ex. `"Token "`).
- Um provedor inválido é **descartado em silêncio** na importação, e os comandos que apontam para ele ficam com "provedor não encontrado". Valide antes de entregar.

## 5. Comando de API (`actions[]`)

```json
{
  "letter": "owm",
  "label": "Clima · OpenWeatherMap",
  "kind": "API",
  "template": "/data/2.5/weather?q={text}&units=metric&lang=pt_br",
  "provider": "owm",
  "packages": [],
  "arg": "TEXT",
  "sms": false,
  "action": "android.intent.action.VIEW",
  "mime": null,
  "extra": null,
  "pattern": "",
  "api": {
    "format": "{name} · {sys.country}\n{main.temp} °C · {weather.0.description}\nUmidade {main.humidity}%",
    "list": "",
    "max": 5,
    "upcoming": ""
  }
}
```

| Campo | Regra |
|---|---|
| `letter` | Palavra do comando: só letras e dígitos, minúscula, não reservada (seção 8). Já usada pelo usuário = ação ignorada na importação (ou renomeada `owm2` se ele escolher). |
| `label` | Nome mostrado. |
| `kind` | `"API"`. |
| `provider` | `id` de um provedor do pacote, ou `"open-meteo"` (embutido, não precisa estar no pacote). |
| `template` | Caminho relativo a `base` (`/v1/x?q={text}`), ou URL https completa num dos hosts do provedor. |
| `packages` | **Obrigatório** e `[]` (o campo tem de existir). |
| `arg` | `"TEXT"` (`palavra [texto]`, preenche `{text}`) ou `"NONE"` (só a palavra). Não use `CONTACT*` em API. |
| `sms`, `action`, `mime`, `extra` | Use os valores do exemplo; não têm efeito em API. |
| `pattern` | Opcional, seção 7. |
| `api` | **Obrigatório** em API. Sem ele a ação é bloqueada na importação. |

### Placeholders do `template`

- `{text}`: o que o usuário digitou, codificado para URL (espaço vira `%20`).
- `{year}`: ano atual.
- Variáveis (preenchidas e codificadas): `{date}`/`{data}`, `{time}`/`{hora}`, `{clip}`/`{copiado}` (área de transferência), `{sel}`/`{selecao}` (texto selecionado), `{location}`/`{local}` (coordenadas), `{maps}` (link do mapa). Clip, seleção e localização são **sensíveis**: a ação entra **desmarcada** na importação com aviso. Use só se a consulta precisar mesmo.

### 5.1 Comando de Abertura de Apps ou Sites (`kind: "VIEW"`)

Além de consultas a APIs externas, um pacote `.tcmd` pode conter ações para disparar aplicativos instalados ou abrir páginas web via Intent VIEW:

```json
{
  "letter": "w",
  "label": "WhatsApp",
  "kind": "VIEW",
  "template": "https://api.whatsapp.com/send?text={text}",
  "packages": ["com.whatsapp"],
  "arg": "TEXT",
  "sms": false,
  "action": "android.intent.action.VIEW",
  "mime": null,
  "extra": null,
  "pattern": ""
}
```

- `kind`: `"VIEW"`.
- `packages`: Lista opcional com os pacotes Android permitidos (ex: `["com.whatsapp"]`). Se vazio, o Android exibe o seletor padrão de apps.
- `provider` e `api`: Omitidos ou nulos (não aplicáveis a comandos VIEW).

## 6. O que mostrar (`api`)

- `format`: texto com `{caminho}` para valores do JSON.
  - Caminho com pontos: `{main.temp}`, `{address.city}`; índice de array com número: `{weather.0.description}`, `{0.name}`.
  - `{campo|date}`: data ISO (`2026-11-15` ou `2026-11-15T...`) vira data curta local + dia da semana ("15/11/2026 dom").
  - Decimais são formatados sem zeros inúteis. Objetos/arrays num campo saem vazios.
  - `\n` separa linhas. Dentro de uma linha, partes separadas por ` · ` (espaço, ponto médio, espaço) **somem quando todos os campos delas estão vazios**; uma linha vazia some. Use isso para campos opcionais.
- `list`: caminho de um array na resposta; cada item é mostrado com `format` (os caminhos passam a ser relativos ao item). Vazio: se a raiz for array, cada elemento é um item; senão a raiz é o único item.
- `max`: itens mostrados, 1 a 20 (padrão 5).
- `upcoming`: nome do campo de data ISO de cada item; itens com data anterior a hoje são omitidos.
- `method`: método HTTP (`GET` por padrão, ou `POST`). Os métodos `PUT`, `PATCH` e `DELETE` não são suportados.
- `body`: corpo enviado no `POST` (opcional).
- Resposta não JSON, fora de 2xx, ou maior que 256 KB = erro na barra. 401/403 viram "recusou as credenciais". Tempo limite de 10 s; até 3 redirecionamentos, que com credencial só podem ir para hosts do provedor.

## 7. `pattern` (opcional)

Regex Java que o texto digitado **sozinho** (sem a palavra) precisa casar **inteiro** para oferecer o comando; o texto vira `{text}`. Até 200 caracteres. Recusada (e removida na importação) se inválida ou se casar com `""`, `" "`, `"a"`, `"1"`, `"abrir chrome"`, `"hello world"`, `"tema escuro"`, `"12*3"`. Use só para formatos claros (CEP `\d{5}-\d{3}`, código de rastreio, placa, ISBN).

## 8. Palavras reservadas (não podem ser `letter`)

`t`, `c` e, em português e inglês (sem acento): abrir/open, alarme/alarm, calc, ligar/call, rota/route, dividir/split, tema/theme, timer, lembrar/remind, wifi, bluetooth/bt, lanterna/flashlight/torch, silencio/dnd, brilho/brightness, rotacao/rotation, volume/vol, tocar/play, pausar/pause, proxima/next, anterior/previous, copiar/copy, colar/paste, config/settings, agenda, nota/note, agendados/scheduled, cancelar/cancel, bateria/battery, armazenamento/storage, ip, uptime, ping, rede/network/net, fast/speedtest, ia/ai, qr, perto/nearby, clima/weather/tempo, liberar/cleanup, desinstalar/uninstall, atualizar/update, pacote/pack.

## 9. O que acontece na importação

- O app mostra, por ação, **o que sai do aparelho e para qual host** ("o que você digitar · sua localização").
- Provedor novo: entra e o app oferece digitar a chave na hora.
- Mesmo `id`, mesmo host e mesma autenticação de um provedor que o usuário já tem: usa o do usuário (e a chave dele).
- Mesmo `id` com outro host ou autenticação: entra renomeado (`owm-2`) e os comandos do pacote passam a apontar para ele. Escolha `id`s específicos para evitar isso.
- Nada do usuário é substituído.

## 10. Checklist antes de entregar

- [ ] `format`, `version`, `actions`, `aliases`, `providers` presentes.
- [ ] Provedor: `id` válido, `name`, `base` https, `auth` correto conforme a documentação da API, sem `Authorization`/`Cookie` em `headers`, `help` https, `test` que responde 200.
- [ ] Ação: `kind: "API"`, `provider` igual ao `id`, `packages: []`, `api.format` presente, `letter` válida e não reservada.
- [ ] `arg: "TEXT"` ⇔ `{text}` no `template`; `arg: "NONE"` sem `{text}`.
- [ ] O caminho somado à `base` dá a URL documentada; host final está em `base`/`hosts`.
- [ ] Nomes de campos do `format` conferem com uma resposta real (exemplo da documentação). Se possível, chame a API e confira.
- [ ] **Nenhuma chave, token ou senha no arquivo.**
- [ ] Rodou o validador (seção 12) sem erro.

Diga ao usuário: importe o arquivo pela tela de importar comandos, cole a chave quando pedir (ou depois em Provedores de API) e use "testar conexão".

## 11. Exemplos

### Chave em cabeçalho + lista (TMDB, busca de filmes)

```json
{
  "format": "terminal-commands",
  "version": 1,
  "actions": [
    {
      "letter": "filme",
      "label": "TMDB · filme",
      "kind": "API",
      "template": "/3/search/movie?query={text}&language=pt-BR",
      "provider": "tmdb",
      "packages": [],
      "arg": "TEXT",
      "sms": false,
      "action": "android.intent.action.VIEW",
      "mime": null,
      "extra": null,
      "pattern": "",
      "api": { "format": "{title} · {release_date|date} · ★ {vote_average}", "list": "results", "max": 5, "upcoming": "" }
    }
  ],
  "aliases": [],
  "providers": [
    {
      "id": "tmdb",
      "name": "TMDB",
      "base": "https://api.themoviedb.org",
      "hosts": [],
      "auth": { "type": "bearer" },
      "headers": {},
      "test": "/3/configuration",
      "help": "https://www.themoviedb.org/settings/api"
    }
  ]
}
```

### Sem autenticação, sem argumento, com datas futuras (BrasilAPI, feriados)

```json
{
  "letter": "feriados",
  "label": "Feriados",
  "kind": "API",
  "template": "/api/feriados/v1/{year}",
  "provider": "brasilapi",
  "packages": [],
  "arg": "NONE",
  "sms": false,
  "action": "android.intent.action.VIEW",
  "mime": null,
  "extra": null,
  "pattern": "",
  "api": { "format": "{date|date} · {name}", "list": "", "max": 6, "upcoming": "date" }
}
```

com o provedor `{"id": "brasilapi", "name": "BrasilAPI", "base": "https://brasilapi.com.br", "test": "/api/feriados/v1/2026"}`.

### Basic (usuário fixo, senha como segredo)

```json
"auth": { "type": "basic", "user": "meu_usuario" }
```

## 12. Validador

Grave em um arquivo temporário fora da pasta de saída e rode com `python3 -I validar_tcmd.py pacote.tcmd`.

```python
#!/usr/bin/env python3
"""Valida um .tcmd com provedores de API com as mesmas regras do Terminal Launcher."""
import json
import re
import sys
from urllib.parse import urlparse

RESERVED = {
    "t", "c", "abrir", "open", "alarme", "alarm", "calc", "ligar", "call", "rota", "route", "dividir", "split",
    "tema", "theme", "timer", "lembrar", "remind", "wifi", "bluetooth", "bt", "lanterna", "flashlight", "torch",
    "silencio", "dnd", "brilho", "brightness", "rotacao", "rotation", "volume", "vol", "tocar", "play", "pausar",
    "pause", "proxima", "next", "anterior", "previous", "copiar", "copy", "colar", "paste", "config", "settings",
    "agenda", "nota", "note", "agendados", "scheduled", "cancelar", "cancel", "bateria", "battery",
    "armazenamento", "storage", "ip", "uptime", "ping", "rede", "network", "net", "fast", "speedtest", "ia", "ai",
    "qr", "perto", "nearby", "clima", "weather", "tempo", "liberar", "cleanup", "desinstalar", "uninstall",
    "atualizar", "update", "pacote", "pack",
}
BUILT_IN_PROVIDERS = {"open-meteo": {"api.open-meteo.com", "geocoding-api.open-meteo.com"}}
ORDINARY = ["", " ", "a", "1", "abrir chrome", "hello world", "tema escuro", "12*3"]
ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,39}$")
HOST = re.compile(r"^[a-zA-Z0-9.-]+$")
SECRETISH = re.compile(r"(api[_-]?key|token|secret|senha|password)=[^&{]+", re.I)
errors = []


def err(msg):
    errors.append(msg)


def https_host(url):
    u = urlparse(url.strip())
    return u.hostname.lower() if u.scheme.lower() == "https" and u.hostname else None


def check_provider(p):
    pid = p.get("id", "")
    where = f"provedor {pid!r}"
    if not ID.match(pid):
        err(f"{where}: id inválido")
    if pid in BUILT_IN_PROVIDERS:
        err(f"{where}: id de provedor embutido")
    if not str(p.get("name", "")).strip():
        err(f"{where}: sem name")
    host = https_host(p.get("base", ""))
    if not host:
        err(f"{where}: base precisa ser https com host")
    hosts = p.get("hosts", [])
    for h in hosts:
        if not HOST.match(h.strip()):
            err(f"{where}: host inválido {h!r}")
    auth = p.get("auth") or {"type": "none"}
    t = auth.get("type", "none")
    if t not in ("none", "key", "bearer", "basic"):
        err(f"{where}: auth.type desconhecido {t!r} (vira none)")
    if t == "key":
        if not str(auth.get("name", "")).strip():
            err(f"{where}: auth key sem name")
        if auth.get("in", "header") not in ("header", "query"):
            err(f"{where}: auth.in deve ser header ou query")
    if t == "basic" and not str(auth.get("user", "")).strip():
        err(f"{where}: auth basic sem user")
    for k in p.get("headers", {}):
        if k.lower() in ("authorization", "cookie"):
            err(f"{where}: cabeçalho {k} proibido (use auth)")
    if p.get("help") and not p["help"].startswith("https://"):
        err(f"{where}: help precisa começar com https://")
    if SECRETISH.search(json.dumps(p)):
        err(f"{where}: parece conter um segredo")
    return pid, {h.strip().lower() for h in hosts} | ({host} if host else set())


def check_action(a, providers):
    letter = str(a.get("letter", "")).strip().lower()
    where = f"ação {letter!r}"
    for f in ("letter", "label", "kind", "template", "packages", "arg"):
        if f not in a:
            err(f"{where}: falta {f}")
    if not letter or not all(ch.isalnum() for ch in letter):
        err(f"{where}: letter só com letras e dígitos")
    if letter in RESERVED:
        err(f"{where}: palavra reservada")
    if a.get("kind") != "API":
        return
    pid = a.get("provider", "")
    if not pid:
        err(f"{where}: sem provider")
    elif pid not in providers:
        err(f"{where}: provider {pid!r} não está no pacote")
    api = a.get("api")
    if not isinstance(api, dict) or not str(api.get("format", "")).strip():
        err(f"{where}: falta api.format")
    elif not 1 <= int(api.get("max", 5)) <= 20:
        err(f"{where}: api.max fora de 1..20 (será limitado)")
    tpl = a.get("template", "").strip()
    arg = a.get("arg")
    if arg not in ("TEXT", "NONE"):
        err(f"{where}: arg de API deve ser TEXT ou NONE")
    if arg == "TEXT" and "{text}" not in tpl:
        err(f"{where}: arg TEXT sem {{text}} no template")
    if arg == "NONE" and "{text}" in tpl:
        err(f"{where}: arg NONE com {{text}} no template")
    if pid in providers and providers[pid]:
        if tpl.lower().startswith("http"):
            h = https_host(re.sub(r"\{[^{}]*\}", "x", tpl))
            if h not in providers[pid]:
                err(f"{where}: URL fora dos hosts do provedor")
    if re.search(r"\{(clip|copiado|area|sel|selection|selecao|location|local|loc|maps|mapa)\}", tpl):
        print(f"AVISO: {where} envia dado sensível; entra desmarcada na importação")
    pattern = a.get("pattern", "")
    if pattern:
        try:
            rx = re.compile(pattern)
            if len(pattern) > 200 or any(rx.fullmatch(s) for s in ORDINARY):
                err(f"{where}: pattern longo ou abrangente demais (será descartado)")
        except re.error as e:
            err(f"{where}: pattern inválido: {e}")


def main():
    raw = open(sys.argv[1], encoding="utf-8").read()
    if len(raw.encode()) > 256 * 1024:
        err("arquivo maior que 256 KB")
    pack = json.loads(raw)
    if pack.get("format") != "terminal-commands" or pack.get("version", 1) > 1:
        err("format/version inválidos")
    providers = dict(BUILT_IN_PROVIDERS)
    for p in pack.get("providers", []):
        pid, hosts = check_provider(p)
        providers[pid] = hosts
    letters = set()
    for a in pack.get("actions", []):
        check_action(a, providers)
        if a.get("letter") in letters:
            err(f"letter repetida {a.get('letter')!r}")
        letters.add(a.get("letter"))
    if errors:
        print("\n".join("ERRO: " + e for e in errors))
        sys.exit(1)
    print(f"OK: {len(pack.get('actions', []))} ação(ões), {len(pack.get('providers', []))} provedor(es)")


if __name__ == "__main__":
    main()
```
