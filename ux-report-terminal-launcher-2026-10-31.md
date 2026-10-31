# Análise de UX: Terminal Launcher (primeira execução, barra de comando, gaveta e configurações)

- **Produto:** Terminal Launcher (`net.mdantas.terminal`), fork do Lawnchair 16 com identidade de terminal de fósforo.
- **Fluxo avaliado:** instalação até o primeiro comando: onboarding (`OnboardingActivity.kt`), tela inicial com a barra de comando, barra de comando (`CommandActivity.kt`), gaveta de apps em estilo diretório (`DirectoryIndexView.java`) e a entrada das configurações (`PreferencesDashboard.kt`).
- **Data:** 2026-10-31
- **Modo:** análise unificada, profundidade rápida (5 personas, fases 5 e 6 enxutas).
- **Fonte:** código e strings lidos (`values/strings.xml`, `values-pt/strings_terminal.xml`, `CUSTOMIZACOES.md`). Contrastes calculados a partir das cores do código (acento padrão `#7DFFB2` sobre `#07090A`). **Não rodei o app, não testei TalkBack nem fonte grande em dispositivo.**
- **Fora do escopo:** a tela Aparência, que já tem relatório próprio (`ux-report-aparencia-2026-10-31.md`).
- **Não li:** `CrtOverlayView`, `BarButton` completo, `CommandGuidePreferences`, o motor `commands/` e o fluxo de widgets. O que depende deles aparece como não avaliável.

---

## 1. Sumário executivo

### UX Score: 74/100

`UX Score = 0,45 × Usabilidade + 0,25 × Acessibilidade + 0,30 × Comportamento/Confiança`

| Componente | Conta | Nota |
|---|---|---|
| Usabilidade (Fase 4) | 100 − 2 high×8 − 3 medium×4 − 2 low×1 = 100 − 16 − 12 − 2 | **70** |
| Acessibilidade (Fase 5) | 100 − 1 falha confirmada medium (4) − metade de (2 medium×4 + 3 low×1 = 11) = 100 − 4 − 5,5 | **90,5** |
| Comportamento/Confiança (Fase 6) | média(7, 6, 7) × 10 = 66,7, sem barreira critical | **66,7** |

0,45×70 + 0,25×90,5 + 0,30×66,7 = 31,5 + 22,6 + 20,0 = **74,1 ≈ 74**

A leitura qualitativa bate com o número. A base é boa: onboarding que não bloqueia a home, barra de comando com prévia, autocomplete e atalhos de teclado, alvos de 40–48 dp e controles rotulados. O que puxa a nota para baixo é o **comando por voz que executa sozinho**, a **dificuldade de descobrir o vocabulário da barra** (a feature principal) e o **tom de alarme e o vocabulário temático** que afastam quem não é entusiasta. A nota de acessibilidade é otimista: quase tudo é risco potencial, porque nada foi confirmado com TalkBack nem em fonte grande.

### Top 3 problemas

Nenhum é `critical`. Os três mais graves:

1. **H001 (high, inferido):** o resultado da voz que forma um comando executável roda na hora, sem confirmação. "Ligar Marina" mal reconhecido liga para a pessoa errada.
2. **H002 (high, inferido):** a barra de comando é a função central, mas o usuário só recebe uma linha de dica ("digite um comando para achar apps"). Os verbos (`w`, `rota`, `calc`, `t`, `ia`) e a ajuda (`?`) não aparecem no onboarding nem na tela inicial das configurações.
3. **H003 (medium, inferido):** o passo de acesso a notificações pede uma permissão sensível do sistema com uma frase de justificativa e sem dizer o que acontece com os dados.

### Top 5 oportunidades

| ID | Oportunidade | Impacto | Esforço | Prioridade |
|---|---|---|---|---|
| O1 | Confirmar comandos de voz que ligam, mandam mensagem ou apagam (contagem de 3 s com "cancelar") | alto | baixo | P0 |
| O2 | Ensinar a barra no onboarding: tela final com 3 exemplos tocáveis (`calc 12*3`, `w ana oi`, `?`) | alto | baixo | P0 |
| O3 | Trocar o vermelho de "definir como launcher padrão" por o tom de acento, com vermelho só para erro real | médio | baixo | P0 |
| O4 | Frase de privacidade e botão "O que isso lê?" no passo de notificações | médio | baixo | P1 |
| O5 | Rótulos em linguagem comum ao lado do vocabulário temático (gaveta, barra de comando) | médio | baixo | P1 |

### Roadmap por sprint

| Sprint | Ação | Tipo | Impacto esperado |
|---|---|---|---|
| 1 | O1 confirmação da voz, O3 cor do botão padrão, A001 contraste do texto fantasma | dev + design | Menos ação errada; tom menos alarmante |
| 1 | O2 tela de exemplos no fim do onboarding | design + conteúdo | Mais gente usa a barra na primeira semana |
| 2 | O4 privacidade no passo de notificações, O5 rótulos comuns, A005 papéis de acessibilidade | conteúdo + dev | Mais confiança; TalkBack coerente |
| 3 | Validar com usuários reais (seção 10) e ajustar | pesquisa | Troca de hipóteses por dados |

### Premissas a validar com usuários reais

- Que a maioria dos usuários quer a barra de comando (e não só o tema). Não há dado de uso na entrada.
- Que o vocabulário temático (MU/TH/UR, "diretório de programas") atrai mais do que afasta.
- Que a voz é usada com frequência para ligar e mandar mensagem, e com que taxa de reconhecimento errado.

---

## 2. Contexto do produto

| Campo | Conteúdo |
|---|---|
| Resumo | Launcher Android com estética de terminal retrô. A tela inicial tem uma barra de comando (open, calc, ligar, rota, tarefa, mensagem, ações personalizadas, IA), gaveta de apps em estilo diretório com índice A–Z e painel de alvos (agenda, clima, mídia, alarme) |
| Categoria | App de sistema/launcher para Android, nicho de customização |
| Mercado | Concorrentes: Lawnchair, Niagara, Olauncher e launchers de texto. A expectativa do segmento é leveza e controle; o mercado de launchers de comando é pequeno e de entusiastas |
| Usuários primários | Entusiastas de customização; usuários técnicos; pessoas que preferem digitar a procurar ícones |
| Metas de negócio | Retenção do launcher como padrão; instalações pelo Play (faixa `internal`/`closed-testing` no momento); diferenciação pela barra e pelos temas |
| Metas do usuário | Abrir apps rápido; executar ações comuns (ligar, mensagem, rota) sem navegar; ter um visual próprio |
| Restrições | Regras da Play Store (por isso o serviço de acessibilidade foi removido); permissões sensíveis (CALL_PHONE, contatos, notificações); base AOSP |
| Riscos | Trocar de launcher é uma decisão de risco percebido alto; ação errada por voz; curva de aprendizado da barra (probabilidade alta, impacto alto) |

**Premissas:** o público aceita vocabulário temático em inglês e português (não validada); a barra é descoberta por tentativa (não validada); permissões pedidas só no uso são aceitas (parcialmente: contatos e ligar pedem no ponto de uso, notificações pede no onboarding).

**Métricas de sucesso sugeridas:** taxa de conclusão do onboarding; % de usuários que executam um comando na primeira sessão; launcher padrão após 7 dias; taxa de comando de voz desfeito ou cancelado; retenção D7.

**`missing_context`:** métricas atuais, dados de funil do onboarding, perfil real dos testadores, aparelhos usados, idioma predominante, resultados da faixa `internal`.

---

## 3. Personas

| ID | Nome | Idade | Papel | Nível técnico | Acessibilidade |
|---|---|---|---|---|---|
| P1 | Teresa | 67 | Aposentada, Fortaleza | Iniciante | Baixa visão, fonte do sistema em 1,5× |
| P2 | Lucas | 19 | Estudante de design, Recife | Intermediário | Nenhuma |
| P3 | Rafael | 34 | Motorista de aplicativo, São Paulo | Intermediário | Mãos ocupadas ao dirigir (limitação situacional), usa voz |
| P4 | Camila | 41 | Enfermeira, Curitiba | Iniciante a intermediário | Nenhuma; atenção fragmentada por plantões |
| P5 | Henrique | 29 | Desenvolvedor, Porto Alegre | Especialista | Nenhuma |

**P1 Teresa.** Quer ver a hora e abrir WhatsApp e fotos sem erro. Medo de "estragar o celular". Aparelho Samsung de entrada, tela de 6,5". Troca de launcher porque o neto instalou e pediu para ela testar. *Cenário:* abre o app pela primeira vez com a família por perto e espera que seja fácil.

**P2 Lucas.** Gosta de deixar o celular com cara de "hacker". Segue canais de setup. Quer temas, fonte pixelada e CRT. Tem paciência com configurações. *Cenário:* instala à noite para ver o visual e mostrar aos amigos.

**P3 Rafael (edge case).** Usa o celular no suporte do carro, com ruído e pressa. Liga para passageiros e manda "cheguei" por voz. Não pode olhar para a tela por mais de 1 s. *Cenário:* aperta [MIC] da tela inicial e fala "ligar Marina" no trânsito.

**P4 Camila.** Usa WhatsApp o dia todo, entre plantões. Lê pouco texto, desconfia de permissões depois de golpes de WhatsApp. Instalou porque viu um vídeo. *Cenário:* quer só deixar o celular mais limpo e rápido, sem aprender nada novo.

**P5 Henrique.** Vive no terminal. Quer aliases, scripts e comandos de API. Lê o texto de ajuda e testa limites. *Cenário:* abre a barra, digita `?` e tenta criar comandos próprios.

**Gate:** nenhuma persona é intercambiável; o conjunto cobre idade, habilidade e uma necessidade de acessibilidade (P1, e P3 como situacional); P1 e P4 provavelmente falhariam.

---

## 4. Resultados da simulação

Todas as probabilidades vêm de simulação e **não são estimativas populacionais**.

| Persona | Primeira impressão | Confusão ou fricção principal | Abandono | Confiança | Prob. de conclusão* | Arco emocional |
|---|---|---|---|---|---|---|
| P1 Teresa | "Parece um computador antigo. Será que está certo?" | Tela de boot com "CHECKING MEMORY OK" em letras pequenas e maiúsculas. "O que é 'launcher padrão'?" O botão vermelho parece erro (H004). Letras de 10–12 sp com fonte 1,5× | Alto: na tela de configurações do sistema para escolher o launcher, ou antes, no passo de notificações | Baixa | **35%** | Curiosa → insegura → pede ajuda → sai |
| P2 Lucas | "Que visual incrível" | Quer ir direto aos temas; o onboarding o segura por 3 passos antes. Pergunta "onde ficam os comandos?" (H002) | Baixo | Alta | **90%** | Empolgado → impaciente → satisfeito |
| P3 Rafael | "Rápido, dá para falar" | Fala "ligar Marina", o reconhecedor entende "Marina" ou "Mariana", e a ligação sai sem conferir (H001). Texto de 10 sp ilegível no relance | Alto, após o primeiro erro de ligação | Média, cai depois de um erro | **70%** (onboarding), com risco de remover o app após o primeiro erro | Confiante → surpreso → desconfiado |
| P4 Camila | "Bonito, mas estranho" | Passo de notificações: "isso lê minhas mensagens?" (H003). Na home, não sabe o que digitar na barra | Médio: pula o passo e usa só os ícones | Média | **50%** | Interessada → cautelosa → indiferente |
| P5 Henrique | "Finalmente um launcher de texto" | Procura onde ver a lista de comandos; encontra `?` pela dica "TYPE ? TO SEE ALL". Não acha atalho para a ajuda nas configurações (H002). Fecha tudo em uma sessão | Baixo | Alta | **95%** | Curioso → eficiente → entusiasmado |

\*Probabilidade de concluir o onboarding e executar o primeiro comando com sucesso.

**Gate de diferenciação:** as confusões diferem (visibilidade e medo em P1, impaciência em P2, risco da voz em P3, privacidade em P4, descoberta em P5).

---

## 5. Problemas heurísticos

As 10 heurísticas foram avaliadas. Falham: 2, 3 (leve), 5, 6, 10. Passam: 1, 4 (com ressalva H004), 7, 8, 9.

| ID | Problema | Categoria | Severidade | Evidência | Tipo | Personas | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|---|
| H001 | O comando falado que já é executável roda sozinho, sem confirmação ou contagem. Vale também para ligar e mandar mensagem | Prevenção de erros | **high** | `CommandActivity.kt:1146-1157`: o resultado da voz chama `runAction` direto quando `analyze(...).action != null`. "Executa sem Enter" está em `CUSTOMIZACOES.md` §4 | inferido (o código mostra o caminho; não vi se `runAction` confirma ligações) | P3, P4 | Para ações com efeito no mundo (ligar, mensagem, apagar, alarme), mostrar o comando entendido por 3 s com botão "Cancelar" e barra de contagem. Comandos de leitura (calc, hora) seguem automáticos | baixo |
| H002 | A função principal não se ensina. O onboarding tem uma linha (`onboarding_ready_hint_command`: "digite um comando para achar apps"). O dashboard de configurações lista a entrada "Ações da barra de comando" com descrição de jargão ("atribua uma letra a ações de outros apps (w = WhatsApp)") e ícone de busca. Não há atalho visível para a ajuda ou lista de comandos | Reconhecimento em vez de memorização | **high** | `OnboardingActivity.kt:358-359`; `PreferencesDashboard.kt:133-139`; strings `command_actions_desc` | inferido (não rastreei se há outra entrada para `CommandGuidePreferences`) | P1, P2, P4, P5 | Tela final do onboarding com 3 exemplos tocáveis que abrem a barra preenchida (`calc 12*3`, `w ana oi`, `?`). Na barra vazia, mostrar uma linha de exemplo rotativa. Renomear a entrada de configurações para "Comandos da barra" e dar a ela um item "Guia de comandos" | médio |
| H003 | O passo de notificações pede acesso às notificações do sistema com uma frase ("para marcar os apps com alertas com um ponto") e abre a tela do sistema, que traz um aviso assustador. Falta dizer o que é lido, que fica no aparelho e que dá para revogar | Confiança e credibilidade | medium | `onboarding_notifications_desc`; `OnboardingActivity.kt:312-321, 397` | inferido | P4, P1 | Duas linhas de privacidade ("Só conta alertas por app. Nada sai do aparelho. Você pode desligar em Configurações"), e dizer o que vem a seguir ("o sistema vai mostrar uma tela de acesso; ative Terminal") | baixo |
| H004 | O botão de ação principal "Definir como launcher padrão" e o cartão do dashboard usam o vermelho de erro (`errorContainer`, `error`). Uma ação positiva ganha tom de alarme, e o cartão vermelho aparece em toda abertura das configurações até ser resolvido | Consistência e padrões | medium | `OnboardingActivity.kt:382-386` (`danger = true`); `PreferencesDashboard.kt:242-278` | observado | P1, P4 | Usar o acento do tema para o botão e um cartão neutro com ícone de informação. Reservar o vermelho para falha real | baixo |
| H005 | O vocabulário temático substitui termos comuns: "diretório de programas" para a gaveta, "INDEXING PROGRAMS", "NOTIFICATION DOTS", "MU/TH/UR 6000", "TERMINAL 03", "motion tracker". Quem não conhece a referência precisa traduzir | Correspondência entre sistema e mundo real | medium | `strings.xml` linhas 1084-1134; `nostromo_swipe_hint` | observado | Manter o estilo, mas pôr o termo comum na primeira aparição ("▲ DESLIZE PARA A GAVETA DE APPS (DIRETÓRIO DE PROGRAMAS)"). Textos de ação (botões, permissões) em linguagem comum | baixo |
| H006 | A tela de boot mistura linhas de teatro (memória, tema de ícones e programas sempre "OK") com estados reais (launcher padrão, notificações). A tela final marca "TEMA OK" mesmo sem a pessoa ter escolhido | Visibilidade do status do sistema | low | `OnboardingActivity.kt:241-253, 342-346` | observado | Marcar as linhas decorativas como tais (por exemplo cinza, sem status) ou tirá-las. Mostrar "TEMA: PADRÃO" quando não escolheu | baixo |
| H007 | "[×] LIMPAR RECENTES" apaga o histórico e as estatísticas de "usados" com um toque, sem confirmar nem desfazer | Controle e liberdade do usuário | low | `CommandActivity.kt:464-469`; chip em `:1397-1405` | observado | Toast com "Desfazer" por 5 s | baixo |
| H008 (+) | **Positivo:** a barra mostra prévia do efeito, autocomplete em texto fantasma, sugestões navegáveis (setas, Tab, Esc), chips de recentes e fixados, histórico e execução por voz. Atende iniciante e especialista | Flexibilidade e eficiência de uso | positive | `CommandActivity.kt:1268-1440`; `CUSTOMIZACOES.md` §4 | observado | Manter | , |
| H009 (+) | **Positivo:** o onboarding não bloqueia a home, deixa pular cada passo, retoma até 3 vezes, pode ser refeito, usa texto e cor (não só cor) para o status e deixa o próprio ecrã servir de prévia do tema | Controle e liberdade do usuário | positive | `OnboardingActivity.kt:70-110, 156-215`; `PreferencesDashboard.kt:206-214` | observado | Manter | , |

Heurísticas 8 (estética e minimalismo), 9 (recuperação de erros) e 10 (ajuda) sem achado próprio além de H002 e H005: a barra tem mensagens de falha com dica (`command_failed_hint`), e a densidade visual é compatível com o estilo. A ajuda é o ponto fraco (H002).

```json
{"heuristics_passed": ["1", "4", "7", "8", "9"], "heuristics_failed": ["2", "3", "5", "6", "10"], "usability_score": 70, "score_rationale": "100 - 2 high (16) - 3 medium (12) - 2 low (2). H008 e H009 são positivos e não pontuam."}
```

---

## 6. Problemas de acessibilidade (WCAG 2.2)

Níveis A e AA. Contrastes calculados sobre `#07090A` com o acento padrão `#7DFFB2`.

| ID | Problema | WCAG | Nível | Status | Severidade | Personas | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|---|
| A001 | O texto fantasma do autocomplete usa o acento com alpha 0,4: contraste medido de **3,2:1**, abaixo de 4,5:1. É a sugestão que a pessoa lê para decidir se aceita o Tab | 1.4.3 Contrast (Minimum) | AA | falha confirmada | medium | P1, P3 | Subir o alpha para pelo menos 0,62 (6,4:1 medido para o tom `dim`) ou distinguir o texto fantasma por estilo (itálico, sublinhado) e não só por opacidade | baixo |
| A002 | Em temas com acento escuro (azul, roxo), o tom `dim` (mix 0,62) cai para cerca de 2,0–2,3:1 e as legendas de 9,5–10 sp (`SUGESTÃO`, `RECENTES`, o tipo de cada linha) ficam ilegíveis. Há aviso de contraste na Aparência, mas não vi se ele cobre os tons derivados | 1.4.3 | AA | risco potencial | medium | P1, P2 | Calcular `dim` e `line` com piso de contraste (4,5:1 para texto, 3:1 para bordas) em vez de mix fixo, como já se faz com `onAccent` em `LauncherGround` | médio |
| A003 | Texto funcional em 9,5–11 sp, em caixa alta e monoespaçado, com alturas fixas (campo de 48 dp, botões de 52 e 48 dp). Em fonte 1,5× a 2× há risco de truncar (`maxLines = 1` no título) ou cortar | 1.4.4 Resize Text; 1.4.12 Text Spacing | AA | risco potencial | low | P1 | Piso de 12 sp para texto que informa; `heightIn(min=...)` em vez de altura fixa; testar a 200% | médio |
| A004 | O índice A–Z da gaveta está fora da árvore de acessibilidade (`IMPORTANT_FOR_ACCESSIBILITY_NO`) e as letras chegam a 7 sp. A busca é uma alternativa, então não bloqueia | 4.1.2 Name, Role, Value | A | risco potencial | low | P1 | Manter a busca em destaque para TalkBack e expor uma ação "ir para a letra" ou rolagem por seção | médio |
| A005 | `[MIC]`, `[PIN]`, as linhas de sugestão e os chips usam `clickable` sem `Role.Button`. Os dois primeiros têm `contentDescription`, as sugestões e os chips não. O TalkBack lê o glifo ("colchete MIC") ou o texto sem dizer que é acionável | 4.1.2 | A | risco potencial | medium | P1 | `clickable(role = Role.Button, onClickLabel = ...)` em todos, como já se faz em `:1814` e `:2074`, e `contentDescription` nas sugestões ("abrir Chrome, aplicativo") | baixo |
| A006 | Efeito de máquina de escrever e CRT animam por conta própria. Não li `CrtOverlayView`, então não sei se respeita "remover animações" do sistema nem se pisca | 2.2.2 Pause, Stop, Hide; 2.3.1 Three Flashes | A | risco potencial | low | P1, P4 | Respeitar `ANIMATOR_DURATION_SCALE = 0` e manter o CRT desligado por padrão | baixo |
| A007 | Mensagens de resultado ("EXECUTADO", "FALHOU", "COPIADO") aparecem na tela. Não confirmei se são anunciadas pelo leitor de tela | 4.1.3 Status Messages | AA | não avaliável — entrada insuficiente | , | P1 | Marcar a área de resultado como `liveRegion = Polite` | baixo |
| A008 | Status do onboarding usa cor e texto (`OK`, `[NOT SET]`, `[PENDING]`) | 1.4.1 Use of Color | A | passa | , | , | Manter | , |

**Cognitiva:** parágrafos de 2 a 3 linhas em caixa alta e monoespaçado (`onboarding_color_hint`) leem mais devagar. Uma regra simples: caixa alta só em rótulos curtos, frases em caixa mista.

```json
{"wcag_a": "partial", "wcag_aa": "partial", "total_issues": 7, "critical_issues": 0, "high_issues": 0, "accessibility_score": 90.5, "score_rationale": "100 - 4 (A001, falha confirmada medium) - 5,5 (metade de A002 4, A005 4, A003 1, A004 1, A006 1). A007 não avaliável não pontua. Nota otimista: sem teste de TalkBack e de fonte grande."}
```

---

## 7. Insights comportamentais

### Barreiras

| ID | Barreira | Framework | Gatilho | Personas | Evidência | Severidade | Corrigível por design | Recomendação |
|---|---|---|---|---|---|---|---|---|
| B001 | Aversão à perda no passo de notificações: a pessoa não sabe o custo (o que será lido) e recusa por precaução | Aversão à perda; confiança de processo | Tela do sistema com aviso de acesso total | P4, P1 | H003 | medium | sim | Enquadrar o ganho e delimitar o risco em duas linhas (ver O4) |
| B002 | Gatilho fraco para a feature principal: o usuário que não vê verbos não digita. Motivação alta (P2, P5), habilidade baixa para P1 e P4 | Modelo de Fogg | Barra vazia sem exemplo | P1, P4 | H002 | high | sim | Exemplos tocáveis e linha de exemplo na barra vazia (ver O2) |
| B003 | Confiança no processo cai depois de uma ação errada. Um único engano em "ligar" pode encerrar o uso do launcher | Formação de confiança; aversão à perda (risco de constrangimento social) | Voz executa sozinha | P3 | H001 | high | sim | Confirmação curta (ver O1) |
| B004 | Carga extrínseca: a tela de boot gasta o primeiro minuto em teatro antes de o pedido real (launcher padrão) | Teoria da carga cognitiva | Tela de boot | P1, P2 | H006 | low | sim | Encurtar o boot, ou fundir boot e primeiro passo |
| B005 | Decisão de trocar de launcher é de risco percebido alto ("se eu não gostar, volto?") e o app não diz como reverter | Aversão à perda | Passo de launcher padrão | P1, P4 | `onboarding_default_*` não traz "como voltar" | medium | sim | Linha "Você pode voltar ao launcher anterior em Configurações do Android > Apps padrão" |

### Confiança

```json
{"initial_trust": "7", "process_trust": "6", "outcome_trust": "7",
 "trust_gaps": ["A voz executa ações com efeito sem conferir", "Permissão de notificações sem frase de privacidade", "Linhas de boot decorativas ao lado de status reais", "Sem 'como voltar' no passo de launcher padrão"],
 "trust_builders_present": ["Identidade visual coerente e distinta", "Prévia do que o comando fará antes de executar", "Cada passo do onboarding pode ser pulado", "Aviso de fork e licença na tela Sobre", "Permissões de contatos e ligação pedidas só no ponto de uso"],
 "trust_builders_missing": ["Texto de privacidade nas permissões", "Confirmação para ações com efeito", "Como reverter a troca de launcher"]}
```

### Mapa Motivação-Habilidade-Gatilho

| Ação | Motivação | Habilidade | Gatilho | Resultado | Recomendação |
|---|---|---|---|---|---|
| Definir como launcher padrão | média | média | forte (botão grande) | em risco | Reduzir o medo: "como voltar" e tom neutro |
| Conceder acesso a notificações | baixa | média | forte | em risco | Privacidade explícita (O4) |
| Escolher um tema | alta | alta | forte (chips com prévia ao vivo) | provável | Manter |
| Digitar o primeiro comando | média | baixa para P1 e P4 | ausente | em risco de abandono | Exemplos tocáveis (O2) |
| Usar voz | alta | alta | forte ([MIC] na home) | provável, com risco de erro | Confirmação nas ações com efeito (O1) |

### Oportunidades comportamentais

| Oportunidade | Framework | Mecanismo | Impacto esperado | Implementação |
|---|---|---|---|---|
| Primeiro comando guiado | Compromisso e consistência | Um micro-sucesso cedo aumenta a chance de uso continuado | Alto | A tela final do onboarding abre a barra com `calc 12*3` já digitado; Enter executa |
| Contagem de 3 s na voz | Prospect Theory | Reduz o custo percebido do erro sem tirar a rapidez | Alto | Barra de contagem sobre o resultado, toque para cancelar, toque longo para executar já |

---

## 8. Insights e padrões entre fases

| ID | Insight | Confiança | Sustentado por | Personas | Impacto no negócio | Impacto no usuário |
|---|---|---|---|---|---|---|
| I001 | A promessa do produto (a barra) e o ensino dela estão desalinhados: o onboarding gasta 3 passos com launcher, tema e notificações e quase nada com o comando | média | H002, B002 | P1, P2, P4, P5 | A maior diferenciação do produto fica invisível para parte dos instaladores | O usuário usa o launcher como um qualquer e não percebe o ganho |
| I002 | A confiança é construída pela estética mas gasta por três pontos de risco: voz, notificações e troca de launcher | média | H001, H003, B003, B005 | P3, P4, P1 | Desinstalação após o primeiro erro; recusa de permissões | Medo de errar ou de ceder dados |
| I003 | O estilo "terminal" é um acerto para o nicho (P2, P5) e um custo para iniciantes (P1, P4): letras pequenas, caixa alta, vocabulário temático e vermelho de alarme se somam | média | H004, H005, A003, B004 | P1, P4 | Define quem fica | Esforço de leitura maior |

**Conflito entre fases:** a Fase 4 (H008) elogia a densidade e a riqueza da barra; a Fase 5 (A003) e a Fase 6 (B004) mostram que a mesma densidade custa a P1 e P4. As duas valem: o recomendado é manter a barra rica e adicionar um modo de leitura (piso de 12 sp, caixa mista) em vez de simplificar a barra.

---

## 9. Oportunidades priorizadas

| ID | Oportunidade | Resolve | Impacto | Esforço | Prioridade |
|---|---|---|---|---|---|
| O1 | Confirmação de 3 s nos comandos de voz que têm efeito no mundo | H001, B003, I002 | alto | baixo | **P0** |
| O2 | Tela de exemplos tocáveis no fim do onboarding e linha de exemplo na barra vazia | H002, B002, I001 | alto | baixo | **P0** |
| O3 | Cor de acento e cartão neutro para "launcher padrão" | H004, I003 | médio | baixo | **P0** (impacto médio, mas custo de um ajuste de cor) |
| O4 | Privacidade e passos seguintes no passo de notificações | H003, B001 | médio | baixo | P1 |
| O5 | Termo comum junto do temático; frases em caixa mista | H005, I003 | médio | baixo | P1 |
| O6 | Contraste calculado para `dim` e `line` e para o texto fantasma | A001, A002 | médio | médio | P1 |
| O7 | Papéis, rótulos e live region na barra (TalkBack) | A005, A007 | médio | baixo | P1 |
| O8 | Piso de 12 sp e alturas flexíveis; teste a 200% | A003 | médio | médio | P2 |
| O9 | "Como voltar" no passo de launcher padrão | B005 | médio | baixo | P2 |
| O10 | Desfazer ao limpar recentes; limpar boot decorativo | H007, H006 | baixo | baixo | P3 |

---

## 10. Experimentos

| Oportunidade | Hipótese | Método | Participantes | Métrica de sucesso | Baseline necessário | Meta (a calibrar) |
|---|---|---|---|---|---|---|
| O1 | Se a voz mostrar o comando entendido por 3 s com "Cancelar" antes de ligar ou enviar, então ligações e mensagens erradas vão cair, porque a pessoa tem tempo de notar o engano | Teste de usabilidade moderado com tarefa em ambiente ruidoso, depois A/B no `closed-testing` | 8 a 10 pessoas que usam voz no carro; depois todo o grupo | Taxa de comandos de voz cancelados e de ligações desfeitas em até 10 s | Taxa atual de cancelamento (hoje impossível) e relatos de erro | Reduzir erros relatados; meta definida com o baseline |
| O2 | Se o fim do onboarding oferecer 3 exemplos tocáveis, então mais pessoas vão executar um comando na primeira sessão, porque o micro-sucesso reduz a barreira de habilidade | A/B test | Instalações novas, divididas em dois grupos | % que executa um comando na primeira sessão; % que usa a barra no D7 | Funil atual do onboarding (não existe na entrada) | Aumento a calibrar com o baseline |
| O3 | Se o botão de launcher padrão usar o acento em vez do vermelho, então a taxa de conclusão desse passo vai subir, porque o vermelho comunica erro | A/B test | Instalações novas | % que define o launcher como padrão no onboarding | Taxa atual do passo | Aumento a calibrar |
| O4 | Se o passo de notificações trouxer frase de privacidade, então mais pessoas concederão o acesso, porque o custo percebido cai | Teste de usabilidade não moderado + A/B | 6 a 8 pessoas com perfil de P4; depois A/B | % que concede acesso; motivo declarado de quem recusa | Taxa atual de concessão | Aumento a calibrar |

Correções `observado` de esforço baixo (O3, parte de O5, O10, A005) podem ir direto para implementação, sem experimento. Os achados `simulado` (P1, P4) precisam de teste com pessoas reais antes de decisões maiores.

---

## 11. Lacunas e premissas

- **Nada foi executado em dispositivo.** Layout, contraste real do papel de parede, comportamento do TalkBack e de fonte 2× são hipóteses.
- **Achados só inferidos:** H001 (não vi se `runAction` confirma ligações), H002 (não rastreei todas as entradas do guia de comandos), H003. Verifique no código de `runAction` e em `CommandGuidePreferences` antes de priorizar.
- **Achados só simulados:** as confusões de P1 e P4 e as probabilidades da seção 4. Não são percentuais da população.
- **Não avaliável:** A007 (anúncio de resultados), A006 (CRT e animações), alvos de toque do índice A–Z, `BarButton`.
- **Fora do escopo:** gaveta de apps além do índice, widgets da tela inicial, painel de alvos, configurações além do dashboard, o relógio, o Terminal Bar.
- **Metas dos experimentos** são para calibrar com o baseline, não previsões.
- **Contraste:** os valores 3,2:1, 6,4:1 e 2,0–2,3:1 foram calculados com a fórmula WCAG a partir das cores do código (mix linear entre `#07090A` e o acento); o app usa `ColorUtils.blendARGB`, que mistura em sRGB, como assumi.
