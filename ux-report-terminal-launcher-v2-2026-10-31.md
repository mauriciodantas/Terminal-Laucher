# Análise de UX: Terminal Launcher, da primeira execução ao primeiro comando

- **Produto:** Terminal Launcher (`net.mdantas.terminal`), launcher Android baseado no Lawnchair 16 / AOSP Launcher3
- **Fluxo avaliado:** onboarding, tela inicial com a barra de comando, barra de comando, gaveta de apps em estilo diretório, dashboard de configurações (a tela Aparência ficou fora do escopo)
- **Data:** 2026-10-31
- **Modo:** análise unificada, profundidade rápida (5 personas, Fases 5 e 6 enxutas)
- **Fonte:** código e strings lidos agora (`OnboardingActivity.kt`, `CommandActivity.kt`, `LawnQsbUi.kt`/`LawnQsbLayout.kt`, `DirectoryIndexView.java`, `PreferencesDashboard.kt`, `strings_terminal.xml` em `values` e `values-pt`). Nenhum relatório ou doc anterior foi lido. Nada foi executado em aparelho: tudo é `observado` ou `inferido` do código, ou `simulado`.

---

## 1. Sumário executivo

### UX Score: 67,7 / 100

`UX Score = 0,45 × Usabilidade + 0,25 × Acessibilidade + 0,30 × Comportamento/Confiança`

| Componente | Conta | Resultado |
|---|---|---|
| Usabilidade (Fase 4) | 100 − [2 high × 8 = 16] − [5 medium × 4 = 20] − [5 low × 1 = 5] = 100 − 41 | **59** |
| Acessibilidade (Fase 5) | 100 − [falha confirmada: A001 medium = 4] − [riscos potenciais pela metade: A002, A003, A004 medium = 3 × 4 × 0,5 = 6; A005, A006, A007 low = 3 × 1 × 0,5 = 1,5] = 100 − 11,5 | **88,5** |
| Comportamento/Confiança (Fase 6) | média(6 ; 7 ; 6) × 10 = 6,33 × 10 = 63,3; menos 5 × 0 barreiras critical | **63,3** |

`UX Score = 0,45 × 59 + 0,25 × 88,5 + 0,30 × 63,3 = 26,55 + 22,13 + 18,99 = 67,67 ≈ 67,7`

> Rationale. A leitura qualitativa concorda com a fórmula: o produto tem identidade forte e um onboarding que nunca bloqueia, mas perde pontos porque quem chega sem repertório de terminal não aprende o que digitar (a parte mais valiosa do produto fica atrás de jargão e de um texto em inglês no meio de uma interface em português). A acessibilidade sai melhor que a usabilidade porque os alvos do onboarding e da barra têm 44 a 56 dp, há `liveRegion` nos resultados e os contrastes do tema padrão passam; o diretório e a cor customizável deixam riscos.

### Top 3 problemas

1. **H003 (high):** o primeiro comando de verdade (`> EXPERIMENTE AGORA`) fica no último passo do onboarding, abaixo de um título de 44 sp, três linhas de log e dicas, dentro de uma coluna rolável, enquanto o botão `ENTRAR NO TERMINAL` fica fixo e em destaque no rodapé. Quem toca no botão grande nunca roda um comando.
2. **H004 (high):** quem pula a configuração (botão `PULAR CONFIGURAÇÃO` já na primeira tela) ou fecha com Voltar não encontra em lugar nenhum um exemplo do que digitar. A tela inicial ensina a deslizar para o diretório, e o campo diz só `DIGITE UM COMANDO`.
3. **H001 + H002 (medium, repetidos em várias telas):** o passo do launcher padrão e o cartão do dashboard usam `set_default_launcher_tip`, que **não existe em nenhum `values-pt*`** e aparece em inglês; e o vocabulário (MU/TH/UR 6000, CICLO DE HIPERSONO, RASTREADOR DE MOVIMENTO, `[NÃO DEFINIDO]`, "FORK DO LAWNCHAIR") exige referência de filme para ser entendido.

### Top 5 oportunidades

| ID | Oportunidade | Impacto | Esforço | Prioridade |
|---|---|---|---|---|
| O1 | Trazer o "experimente" para o início do passo final (ou transformar `ENTRAR NO TERMINAL` em segundo plano quando houver exemplo não tocado) | high | low | P0 |
| O2 | Mostrar um exemplo rotativo na barra da tela inicial (`ex.: calc 12*3`, `?` lista tudo) no lugar do `DIGITE UM COMANDO` fixo | high | low | P0 |
| O3 | Traduzir `set_default_launcher_tip` e conferir as demais strings herdadas do Lawnchair em `values-pt*` | medium | low | P0 |
| O4 | Dar ao diretório um balão com a letra ativa, índice com área de toque maior e ação de acessibilidade | medium | medium | P1 |
| O5 | Mensagem de falha da barra com próxima ação (`TENTE: ?`) e reduzir o jargão do onboarding | medium | low | P1 |

---

## 2. Contexto do produto (Fase 1)

```json
{
  "product_summary": "Launcher Android com estética de terminal (fósforo verde sobre fundo escuro, fonte monoespaçada) cujo centro é uma barra de comando com autocompletar (abrir, calc, alarme, ligar, voz, IA). Gaveta de apps em formato de diretório com índice alfabético.",
  "product_category": "App mobile, utilitário de personalização (launcher), distribuição Play Store, faixa de testes internos",
  "market_context": "Concorrentes: Nova, Lawnchair, Niagara, Olauncher, Kvaesitso. O nicho 'launcher minimalista guiado por texto' (Olauncher, Niagara) atrai quem quer menos distração; o Terminal soma motor de comandos e estética retrô. O usuário típico espera trocar de launcher em poucos toques e ver a mudança na hora.",
  "primary_users": ["entusiastas de personalização e de linha de comando", "minimalistas que querem menos distração", "usuários avançados que automatizam (apelidos, API, IA)"],
  "business_goals": ["ativar o launcher como padrão", "levar o usuário ao primeiro comando executado", "reter pela barra de comando (diferencial)"],
  "user_goals": ["abrir apps rápido", "executar ações por texto ou voz", "personalizar o visual sem esforço"],
  "constraints": ["Android, vários tamanhos de tela e fontes do sistema", "o launcher padrão só muda pelas configurações do Android (ACTION_HOME_SETTINGS)", "textos herdados do Lawnchair misturados com os do fork", "idioma principal pt-BR, mais 15 locales de strings_terminal"],
  "risks": [
    {"risk": "Usuário não entende o que digitar e abandona antes do primeiro comando", "likelihood": "high", "impact": "high"},
    {"risk": "Interface em idioma misto reduz a confiança no passo que pede o launcher padrão", "likelihood": "medium", "impact": "high"},
    {"risk": "Customização de cor permite contraste baixo em todas as telas", "likelihood": "medium", "impact": "medium"}
  ],
  "assumptions": [
    {"assumption": "A estética de terminal atrai o público-alvo e não afasta quem chega por indicação", "validation_status": "unvalidated"},
    {"assumption": "Quem chega sabe que existe uma barra de comando", "validation_status": "unvalidated"},
    {"assumption": "Três passos de configuração antes de usar é aceitável", "validation_status": "unvalidated"}
  ],
  "key_success_metrics": ["% que termina ou pula o onboarding e executa um comando na primeira sessão", "% que define o launcher padrão", "comandos por usuário no dia 1 e dia 7", "taxa de falha de comando (`command_failed`)"],
  "missing_context": ["métricas atuais de funil", "perfil real dos testadores", "como se vê no aparelho (não houve captura de tela)", "o que a gaveta mostra com 0 ou 1 seção"],
  "notes": "Sem baseline de funil, todas as metas deste relatório são hipóteses a calibrar."
}
```

---

## 3. Personas (Fase 2)

| ID | Nome | Idade | Papel | Nível técnico | Acessibilidade |
|---|---|---|---|---|---|
| P1 | Lúcia Ferreira | 63 | Aposentada, Recife | novice | Baixa visão (presbiopia), fonte do sistema em 130% |
| P2 | Rafael Moura | 19 | Estudante de Design, Belo Horizonte | intermediate | nenhuma |
| P3 | Marta Gouveia | 52 | Enfermeira de plantão, Curitiba | intermediate | Tremor essencial leve nas mãos (motora) |
| P4 | Diego Bastos | 34 | Dev backend, Porto Alegre | expert | nenhuma |
| P5 | Tomás Carvalho | 45 | Técnico de logística, Lisboa (pt-PT), edge case | novice a intermediate | nenhuma |

```json
[
 {"persona_id":"P1","name":"Lúcia Ferreira","age":63,"location":"Recife, PE","occupation":"Aposentada","income_level":"low","experience_level":"novice",
  "goals":["abrir WhatsApp e fotos sem errar","enxergar os ícones"],"motivations":["a neta pediu um celular 'mais limpo'"],
  "pain_points":["letra pequena","palavras em inglês","medo de apagar algo"],"fears":["mudar uma configuração e não conseguir voltar"],
  "decision_factors":["alguém de confiança recomendou","consegue voltar atrás"],"technology_profile":"usa WhatsApp, YouTube e câmera; nunca trocou de launcher",
  "devices":["Moto G (tela 6,5\")"],"behavior_patterns":["lê só o título e o botão grande","pede ajuda quando trava"],
  "accessibility_needs":["baixa visão","fonte aumentada","contraste alto"],"personality_traits":["cautelosa","paciente"],
  "prior_product_experience":"launcher de fábrica da Motorola",
  "typical_scenario":"A neta instala o Terminal e entrega o celular. Lúcia abre, vê uma tela preta com texto verde e precisa decidir o que tocar."},
 {"persona_id":"P2","name":"Rafael Moura","age":19,"location":"Belo Horizonte, MG","occupation":"Estudante de Design","income_level":"low","experience_level":"intermediate",
  "goals":["visual único para o celular","mostrar para os amigos"],"motivations":["estética retrô de filme de ficção científica"],
  "pain_points":["launchers genéricos","configuração demorada antes de ver o resultado"],"fears":["tema feio e sem como trocar"],
  "decision_factors":["aparência na primeira tela","ver o tema mudar na hora"],"technology_profile":"instala apps novos toda semana, conhece Reddit r/androidthemes",
  "devices":["Galaxy A54"],"behavior_patterns":["explora tudo antes de ler","toca no que parece clicável"],
  "accessibility_needs":[],"personality_traits":["curioso","impaciente com texto longo"],
  "prior_product_experience":"Nova Launcher e Niagara",
  "typical_scenario":"Viu um vídeo do launcher, instala à noite e quer ver o tema verde e a barra de comando funcionando em minutos."},
 {"persona_id":"P3","name":"Marta Gouveia","age":52,"location":"Curitiba, PR","occupation":"Enfermeira de plantão","income_level":"middle","experience_level":"intermediate",
  "goals":["abrir apps com uma mão, de luvas ou com pressa","usar voz entre atendimentos"],"motivations":["rapidez, poucos toques"],
  "pain_points":["alvos pequenos","toques errados por tremor"],"fears":["tocar em algo por engano durante o plantão"],
  "decision_factors":["precisa funcionar de primeira","voz"],"technology_profile":"usa apps de escala e mensagens; configura pouco",
  "devices":["Galaxy S21"],"behavior_patterns":["usa o polegar, arrasta devagar"],
  "accessibility_needs":["tremor leve","alvos de toque maiores","evitar arrastar com precisão"],"personality_traits":["objetiva","pouco tempo"],
  "prior_product_experience":"One UI padrão",
  "typical_scenario":"No intervalo, tenta achar o app do hospital pela gaveta e rolar até a letra certa."},
 {"persona_id":"P4","name":"Diego Bastos","age":34,"location":"Porto Alegre, RS","occupation":"Desenvolvedor backend","income_level":"high","experience_level":"expert",
  "goals":["automatizar com apelidos, API e IA","teclado em vez de ícones"],"motivations":["controle total, scriptável"],
  "pain_points":["launchers sem automação","menus escondidos"],"fears":["perder o histórico e os apelidos numa troca de aparelho"],
  "decision_factors":["profundidade do motor de comandos","backup"],"technology_profile":"usa Termux, Tasker e shell diariamente",
  "devices":["Pixel 8"],"behavior_patterns":["pula onboarding","vai direto a '?' e às configurações"],
  "accessibility_needs":[],"personality_traits":["cético","eficiente"],
  "prior_product_experience":"Nova + Tasker, Kvaesitso",
  "typical_scenario":"Instala, ignora a configuração, abre a barra, digita `?` e vai direto às ações de comando para criar apelidos."},
 {"persona_id":"P5","name":"Tomás Carvalho","age":45,"location":"Lisboa, Portugal","occupation":"Técnico de logística","income_level":"middle","experience_level":"intermediate",
  "goals":["telemóvel mais rápido","menos distrações"],"motivations":["um colega usa e recomendou"],
  "pain_points":["português do Brasil misturado com inglês","pouca paciência para ecrãs de configuração"],"fears":["trocar o launcher e perder a organização"],
  "decision_factors":["entender o benefício em duas frases"],"technology_profile":"Android com idioma do sistema pt-PT",
  "devices":["Xiaomi Redmi Note 12"],"behavior_patterns":["salta tudo o que parece formalidade","toca em 'Ignorar' ao primeiro sinal de texto longo"],
  "accessibility_needs":[],"personality_traits":["prático","desconfiado de permissões"],
  "prior_product_experience":"MIUI launcher",
  "typical_scenario":"Instala por recomendação, salta a configuração para ver o ecrã e não percebe o que escrever na linha de comando."}
]
```

Gate: nenhuma persona é intercambiável; cobre novice a expert, 19 a 63 anos, acessibilidade visual e motora, edge case de idioma/atalho de onboarding; P1 e P5 provavelmente falhariam.

---

## 4. Simulação cognitiva (Fase 3)

| Persona | Primeira impressão | Modelo mental | Confusão principal (severidade, evidência) | Risco de abandono | Conclusão (chega ao 1º comando) | Arco emocional |
|---|---|---|---|---|---|---|
| P1 Lúcia | "Parece computador de filme, é isto mesmo?" | Telefone com ícones e botão "Início" | "O que é 'CICLO DE HIPERSONO'? 'FORK DO LAWNCHAIR'? Eu não pedi nada disso." (medium, observado); passo do launcher padrão traz uma frase em inglês: "To access shortcuts and additional features..." (medium, observado). Texto mono de 10 a 11 sp, em caixa alta, a 130% de fonte (simulado). | high, no passo 1 ou no passo do launcher padrão | 35% | estranhamento, depois cautela, por fim pede ajuda |
| P2 Rafael | "Que estilo, é isso." | Tela de boot de filme, Niagara | Mexe nos temas, adora ver tudo mudar na hora (positivo). Pula a barra de ajuda ao tocar no botão grande `ENTRAR NO TERMINAL` sem ter tocado em nenhum exemplo (high, inferido). | low | 85% | encanto, exploração, leve dúvida sobre "e agora?" |
| P3 Marta | "Preciso ver rápido onde abre o app do hospital." | Gaveta com índice lateral como contatos | Faixa de letras com 18 dp de largura e células de cerca de 20 a 24 dp de altura: escorrega para a letra vizinha ao arrastar com tremor, e não há balão mostrando a letra ativa sob o dedo (medium, inferido). | medium | 55% (a barra de voz ajuda) | pressa, irritação com o índice, alívio com `[MIC]` de 56 × 48 dp |
| P4 Diego | "Ok, ver se tem motor de verdade." | Termux, Raycast | Toca em `PULAR CONFIGURAÇÃO`, digita `?` e resolve. Dicas `[TAB] COMPLETAR` / `[▲] HISTÓRICO` fazem sentido para ele (positivo). Falha de comando não diz o que tentar (medium, observado). | low | 90% | curiosidade, satisfação, cobrança por mais profundidade |
| P5 Tomás | "Ecrã de configuração, vou saltar." | Qualquer app que pede permissões | Toca em `PULAR CONFIGURAÇÃO` já na primeira tela. Na tela inicial lê `DIGITE UM COMANDO` e `▲ DESLIZE PARA O DIRETÓRIO`: não há exemplo nem `?`. Strings pt-PT herdam as do pt (tip em inglês também no dashboard). (high, observado e inferido) | high | 40% | impaciência, desorientação, abandona em dois minutos |

Gate: as confusões diferem por persona (jargão e idioma para P1, precisão de toque para P3, falta de exemplo para P5, nada de grave para P4).

---

## 5. Problemas heurísticos (Fase 4)

| ID | Problema | Categoria | Sev. | Evidência (tipo) | Personas | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|
| H003 | O "experimente agora" (`calc 12*3`, `torch`, `?`) é o último bloco do último passo. Antes dele há `> SISTEMA PRONTO`, `PRONTO.` a 44 sp, três linhas de log, o texto de itens pulados e o hint de deslizar, tudo em coluna rolável. O botão `ENTRAR NO TERMINAL` (52 dp, primário) fica fixo fora da rolagem. O gesto mais fácil encerra o onboarding sem nenhum comando. | Carga cognitiva | high | `ReadyStep`, `TryPanel`, `Actions` (observado); efeito no comportamento (inferido) | P1, P2, P5 | Subir o `TryPanel` para logo abaixo do título, reduzir o título e o log a uma linha cada, e rebaixar `ENTRAR NO TERMINAL` a botão secundário (outline) enquanto nenhum exemplo foi tocado. | low |
| H004 | Quem pula (`PULAR CONFIGURAÇÃO` na primeira tela, que chama `markDone`) ou sai com Voltar vê uma home sem nenhum exemplo de comando. O campo diz `DIGITE UM COMANDO`; o hint abaixo ensina a deslizar para o diretório (`▲ DESLIZE PARA O DIRETÓRIO DE PROGRAMAS`), não a usar o campo. `?` só aparece em `command_more` quando a lista é truncada. | Ajuda e documentação | high | `LawnQsbLayout` (placeholder e hint), `strings_terminal.xml` (observado) | P4, P5, P1 | Placeholder rotativo com exemplos reais (`calc 12*3`, `abrir chrome`, `? lista tudo`), e dica `? AJUDA` fixa na barra. | low |
| H001 | O passo "Launcher padrão" do onboarding e o cartão amarelo do dashboard usam `set_default_launcher_tip`, que existe só em `values/strings.xml` ("To access shortcuts and additional features, set Terminal as your default launcher"). Não há tradução em `values-pt`, `pt-rBR` ou `pt-rPT`. O título e o botão estão em português. | Localização/idioma | medium | grep nos três `values-pt*` (observado) | P1, P5 | Traduzir nos 3 locales e rodar um verificador de chaves faltantes no CI local. O texto atual é vago ("atalhos e recursos extras"): usar o do painel seguinte ("o botão Início abre o Terminal"). | low |
| H002 | Vocabulário de ambientação sem significado para quem não viu o filme: `MU/TH/UR 6000`, `CICLO DE HIPERSONO · SEÇÃO A`, `RASTREADOR DE MOVIMENTO`, `FORK DO LAWNCHAIR`, `[NÃO DEFINIDO]`, `DIRETÓRIO DE PROGRAMAS`. O sistema fala a língua do tema, não a do usuário. | Correspondência entre sistema e mundo real | medium | `strings_terminal.xml`, `onboarding_boot_subtitle` (observado) | P1, P5 | Manter o clima no título e nos logs, mas escrever o que importa em linguagem direta (`ainda não é o padrão` em vez de `[NÃO DEFINIDO]`; `lista de apps` ao lado de `DIRETÓRIO`). | low |
| H005 | O campo da tela inicial parece digitável (`>`, texto de espaço reservado, cursor piscando), mas o toque abre outra tela (`CommandActivity.start`). Quem espera digitar no lugar vê uma troca de contexto. | Consistência e padrões | medium | `LawnQsbUi` promptRow, `onQsbClick` (observado) | P1, P2 | Aceitar a convenção (é um atalho), mas deixar claro: seta ou ícone de expandir, e abrir a barra com o teclado já visível e sem animação longa. | low |
| H007 | Falha de comando: `NENHUM APP PODE EXECUTAR ESTE COMANDO` (`command_failed`). Não diz o que foi interpretado nem o que tentar. | Ajudar a reconhecer, diagnosticar e recuperar erros | medium | `strings_terminal.xml` (observado) | P1, P5 | `NÃO ENTENDI "xyz". DIGITE ? PARA VER OS COMANDOS` com `?` clicável. | low |
| H009 | Diretório: o índice tem 18 dp de largura; a única resposta ao toque é pintar a célula da letra ativa, que fica sob o dedo. Sem balão ou texto grande da letra, quem arrasta não sabe onde está até a lista parar. | Lei de Fitts | medium | `DirectoryIndexView`, `directory_index_width` (observado); efeito (simulado, P3) | P3, P1 | Balão ou letra grande ao lado do dedo, largura mínima de 24 dp de toque (pode ser invisível), toque simples também rola. | medium |
| H006 | A barra mostra `[▲] HISTÓRICO`, `[TAB] COMPLETAR`, `[▼] PRÓXIMA`. São botões clicáveis (44 dp), mas nomeados como teclas que o celular não tem. | Correspondência entre sistema e mundo real | low | `command_key_*` (observado) | P1, P5 | `[→] COMPLETAR`, `[↑] ANTERIORES`; ou texto: `COMPLETAR`. | low |
| H008 | O botão do cartão "launcher padrão" no dashboard chama `finish()` na activity de configurações depois de abrir as configurações do Android. Quem volta do sistema está de novo na home, não no dashboard. | Controle e liberdade do usuário | low | `PreferencesSetDefaultLauncherWarning` (observado) | P1, P5 | Não fechar a activity; só reavaliar `isDefaultLauncher` ao retomar. | low |
| H010 | Parágrafos longos em caixa alta, monoespaçados, de 10,5 a 12,5 sp (`onboarding_default_revert`, `onboarding_color_hint`, `onboarding_try_hint`). Caixa alta reduz a velocidade de leitura de frases. | Estética e design minimalista | low | `OnboardingActivity`, strings (observado) | P1, P5 | Caixa alta só em rótulos curtos; frases em caixa normal. | low |
| H011 | O log de boot mostra `VERIFICANDO MEMÓRIA`, `CARREGANDO TEMA DE ÍCONES`, `INDEXANDO PROGRAMAS` sempre com `[OK]` (`ok = true` fixo). É decoração apresentada como estado real. | Visibilidade do status do sistema | low | `BootStep` (observado) | P4 | Deixar claro que é ambientação, ou ligar a status real (ex.: apps indexados: N). | low |
| H012 | Voltar na primeira tela sai sem `markDone`, e o onboarding reaparece até 3 vezes (`MAX_AUTO_OPENINGS`). O reabrir pelo menu (`Rever configuração inicial`) está escrito em caixa normal e sem ícone, diferente do resto. | Controle e liberdade do usuário | low | `shouldShow`, `PreferencesOverflowMenu` (observado) | P4, P5 | Voltar na primeira tela conta como "pular"; mensagem curta ao sair. | low |
| H013 (positivo) | O onboarding nunca bloqueia: cada passo pode ser pulado, o estado é relido ao voltar do sistema (`ON_RESUME`), o texto de reversão ensina como voltar ao launcher anterior, e as linhas de exemplo têm 48 dp, `onClickLabel` e abrem a barra com a linha digitada. O pulso respeita a escala de animação do sistema. | Controle e liberdade do usuário | positive | `OnboardingActivity` (observado) | todas | Manter. | n/a |
| H014 (positivo) | Diretório: aviso tátil por letra (`CLOCK_TICK`), tamanho do texto adaptativo, esconde o índice com uma única seção e escolhe a cor do texto da letra ativa por contraste (`onAccent`). | Flexibilidade e eficiência de uso | positive | `DirectoryIndexView` (observado) | P3, P4 | Manter. | n/a |
| H015 (positivo) | Dashboard: seis entradas, descrições concretas (`Atribua uma letra a ações de outros apps (w = WhatsApp)`), cartão do launcher padrão no topo só quando falta. | Arquitetura da informação | positive | `PreferencesDashboard` (observado) | P4, P2 | Manter. | n/a |

Cobertura das 10 heurísticas: 1 (H011), 2 (H002, H006), 3 (H008, H012), 4 (H005), 5 (sem achado: o único passo irreversível, trocar o launcher padrão, é explicado e reversível), 6 (sem achado na barra: chips de recentes e fixados), 7 (H014 positivo), 8 (H010), 9 (H007), 10 (H004). Aprovadas: 5, 6, 7.

```json
{"heuristics_passed":["Prevenção de erros","Reconhecimento em vez de memorização","Flexibilidade e eficiência de uso"],
 "heuristics_failed":["Ajuda e documentação","Correspondência entre sistema e mundo real","Visibilidade do status do sistema","Consistência e padrões","Controle e liberdade do usuário","Estética e design minimalista","Ajudar a reconhecer, diagnosticar e recuperar erros"],
 "usability_score":59,
 "score_rationale":"100 menos 2 high (16), 5 medium (20) e 5 low (5) = 59. Sem critical."}
```

---

## 6. Acessibilidade (Fase 5), WCAG 2.2

Contrastes medidos a partir das cores do código no tema padrão: fósforo `#7DFFB2` (`NOSTROMO_PHOSPHOR`), fundo `#07090A` (`LauncherGround.DEFAULT`). `dim` = mistura 0,62; `line` = mistura 0,4 (`CommandActivity`).

| Elemento | Cores | Contraste | Critério |
|---|---|---|---|
| Texto principal, `>` | `#7DFFB2` sobre `#07090A` | 15,98:1 | 4.5:1 (passa) |
| Rótulos `dim` (10 sp) | `#50A272` | 6,42:1 | 4.5:1 (passa) |
| Índice do diretório (alpha 0xB0) | `#58B37E` | 7,76:1 | 4.5:1 (passa) |
| Letra ativa do índice | fundo ground sobre fósforo | 15,98:1 | passa |
| Bordas `line` | `#366B4D` | 3,20:1 | 3:1 (passa, margem curta) |
| Texto fantasma do autocompletar (`phosphor` alpha 0,4) | `#366B4D` | **3,20:1** | 4.5:1 (**falha**) |
| Perigo / aviso (`#FF5A45` / `#F2B84B`) | sobre fundo | 6,46:1 / 11,15:1 | passa |
| Texto do botão sobre perigo / aviso | `#1A0502` / `#1A1202` | 6,38:1 / 10,37:1 | passa |
| Rótulo `kind` ativo (ground a 75% sobre fósforo) | `#244634` sobre fósforo | 8,41:1 | passa |
| `dim()` do onboarding (`onSurfaceVariant` × 0,75) | cor do esquema derivado | não calculável sem o esquema resolvido | risco potencial |

| ID | Problema | WCAG | Nível | Status | Sev. | Personas | Impacto | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|---|---|
| A001 | O texto fantasma do autocompletar (a conclusão sugerida, que `[TAB]` aceita) é desenhado com o fósforo a 40% de alpha: 3,2:1 sobre o fundo. É informação, não decoração. | 1.4.3 Contrast (Minimum) | AA | falha confirmada | medium | P1 | Quem tem baixa visão não distingue a sugestão do texto digitado | Subir para alpha 0,62 (6,4:1) e manter a diferença de peso ou sublinhado | low |
| A002 | O índice do diretório tem 18 dp de largura e células de altura `altura/seções` (cerca de 20 a 24 dp com 26 letras). Células adjacentes, sem espaço entre elas. Há alternativa funcional (rolar a lista, filtrar), o que pode ser aceito como equivalente. | 2.5.8 Target Size (Minimum) | AA | risco potencial | medium | P3 | Tremor e polegar largo escorregam para a letra ao lado | Largura de toque de 24 dp ou mais (a faixa visual pode continuar fina) e altura mínima por célula; tocar na letra já rola | medium |
| A003 | O índice está marcado `IMPORTANT_FOR_ACCESSIBILITY_NO` e não tem nome ou ação para leitor de tela. A lista continua navegável, mas o atalho por letra some para TalkBack. | 4.1.2 Name, Role, Value | A | risco potencial | medium | P1 | Quem usa leitor de tela perde o atalho alfabético (a rolagem da lista continua) | Expor como controle de ajuste (`AccessibilityAction` por seção) ou documentar que a lista é a via equivalente | medium |
| A004 | O sistema de cores aceita pares com contraste a partir de 1,5:1 (`MIN_VISIBILITY`), avisando só abaixo de 3:1 (`COMFORTABLE_VISIBILITY`). Todas as telas (onboarding, barra, dashboard) herdam essa cor, e o texto secundário usa 62% do mesmo fósforo. | 1.4.3 / 1.4.11 | AA | risco potencial | medium | P1, P2 | Um tema bonito mas ilegível quebra todas as telas, inclusive a de desfazer | Piso de 4,5:1 para o texto principal, ou travar o `dim` ao contraste mínimo derivado (fora do escopo desta análise a tela Aparência, o efeito é aqui) | medium |
| A005 | Nome acessível da barra inicial: o contêiner usa `state.contentDescription` (rótulo de busca) enquanto o texto visível é `> DIGITE UM COMANDO`. O nome falado pode não conter o texto visível. | 2.5.3 Label in Name | A | risco potencial | low | P1 | Comando de voz "tocar em digite um comando" não encontra o controle | Nome acessível começar por `Digite um comando` | low |
| A006 | O cursor da barra inicial pisca para sempre (1 Hz) sem pausa e sem checar a escala de animação do sistema; o pulso do onboarding checa. | 2.2.2 Pause, Stop, Hide | A | risco potencial | low | P1 | Distração para quem tem dificuldade de atenção | Desligar o piscar quando `ANIMATOR_DURATION_SCALE == 0` e parar após alguns ciclos | low |
| A007 | Parágrafos longos, em caixa alta e monoespaçados (onboarding), jargão temático e idioma misto no passo do launcher padrão. | Acessibilidade cognitiva (linguagem simples) | n/a | risco potencial | low | P1, P5 | Mais esforço de leitura e compreensão | Ver H001, H002, H010 | low |

Verificado sem falha: nome acessível nos botões da barra (`[MIC]` e `[PIN]` com `contentDescription`, 44 dp), `liveRegion` Polite/Assertive nos resultados da barra, 52 dp no botão primário e 48 dp no secundário do onboarding, status do log em texto além da cor (1.4.1), arrastar não é necessário no índice (2.5.7: tocar basta), alvos das linhas de exemplo de 48 dp. Teclado físico, ordem de foco e TalkBack real: **não avaliável** com o código apenas.

```json
{"wcag_a":"partial","wcag_aa":"partial","total_issues":7,"critical_issues":0,"high_issues":0,
 "accessibility_score":88.5,
 "score_rationale":"100 menos falha confirmada A001 (medium, 4) menos riscos potenciais pela metade: A002, A003, A004 (medium, 3 x 4 x 0,5 = 6) e A005, A006, A007 (low, 3 x 1 x 0,5 = 1,5) = 100 - 4 - 6 - 1,5 = 88,5."}
```

---

## 7. Insights comportamentais (Fase 6, enxuta)

| ID | Barreira | Framework | Gatilho | Personas | Sev. | Corrigível por design |
|---|---|---|---|---|---|---|
| B001 | Quem pula não recebe gatilho para o primeiro comando. Motivação média, habilidade baixa (não sabe a sintaxe), gatilho ausente depois do skip. | Modelo de Fogg | `PULAR CONFIGURAÇÃO`, home sem exemplo | P5, P4 | high | sim (H004, O2) |
| B002 | O último passo concentra 7 elementos antes da ação útil (título 44 sp, 3 logs, aviso, hint, painel). Carga extrínseca. | Carga cognitiva, Lei de Hick | `ReadyStep` | P1, P5 | medium | sim (H003) |
| B003 | Trocar o launcher padrão pesa como risco de perda; o texto que acalma (`VOCÊ PODE VOLTAR QUANDO QUISER`) vem abaixo do texto em inglês. | Aversão à perda | passo 1 | P1, P5 | medium | sim (H001) |
| B004 | O tema é escolhido antes de qualquer valor entregue (passo 2 de 3), com uma roda de cores e uma fileira de opções. | Fadiga de decisão, compromisso e consistência | `ThemeStep` | P1, P3 | low | sim (reordenar: comando primeiro) |

```json
{"initial_trust":6,"process_trust":7,"outcome_trust":6,
 "trust_gaps":["texto em inglês no passo do launcher padrão","jargão sem tradução","falha de comando sem próxima ação"],
 "trust_builders_present":["pular em qualquer passo","texto de reversão","estado relido ao voltar das configurações","contraste alto no tema padrão","exemplos que abrem a barra com a linha pronta"],
 "trust_builders_missing":["exemplo na tela inicial","prova de que o comando funciona antes de pedir permissões","indicação do que o app faz com as notificações além do ponto no ícone"]}
```

Mapa Motivação-Habilidade-Gatilho:

| Ação-chave | Motivação | Habilidade | Gatilho | Resultado | Recomendação |
|---|---|---|---|---|---|
| Definir launcher padrão | media a alta | media (sai do app) | forte (botão primário) | likely_complete | texto traduzido e claro (H001) |
| Escolher um tema | alta (P2) / baixa (P1) | alta | forte | likely_complete | deixar para depois do primeiro comando |
| Executar o primeiro comando | media | baixa (sintaxe) | fraca (último passo, ou ausente se pulou) | at_risk | O1 e O2 |
| Achar um app pela gaveta | alta | media (P3 baixa por tremor) | forte (hint e índice) | likely_complete | O4 |

Oportunidades comportamentais: (1) micro-compromisso: pedir um comando antes do tema (consistência), (2) exemplo rotativo como gatilho contextual (Fogg), (3) enquadrar o launcher padrão como ganho concreto, não como configuração.

---

## 8. Insights e padrões entre fases (Fase 7)

| ID | Insight | Confiança | Sustentado por | Personas | Impacto no negócio | Impacto no usuário |
|---|---|---|---|---|---|---|
| I001 | O produto entrega o diferencial (barra de comando) por um caminho que quem pula nunca vê: exemplo só no último passo e nenhum na home. | alta (código) | H003, H004, B001, B002 | P1, P2, P5 | menos usuários chegam ao primeiro comando e à retenção | sente "um launcher preto com texto verde" |
| I002 | Idioma e jargão pesam mais do que a estética no início: português misturado com inglês e referências de filme. | média (código mais simulação) | H001, H002, H010, A007, B003 | P1, P5 | queda de confiança no passo que pede uma ação irreversível | estranhamento e hesitação |
| I003 | Os alvos pequenos e a cor customizável tornam a acessibilidade dependente do tema e da mão do usuário. | média | H009, A002, A004, A001 | P1, P3 | usuários com necessidades específicas abandonam | erro de toque, texto ilegível |
| I004 | Os pontos fortes (pular sempre, desfazer, exemplos tocáveis, barra com voz e fixados) estão prontos, só mal ordenados. | alta | H013, H014, H015 | todas | correções baratas | |

Conflito registrado: a Fase 4 (H005) sugere reduzir a ambiguidade do campo falso; a Fase 6 (B001) quer que o campo seja o gatilho do primeiro comando. As duas convergem se o campo ganhar um exemplo rotativo (O2) sem fingir que é digitável no lugar.

---

## 9. Oportunidades priorizadas (Fase 8)

| ID | Oportunidade | Resolve | Impacto | Esforço | Prioridade |
|---|---|---|---|---|---|
| O1 | Subir o "experimente" no passo final; `ENTRAR NO TERMINAL` vira secundário até um exemplo ser tocado | H003, B002, I001 | high | low | P0 |
| O2 | Placeholder rotativo com exemplos reais e dica `?` na barra da tela inicial | H004, H005, B001, I001 | high | low | P0 |
| O3 | Traduzir `set_default_launcher_tip` (pt, pt-BR, pt-PT) e checar chaves faltantes | H001, I002 | medium | low | P0 |
| O4 | Índice do diretório: balão com a letra, área de toque de 24 dp ou mais, ação de acessibilidade | H009, A002, A003, I003 | medium | medium | P1 |
| O5 | Falha de comando com próxima ação; rótulos de teclas sem `[TAB]`; menos jargão no onboarding | H007, H006, H002, I002 | medium | low | P1 |
| O6 | Contraste do texto fantasma e piso de contraste para temas | A001, A004, I003 | medium | low a medium | P1 |
| O7 | Dashboard: não fechar a activity ao abrir as configurações do sistema; Voltar na primeira tela conta como pular | H008, H012 | low | low | P3 |
| O8 | Frases do onboarding em caixa normal; log com estado real | H010, H011 | low | low | P3 |

---

## 10. Experimentos (Fase 9)

| Oportunidade | Hipótese | Método | Participantes | Métrica de sucesso | Baseline necessário | Meta (a calibrar) |
|---|---|---|---|---|---|---|
| O1 | Se o "experimente" subir no último passo e o botão final ficar secundário, a parcela que executa um comando no onboarding vai subir, porque o gatilho fica visível sem rolagem. | A/B test na faixa de testes | testadores novos, divididos ao meio | % que toca em uma linha de exemplo | % atual (instrumentar o `onTry`) | a definir após o baseline |
| O2 | Se a barra da tela inicial mostrar exemplos rotativos, mais usuários que pulam executam um comando no dia 1. | A/B test, ou teste de usabilidade não moderado com 6 a 8 pessoas que pulam | novos usuários que tocaram em pular | % com 1 ou mais comandos no dia 1 | comandos no dia 1 hoje | a definir |
| O3 | Se o passo do launcher padrão estiver todo em português, a parcela que toca em `Definir agora` não cai. | Análise de funil por idioma | usuários pt, pt-BR e pt-PT | taxa de toque em `Definir agora` | funil atual | sem queda, e melhora no pt-PT |
| O4 | Com balão e área maior, a precisão de seleção da letra sobe e o tempo para achar um app cai. | Teste de usabilidade moderado | 5 pessoas, incluindo 2 com tremor ou polegar largo | acertos na 1ª tentativa, tempo | medir a versão atual | a definir |
| O5 | Mensagem de falha com próxima ação reduz comandos repetidos sem sucesso. | Análise de funil | todos | repetição do mesmo comando falho em 30 s | taxa de `command_failed` | a definir |

As metas numéricas são hipóteses, não previsões. Correções `observado` e de esforço baixo (O3, parte de O5, O7, O8) podem ir direto para implementação.

### Roadmap sugerido

| Sprint | Ação | Tipo | Impacto |
|---|---|---|---|
| 1 | O3 (traduzir a string), O2 (placeholder com exemplo), O1 (reordenar o passo final) | conteúdo, dev | high |
| 2 | O5 (falha de comando, rótulos de teclas), O6 (contraste do texto fantasma), O7, O8 | dev, conteúdo | medium |
| 3 | O4 (índice do diretório) e o piso de contraste do tema | design, dev | medium |

---

## 11. Lacunas e premissas

- **Não avaliado:** teclado físico, ordem de foco, TalkBack real, comportamento com fonte 200%, o que a gaveta mostra com 0 ou 1 seção (o índice se esconde), a lista inicial de sugestões da barra vazia (só vi o código dos chips e das linhas), qualquer tela que não seja as cinco do escopo.
- **Só simulado:** a conclusão das personas (35, 85, 55, 90, 40%), o escorregar do polegar no índice, o abandono de P5 e de P1. Validar com pessoas reais.
- **Premissas a validar:** que a estética de terminal atrai e não afasta; que o público sabe que existe uma barra de comando; que três passos são aceitáveis; que o piso de 1,5:1 de contraste do tema não é usado na prática.
- **Fora do escopo por instrução:** tela Aparência (A004 aponta só o efeito do contraste das cores nas outras telas).
- **Observação do `dim()` do onboarding:** o contraste de `onSurfaceVariant × 0,75` não pôde ser calculado sem o esquema de cores resolvido em execução.
