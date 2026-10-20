package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.copyToClipboard
import com.android.launcher3.R

private const val GITHUB_GUIDE_URL =
    "https://github.com/mauriciodantas/Terminal-Laucher/blob/16-dev/docs/gerar-comando-api-provedor.md"

internal const val AI_MASTER_PROMPT = """Você é um especialista em automação e comandos do Terminal Launcher para Android.
Sua tarefa é gerar comandos para a barra e pacotes no formato .tcmd (arquivo JSON).

SINTAXE DA LINHA DE COMANDO:
1. Links:
   NOME https://exemplo.com/busca?q={text} # Rótulo
2. Abrir apps e atalhos:
   NOME am start -p com.pacote.app # Rótulo
   NOME am start -p com.pacote.app --shortcut id_do_atalho # Rótulo
3. Disparar intents e broadcasts:
   NOME am start -a android.intent.action.VIEW -d geo:0,0?q={text} # Mapa
   NOME am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR {1} --ei android.intent.extra.alarm.MINUTES {2} --ez android.intent.extra.alarm.SKIP_UI true # Despertar
   NOME am broadcast -a com.exemplo.ACAO --es chave valor # Broadcast
4. Consultar APIs:
   NOME curl @provedor/caminho [-X METHOD] [-H "Header: Valor"] [-d corpo] [--type json|form|text] [--list items] [--max 5] [--upcoming data] [--paginate cursor|next-url|offset|page|link] [--page-param nome] [--page-next caminho] [--pages 3] [--format 'FORMATO'] # Rótulo
   (o FORMATO vai entre aspas simples em --format; "|" fora de aspas encadeia comandos e não é mais o formato)
5. Scripts (JavaScript isolado, sem rede nem acesso ao aparelho):
   NOME node -p 'EXPRESSÃO' # Rótulo
   NOME node -e 'function main(input, ctx) { return input.text; }' # Rótulo
6. Apelidos:
   alias NOME EXPANSÃO
   Uma expansão pode encadear comandos: alias cepmae cep {text} | w mae

REGRAS OBRIGATÓRIAS DO PACOTE .tcmd:
1. ESTRUTURA GERAL:
O arquivo deve ser exclusivamente um JSON válido, codificado em UTF-8, com a seguinte raiz:
{
  "format": "terminal-commands",
  "version": 1,
  "actions": [],
  "aliases": [],
  "providers": []
}

2. DIRETRIZ DE SEGURANÇA (CRÍTICO):
NUNCA inclua nenhuma chave de API, token ou senha no arquivo.
O Terminal Launcher solicita a chave ao usuário durante a importação do pacote e a armazena de forma criptografada e segura no dispositivo.

3. DEFINIÇÃO DE PROVEDOR (providers[]):
{
  "id": "identificador_unico",
  "name": "Nome do Provedor",
  "base": "https://api.exemplo.com",
  "hosts": [],
  "auth": { "type": "none" },
  "headers": {},
  "test": "/status",
  "help": "https://exemplo.com/api-keys"
}
- "id": Apenas letras minúsculas, números, '_' e '-'. Não pode ser 'open-meteo' (embutido).
- "base": URL https com host. Caminhos dos comandos serão somados a ela.
- "hosts": Hosts adicionais que podem receber a credencial (sem https://).
- "auth": Tipo de autenticação:
  * {"type": "none"}
  * {"type": "key", "name": "X-Api-Key", "in": "header"|"query", "prefix": ""}
  * {"type": "bearer"} -> Cabeçalho Authorization: Bearer <token>
  * {"type": "basic", "user": "usuario"} -> Authorization: Basic base64(user:senha)
- "headers": Cabeçalhos fixos estáticos não confidenciais (ex: "Accept-Language": "pt-BR"). NUNCA use "Authorization" ou "Cookie" em headers; use "auth".
- "test": Rota GET rápida que retorna 200 para teste de conexão.
- "help": Link https onde o usuário obtém a chave de API.

4. DEFINIÇÃO DE COMANDO DE API (actions[]):
{
  "letter": "palavra",
  "label": "Rótulo do comando",
  "kind": "API",
  "template": "/endpoint?q={text}",
  "provider": "identificador_unico",
  "packages": [],
  "arg": "TEXT",
  "pattern": "",
  "api": {
    "method": "GET",
    "format": "{campo.subcampo} · {0.titulo}\n{outro}",
    "list": "items",
    "max": 5,
    "upcoming": "",
    "page": {"type": "cursor", "param": "cursor", "next": "meta.next", "pages": 3},
    "headers": {},
    "body": "",
    "bodyType": "JSON"
  }
}
- "letter": Palavra do comando na barra (apenas letras e dígitos, minúsculas). Não use palavras reservadas do sistema (calc, clima, tempo, abrir, wifi, bluetooth, lanterna, silencio, brilho, volume, tocar, copiar, colar, config, agenda, nota, cancelar, bateria, armazenamento, ip, ping, rede, fast, ia, qr, am, curl, node, etc.).
- "kind": "API".
- "provider": Deve coincidir com o "id" de um provedor em providers[].
- "arg": "TEXT" se o comando usa o que for digitado ({text}), ou "NONE" se roda sozinho.
- "template" e "api.body" aceitam {stdin}: a saída do comando anterior quando o comando vem depois de um "|" na barra (vazio fora disso). Um comando que só usa {stdin} tem "arg": "NONE".
- "packages": Deve ser obrigatoriamente [] em API.
- "api.method": "GET", "POST", "PUT", "PATCH" ou "DELETE".
- "api.format": String de formatação da resposta. Chaves {campo} apontam para o JSON da resposta (com pontos para aninhamento: {main.temp}, {weather.0.description}).
  * Suporta {campo|date} para converter datas ISO em data amigável local.
  * Partes separadas por ' · ' (espaço, ponto médio, espaço) são omitidas se os campos estiverem vazios.
- "api.list": Caminho do array se a resposta for uma lista (ex: "results").
- "api.max": Quantidade de itens mostrados (1 a 20, padrão 5).
- "api.upcoming": Nome do campo de data ISO para omitir eventos passados (opcional).
- "api.page": Lê várias páginas da resposta e as junta numa lista só (opcional; omita se a API não pagina). "type": "cursor" (a resposta traz o cursor da próxima página em "next"; é enviado como query "param"), "next-url" (a resposta traz em "next" o endereço da próxima), "offset" (envia em "param" a quantidade de itens já lidos), "page" (envia em "param" o número da página, começando em 2) ou "link" (usa o cabeçalho Link rel="next"). "pages": no máximo de páginas, de 2 a 10 (padrão 3). Em "offset" e "page", "next" é opcional: um caminho que fica vazio ou false na última página. Para GraphQL (POST), escreva {cursor} no corpo, entre aspas se for texto, e use "cursor", "offset" ou "page". Para de ler quando já há "max" itens a mostrar (aumente "max" para juntar mais páginas de uma vez); na barra, o botão [MAIS] lê o próximo lote de "max" itens e o põe depois dos já mostrados. Exige "list" (ou resposta que seja um array).
- "api.headers": Cabeçalhos específicos desta chamada (opcional).
- "api.body": Corpo da requisição quando method != GET (opcional).
- "api.bodyType": "JSON", "FORM" ou "TEXT" (opcional).

5. DEFINIÇÃO DE COMANDO INTENT / APP / SHORTCUT (actions[]):
{
  "letter": "w",
  "label": "WhatsApp",
  "kind": "INTENT",
  "template": "https://api.whatsapp.com/send?text={text}",
  "packages": ["com.whatsapp"],
  "arg": "TEXT",
  "target": "activity",
  "categories": [],
  "component": null,
  "extras": [
    { "key": "android.intent.extra.TEXT", "type": "string", "value": "{text}" }
  ]
}
- "kind": "INTENT", "APP" ou "SHORTCUT".
- "target": "activity" (padrão) ou "broadcast".
- "categories": Lista de categorias de Intent (ex: ["android.intent.category.BROWSABLE"]).
- "component": Componente explícito "pacote/classe" (opcional).
- "extras": Lista de extras com "key", "type" ("string"|"int"|"boolean"|"long"|"float"|"uri") e "value".
- "template" e os valores de "extras" aceitam {stdin} como nas APIs.

6. DEFINIÇÃO DE COMANDO SCRIPT (actions[]), JavaScript isolado:
{
  "letter": "dias",
  "label": "dias até uma data",
  "kind": "SCRIPT",
  "template": "",
  "packages": [],
  "arg": "TEXT",
  "script": { "source": "function main(input, ctx) {\n  const d = Math.ceil((new Date(input.args[0]) - Date.now()) / 864e5);\n  return d + ' dias';\n}" }
}
- "script" tem exatamente um dos campos: "expr" (uma expressão, como em node -p: { "expr": "input.text.toUpperCase()" }) ou "source" (um programa, como em node -e, que declara function main(input, ctx)). Até 64 KB.
- "arg": sempre "TEXT" (o texto pode vir vazio). "template": "" e "packages": [].
- Entrada: input.text (o que veio depois do nome), input.args (input.text em palavras), input.stdin ({ text, data } do comando anterior num "|", ou null), ctx.vars (só date e time), ctx.locale e ctx.command.
- Retorno de main: texto, número ou booleano viram o texto mostrado; um objeto com "text", "data" ou "action" usa esses campos; outro objeto ou lista vira "data"; null ou nada é sucesso sem texto; throw é erro. main pode ser async.
- "action" (só vale no último comando de uma cadeia): { "open": "https://..." }, { "copy": "texto" }, { "share": "texto" } ou { "run": "linha da barra" }. Quem executa é a barra, com as confirmações dela.
- O script NÃO tem rede, arquivos, DOM, fetch, setTimeout nem acesso ao Android: só ECMAScript. Para buscar dados, use um comando API antes dele com "|".
- Limites: 2 s de execução, 16 MB de memória, 256 KB de entrada, 64 KB de texto e 256 KB de dados na saída.
- Um Script importado entra desmarcado: o usuário lê o código antes de habilitar.

7. APELIDOS E ENCADEAMENTO (aliases[]):
{ "name": "cepmae", "expansion": "cep {text} | w mae" }
- Na expansão, {text} recebe o que for digitado depois do apelido (sem {text}, o texto vai para o fim).
- Operadores, como no shell: "a ; b" (sempre), "a && b" (só se a deu certo), "a || b" (só se a falhou), "a | b" (b recebe a saída de a). "|" liga mais forte que "&&" e "||", que ligam mais forte que ";". Entre aspas, são texto.
- Depois de um "|", um comando digitado sem texto recebe a saída anterior em {text}; com texto, o digitado ganha e a saída fica em {stdin}.
- "alias" e "unalias" não podem fazer parte de uma cadeia.

DOCUMENTAÇÃO DA API A INTEGRAR:
[Forneça aqui a documentação da API, URL base, endpoint desejado e estrutura de resposta JSON]"""

@Composable
fun CommandGuidePreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val promptCopiedMessage = stringResource(R.string.cmd_guide_prompt_copied)

    PreferenceLayout(
        label = stringResource(R.string.cmd_guide_title),
        modifier = modifier,
        actions = {
            ClickableIcon(
                imageVector = Icons.Rounded.ContentCopy,
                onClick = {
                    copyToClipboard(
                        context = context,
                        text = AI_MASTER_PROMPT,
                        label = "Prompt Terminal Launcher (.tcmd)",
                        toastMessage = promptCopiedMessage,
                    )
                },
            )
            ClickableIcon(
                imageVector = Icons.Rounded.OpenInBrowser,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_GUIDE_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        },
    ) {
        // Quick Action Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.cmd_guide_copy_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cmd_guide_copy_prompt_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            copyToClipboard(
                                context = context,
                                text = AI_MASTER_PROMPT,
                                label = "Prompt Terminal Launcher (.tcmd)",
                                toastMessage = promptCopiedMessage,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cmd_guide_copy_prompt))
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_GUIDE_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cmd_guide_github_btn))
                    }
                }
            }
        }

        // Section 1: Introduction
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_intro)) {
            GuideTextCard(
                text = "Um pacote de comandos (.tcmd) é um arquivo JSON compacto (até 256 KB) que define provedores de API externos, comandos de consulta e atalhos rápidos.\n\n" +
                    "Ao receber ou criar um arquivo .tcmd, você pode importá-lo diretamente pelo Terminal Launcher em Configurações › Ações da barra › Backup › Importar comandos, ou simplesmente abrindo o arquivo a partir de qualquer aplicativo de mensagens ou gerenciador de arquivos.",
            )
            JsonCodeCard(
                json = """{
  "format": "terminal-commands",
  "version": 1,
  "actions": [ /* comandos */ ],
  "aliases": [ /* apelidos */ ],
  "providers": [ /* serviços de API */ ]
}""",
            )
        }

        // Section: Command-line Syntax
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_syntax)) {
            GuideTextCard(
                text = "Você pode cadastrar e editar comandos na barra usando sintaxe de linha ou no arquivo .tcmd:\n\n" +
                    "• Links diretos:\n  g https://www.google.com/search?q={text} # Google\n\n" +
                    "• Abrir apps e atalhos:\n  w am start -p com.whatsapp # WhatsApp\n  lanterna am start -p com.android.systemui --shortcut torch # Lanterna\n\n" +
                    "• Intents e Broadcasts:\n  alarme am start -a android.intent.action.SET_ALARM --ei android.intent.extra.alarm.HOUR {1} --ei android.intent.extra.alarm.MINUTES {2} --ez android.intent.extra.alarm.SKIP_UI true # Despertar\n  notif am broadcast -a com.exemplo.AVISO --es msg \"{text}\" # Notificar\n\n" +
                    "• Consultar APIs (curl):\n  cep curl @viacep/ws/{text}/json/ --format '{logradouro} · {bairro} · {localidade}/{uf}' # CEP\n  O formato vai em --format, entre aspas simples; \\n quebra a linha. O \"|\" fora de aspas encadeia comandos.\n\n" +
                    "• Scripts (node):\n  conta node -p 'eval(input.text)' # Calculadora\n  maiusc node -e 'function main(input) { return input.text.toUpperCase(); }'\n\n" +
                    "• Apelidos (aliases):\n  alias casa rota casa\n  alias cepmae cep {text} | w mae\n\n" +
                    "Aspas: dentro de aspas simples só \\' (uma aspa) e \\\\ (uma barra) são escapes; as outras barras ficam como estão, para o código ler como foi escrito. Isso difere do shell POSIX de propósito. Dentro de aspas a linha pode ter quebras.\n\n" +
                    "Palavras reservadas: am, curl e node não podem ser nome de comando. Os operadores |, &&, || e ; fora de aspas só valem num apelido.\n\n" +
                    "Flags suportadas: -a (ação), -d (URI/dados), -t (MIME), -c (categoria), -n (componente), -p (pacote), --shortcut, --sms e extras (--es, --ei, --ez, --el, --ef, --eu).",
            )
        }

        // Section 2: Providers
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_providers)) {
            GuideTextCard(
                text = "O provedor define onde a API reside e como se autenticar. A chave ou segredo NUNCA deve ser incluída no arquivo — o aplicativo solicita a chave na importação e guarda criptografada no aparelho.",
            )
            JsonCodeCard(
                json = """{
  "id": "tmdb",
  "name": "The Movie Database",
  "base": "https://api.themoviedb.org",
  "hosts": [],
  "auth": {
    "type": "bearer"
  },
  "headers": {},
  "test": "/3/configuration",
  "help": "https://www.themoviedb.org/settings/api"
}""",
            )
            GuideTextCard(
                text = "Tipos de autenticação suportados:\n" +
                    "• none: Sem chave pública\n" +
                    "• bearer: Authorization: Bearer <token>\n" +
                    "• key (header ou query): chave via cabeçalho ou parâmetro de URL (?appid=...)\n" +
                    "• basic: Authorization: Basic com usuário fixo e senha solicitada",
            )
        }

        // Section 3: API Actions
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_actions)) {
            GuideTextCard(
                text = "Uma ação de API (kind: \"API\") associa uma palavra-chave (letter) a um endpoint do provedor e especifica como formatar o JSON de resposta.",
            )
            JsonCodeCard(
                json = """{
  "letter": "filme",
  "label": "TMDB · Busca de filmes",
  "kind": "API",
  "template": "/3/search/movie?query={text}&language=pt-BR",
  "provider": "tmdb",
  "packages": [],
  "arg": "TEXT",
  "pattern": "",
  "api": {
    "method": "GET",
    "format": "{title} · {release_date|date} · ★ {vote_average}",
    "list": "results",
    "max": 5,
    "headers": {
      "Accept": "application/json"
    }
  }
}""",
            )
            GuideTextCard(
                text = "Campos e regras de formatação (api):\n" +
                    "• method: GET, POST, PUT, PATCH ou DELETE.\n" +
                    "• format: Template com {campo} para extrair dados. Suporta objetos {a.b}, arrays {items.0.nome} e filtro de data {campo|date}.\n" +
                    "• Partes separadas por ' · ' são automaticamente omitidas quando os dados estão vazios.\n" +
                    "• list: Nome do array na resposta JSON.\n" +
                    "• max: Limite de itens na barra (1 a 20).\n" +
                    "• upcoming: Campo de data ISO para omitir eventos passados.\n" +
                    "• page: Paginação (type cursor, next-url, offset, page ou link; param, next e pages de 2 a 10). Na linha: --paginate, --page-param, --page-next e --pages. Para em max itens; [MAIS] na barra traz o próximo lote.\n" +
                    "• headers: Cabeçalhos específicos desta chamada HTTP.\n" +
                    "• body e bodyType: Corpo da requisição para métodos POST/PUT/PATCH (JSON, FORM ou TEXT).",
            )
        }

        // Section 4: VIEW / INTENT Actions
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_view_actions)) {
            GuideTextCard(
                text = "Ações do tipo INTENT, APP ou SHORTCUT abrem aplicativos instalados, atalhos ou URLs usando intents do Android.",
            )
            JsonCodeCard(
                json = """{
  "letter": "despertar",
  "label": "Alarme",
  "kind": "INTENT",
  "template": "",
  "action": "android.intent.action.SET_ALARM",
  "packages": [],
  "arg": "TEXT",
  "target": "activity",
  "categories": [],
  "component": null,
  "extras": [
    { "key": "android.intent.extra.alarm.HOUR", "type": "int", "value": "{1}" },
    { "key": "android.intent.extra.alarm.MINUTES", "type": "int", "value": "{2}" },
    { "key": "android.intent.extra.alarm.SKIP_UI", "type": "boolean", "value": "true" }
  ]
}""",
            )
            GuideTextCard(
                text = "Campos de intents:\n" +
                    "• target: \"activity\" (padrão) para abrir telas ou \"broadcast\" para mensagens em segundo plano.\n" +
                    "• categories: Lista de categorias do intent (ex: [\"android.intent.category.DEFAULT\"]).\n" +
                    "• component: Componente explícito \"pacote/classe\" (opcional).\n" +
                    "• extras: Lista de extras tipados (tipos: string, int, boolean, long, float, uri).",
            )
        }

        // Section: Scripts
        PreferenceGroup(heading = "Scripts (JavaScript)") {
            GuideTextCard(
                text = "Um Script roda JavaScript num sandbox isolado do aparelho (o V8 do WebView, em outro processo): só ECMAScript, sem rede, arquivos, DOM, fetch ou setTimeout. Ele só calcula e devolve dados.\n\n" +
                    "Todo script define function main(input, ctx). node -p 'EXPRESSÃO' vira main que devolve a expressão; node -e 'PROGRAMA' precisa declarar main.\n" +
                    "• input.text: o que veio depois do nome (\"\" se nada)\n" +
                    "• input.args: input.text em palavras, como {1}…{9}\n" +
                    "• input.stdin: { text, data } do comando anterior num \"|\", ou null\n" +
                    "• ctx.vars: só variáveis não sensíveis ({date}, {time}); {clip}, {sel} e {location} nunca entram\n" +
                    "• ctx.locale (ex. pt-BR) e ctx.command (o nome)\n\n" +
                    "main pode ser async quando o aparelho permite. O que ela devolve vira o resultado:\n" +
                    "• nada ou null: sucesso sem texto\n" +
                    "• texto, número ou booleano: o texto mostrado\n" +
                    "• objeto com text, data ou action: esses campos\n" +
                    "• outro objeto ou lista: data\n" +
                    "• throw: erro, com a mensagem e a linha\n\n" +
                    "action pede à barra, no fim da cadeia: { open: URI }, { copy: texto }, { share: texto } ou { run: linha }. Quem executa é a barra, com as confirmações de sempre; run não aceita alias/unalias e encadeia no máximo 3 vezes.\n\n" +
                    "Limites: 2 s de execução, 16 MB de memória, 64 KB de código, 256 KB de entrada, 64 KB de texto e 256 KB de dados na saída. Sem o sandbox no aparelho o script é salvo, mas não roda.\n\n" +
                    "No .tcmd, kind SCRIPT, script e {stdin} são opcionais e o pacote continua version 1. Uma versão antiga do app ignora só os comandos SCRIPT (descarta o kind que não conhece) e importa o resto. Na importação, um Script entra desmarcado: toque em Ver código antes de marcá-lo.",
            )
            JsonCodeCard(
                json = """{
  "letter": "dias",
  "label": "dias até uma data",
  "kind": "SCRIPT",
  "arg": "TEXT",
  "template": "",
  "packages": [],
  "script": { "source": "function main(input, ctx) {\n  const d = Math.ceil((new Date(input.args[0]) - Date.now()) / 864e5);\n  return `${'$'}{d} dias`;\n}" }
}""",
            )
        }

        // Section: Chains
        PreferenceGroup(heading = "Encadeamento") {
            GuideTextCard(
                text = "Na barra, como no shell:\n" +
                    "• a ; b  executa b sempre\n" +
                    "• a && b  executa b só se a deu certo\n" +
                    "• a || b  executa b só se a falhou (clima || w mae sem internet)\n" +
                    "• a | b  b recebe a saída de a (cep 88101000 | w mae)\n\n" +
                    "| liga mais forte que && e ||, que ligam mais forte que ;. Numa sequência de |, a primeira falha para tudo. Operadores entre aspas são texto.\n\n" +
                    "Depois de um |, o comando recebe a saída anterior em {text} quando foi digitado sem texto, e sempre em {stdin}; o que foi digitado ganha. Um Script lê input.stdin. Só o último comando pode terminar com uma action. Se um passo altera dados (POST...), a prévia fica amarela e o Enter vale para a cadeia inteira.",
            )
        }

        // Section 5: Placeholders
        PreferenceGroup(heading = "Placeholders e Variáveis") {
            GuideTextCard(
                text = "Você pode usar variáveis no template da URL:\n" +
                    "• {text}: Texto digitado após o comando (codificado para URL)\n" +
                    "• {stdin}: Saída do comando anterior num \"|\" (vazio sem ele); um destino que só usa {stdin} não pede argumento\n" +
                    "• {year}: Ano corrente (ex: 2026)\n" +
                    "• {date}: Data atual\n" +
                    "• {time}: Hora atual\n" +
                    "• {clip}: Conteúdo da área de transferência (sensível)\n" +
                    "• {location}: Coordenadas GPS (sensível)",
            )
        }

        // Section 6: Full Examples
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_examples)) {
            GuideTextCard(
                text = "Exemplo completo de pacote com comando público sem chave (Feriados Nacionais via BrasilAPI):",
            )
            JsonCodeCard(
                json = """{
  "format": "terminal-commands",
  "version": 1,
  "actions": [
    {
      "letter": "feriados",
      "label": "Feriados Nacionais",
      "kind": "API",
      "template": "/api/feriados/v1/{year}",
      "provider": "brasilapi",
      "packages": [],
      "arg": "NONE",
      "api": {
        "method": "GET",
        "format": "{date|date} · {name}",
        "list": "",
        "max": 6,
        "upcoming": "date"
      }
    }
  ],
  "aliases": [],
  "providers": [
    {
      "id": "brasilapi",
      "name": "BrasilAPI",
      "base": "https://brasilapi.com.br",
      "hosts": [],
      "auth": { "type": "none" },
      "test": "/api/feriados/v1/2026"
    }
  ]
}""",
            )
        }
    }
}

@Composable
private fun GuideTextCard(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun JsonCodeCard(json: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "JSON",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = {
                        copyToClipboard(
                            context = context,
                            text = json,
                            label = "JSON .tcmd",
                            toastMessage = "JSON copiado!",
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar JSON",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
