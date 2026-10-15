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
Sua tarefa é gerar um pacote de comandos no formato .tcmd (arquivo JSON) a partir da documentação de API fornecida pelo usuário.

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
    "upcoming": ""
  }
}
- "letter": Palavra do comando na barra (apenas letras e dígitos, minúsculas). Não use palavras reservadas do sistema (calc, clima, tempo, abrir, wifi, bluetooth, lanterna, silencio, brilho, volume, tocar, copiar, colar, config, agenda, nota, cancelar, bateria, armazenamento, ip, ping, rede, fast, ia, qr, etc.).
- "kind": "API".
- "provider": Deve coincidir com o "id" de um provedor em providers[].
- "arg": "TEXT" se o comando usa o que for digitado ({text}), ou "NONE" se roda sozinho.
- "packages": Deve ser obrigatoriamente [] em API.
- "api.method": "GET" ou "POST" (PUT/PATCH/DELETE não são suportados).
- "api.format": String de formatação da resposta. Chaves {campo} apontam para o JSON da resposta (com pontos para aninhamento: {main.temp}, {weather.0.description}).
  * Suporta {campo|date} para converter datas ISO em data amigável local.
  * Partes separadas por ' · ' (espaço, ponto médio, espaço) são omitidas se os campos estiverem vazios.
- "api.list": Caminho do array se a resposta for uma lista (ex: "results").
- "api.max": Quantidade de itens mostrados (1 a 20, padrão 5).
- "api.upcoming": Nome do campo de data ISO para omitir eventos passados (opcional).
- "api.body": Corpo da requisição se method for "POST" (opcional).

5. DEFINIÇÃO DE COMANDO PARA ABRIR APPS/SITES (kind: "VIEW"):
{
  "letter": "w",
  "label": "WhatsApp",
  "kind": "VIEW",
  "template": "https://api.whatsapp.com/send?text={text}",
  "packages": ["com.whatsapp"],
  "arg": "TEXT"
}

DOCUMENTAÇÃO DA API A INTEGRAR:
[Forneça aqui a documentação da API, URL base, endpoint desejado e estrutura de resposta JSON]"""

@Composable
fun CommandGuidePreferences() {
    val context = LocalContext.current
    val promptCopiedMessage = stringResource(R.string.cmd_guide_prompt_copied)

    PreferenceLayout(
        label = stringResource(R.string.cmd_guide_title),
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
    "max": 5
  }
}""",
            )
            GuideTextCard(
                text = "Campos e regras de formatação (api):\n" +
                    "• method: Apenas GET ou POST.\n" +
                    "• format: Template com {campo} para extrair dados. Suporta objetos {a.b}, arrays {items.0.nome} e filtro de data {campo|date}.\n" +
                    "• Partes separadas por ' · ' são automaticamente omitidas quando os dados estão vazios.\n" +
                    "• list: Nome do array na resposta JSON.\n" +
                    "• max: Limite de itens na barra (1 a 20).\n" +
                    "• upcoming: Campo de data ISO para omitir eventos passados.",
            )
        }

        // Section 4: VIEW Actions
        PreferenceGroup(heading = stringResource(R.string.cmd_guide_section_view_actions)) {
            GuideTextCard(
                text = "Ações normais (kind: \"VIEW\") abrem aplicativos instalados ou URLs de navegadores usando intents do Android.",
            )
            JsonCodeCard(
                json = """{
  "letter": "w",
  "label": "WhatsApp",
  "kind": "VIEW",
  "template": "https://api.whatsapp.com/send?text={text}",
  "packages": ["com.whatsapp"],
  "arg": "TEXT"
}""",
            )
        }

        // Section 5: Placeholders
        PreferenceGroup(heading = "Placeholders e Variáveis") {
            GuideTextCard(
                text = "Você pode usar variáveis no template da URL:\n" +
                    "• {text}: Texto digitado após o comando (codificado para URL)\n" +
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
