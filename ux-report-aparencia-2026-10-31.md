# Análise de UX: tela Aparência do Terminal Launcher

- **Produto:** Terminal Launcher (`net.mdantas.terminal`)
- **Tela avaliada:** Configurações > Aparência (`AppearancePreferences.kt` e componentes filhos)
- **Data:** 2026-10-31
- **Modo:** análise unificada, profundidade rápida (5 personas, fases 5 e 6 enxutas)
- **Fonte:** código lido (`AppearancePreferences.kt`, `SavedThemesPreference.kt`, `ComplementaryColorsPreference.kt`, `FollowSystemColorModePreference.kt`, `ColorPreference.kt`, `ColorContrastWarning.kt`, `PreferencesDashboard.kt`) e strings `en` e `pt`. Não rodei o app, não testei TalkBack nem medi contraste em dispositivo.
- **Não li:** `ComplementaryWheelDialog`, `ColorSelectionPreference`, `FontPreference` e as telas de formato. O que dependa deles aparece como não avaliável.

---

## 1. Sumário executivo

### UX Score: 67/100

`0,45 × Usabilidade + 0,25 × Acessibilidade + 0,30 × Comportamento/Confiança`

| Componente | Conta | Nota |
|---|---|---|
| Usabilidade (Fase 4) | 100 − 3 high×8 − 5 medium×4 − 3 low×1 = 100 − 24 − 20 − 3 | **53** |
| Acessibilidade (Fase 5) | 100 − metade de (4 medium×4 + 2 low×1) = 100 − 9 (nenhuma falha confirmada; A005 não avaliável não pontua) | **91** |
| Comportamento/Confiança (Fase 6) | média(7, 6, 7) × 10 = 66,7, sem barreira critical | **66,7** |

0,45×53 + 0,25×91 + 0,30×66,7 = 23,85 + 22,75 + 20,0 = **66,6 ≈ 67**

A leitura qualitativa bate com o número. A tela tem muito poder e boas ideias (looks prontos, roda de cores, prévia da animação, confirmação na tela de bloqueio). Perde pontos por **sobrecarga** (cerca de 25 controles em 6 grupos), **falta de prévia da home** enquanto se muda cor e papel de parede, e **três chaves de papel de parede que se sobrepõem**. A nota de acessibilidade é otimista: nada foi confirmado em TalkBack nem em fonte grande, e o que existe é risco potencial.

### Top 3 problemas

1. **H001 (high):** carga cognitiva. Uma página rolável mistura temas, 7 cores, papel de parede, formatos, fonte e 5 efeitos, e todos têm o mesmo peso visual.
2. **H002 (high):** as chaves "Mostrar papel de parede", "Cor de fundo como papel de parede" e "Também na tela de bloqueio" se sobrepõem, são independentes no código e estão fora de ordem lógica.
3. **H003 (high):** a tela não mostra a home enquanto o usuário muda cores, papel de parede e efeitos. Só a animação de tela tem prévia.

### Top 5 oportunidades

| ID | Oportunidade | Impacto | Esforço | Prioridade |
|---|---|---|---|---|
| O1 | Prévia da home fixa no topo (ou um botão "Ver na home") | alto | médio | P1 |
| O2 | Reorganizar e agrupar as chaves de papel de parede em um fluxo único | alto | baixo | P0 |
| O3 | Recolher itens avançados (cores de componentes, efeitos) em "Avançado" | alto | baixo | P0 |
| O4 | Aplicar look/tema com "Desfazer" (snackbar) e marcar o tema ativo na lista | médio | baixo | P1 |
| O5 | Corrigir textos (pt-BR "Tamanho da pasta") e trocar jargão ("fósforo", "glifo") | médio | baixo | P0 |

---

## 2. Contexto do produto

```json
{
  "product_summary": "Launcher Android com estética de terminal, barra de comando e temas (.tthm) criados à mão ou por IA. A tela Aparência concentra tudo o que decide como o launcher parece.",
  "product_category": "App mobile de personalização (launcher), segmento de entusiastas",
  "market_context": "Concorrentes e referências: Lawnchair, Nova, Niagara, Material You. O usuário espera ver o efeito de uma mudança de cor na hora.",
  "primary_users": ["entusiastas de personalização", "usuários técnicos que gostam da estética de terminal", "usuários que só querem tema escuro legível"],
  "business_goals": ["retenção via personalização", "diferenciar o launcher pelo visual terminal e pelos temas por IA"],
  "user_goals": ["deixar a home com a cara que quero", "trocar de look rápido", "não estragar o que já funciona", "não perder meu papel de parede"],
  "constraints": ["tela sobre Compose + PreferenceLayout do Lawnchair", "paridade com a barra de comando e a theme spec (CLAUDE.md)"],
  "risks": [
    {"risk": "Usuário não entende o efeito de uma chave e deixa a home ilegível", "likelihood": "medium", "impact": "high"},
    {"risk": "Perda do papel de parede do sistema ou da tela de bloqueio", "likelihood": "medium", "impact": "high"}
  ],
  "assumptions": [
    {"assumption": "A maioria dos usuários troca de look por chip pronto, não por cor individual", "validation_status": "unvalidated"},
    {"assumption": "Quem importa tema por IA chega pelo Guia de temas no fim da tela", "validation_status": "unvalidated"}
  ],
  "key_success_metrics": ["taxa de quem aplica um look e mantém por 7 dias", "reversões de look em menos de 1 minuto", "uso do Guia de temas", "ativação de 'Também na tela de bloqueio' seguida de desativação"],
  "missing_context": ["analytics de uso da tela", "ordem real de uso dos grupos", "ComplementaryWheelDialog, ColorSelection e FontPreference não lidos", "comportamento com TalkBack e fonte 200%"]
}
```

---

## 3. Personas

| ID | Nome | Idade | Papel | Nível técnico | Acessibilidade |
|---|---|---|---|---|---|
| P1 | Rafaela | 34 | Designer de produto, São Paulo | avançado | não |
| P2 | Antônio | 67 | Aposentado, Recife | iniciante | baixa visão, fonte do sistema em 200% |
| P3 | Lucas | 19 | Estudante de computação, Curitiba | expert | não |
| P4 | Camila | 41 | Enfermeira de plantão, Belo Horizonte | intermediário | tremor leve nas mãos, usa o celular com uma mão |
| P5 | Diego | 28 | Desenvolvedor cego, Porto Alegre | avançado | leitor de tela (TalkBack) |

Detalhes das personas (objetivo, cenário, medo):

- **P1 Rafaela.** Quer uma paleta própria com contraste cuidadoso. Usa o celular como vitrine. Medo: o launcher ficar "feio" por um default ruim. Cenário: domingo à noite, monta o look do mês, abre a roda de cores.
- **P2 Antônio.** Neto instalou o launcher. Quer só letras maiores e fundo escuro para ler sem óculos. Medo: "mexer e não conseguir voltar". Cenário: toca em Aparência, vê muitos itens pequenos e procura "Escuro".
- **P3 Lucas.** Fã de terminal. Já tem um tema de IA e quer importar o .tthm. Edge case: usa efeitos (CRT, chuva) e quer todos ligados. Cenário: procura onde importar e onde ajustar a intensidade.
- **P4 Camila.** Tem 2 minutos no intervalo. Quer a tela escura à noite sem reflexo. Medo: tocar errado e trocar algo que não sabe desfazer. Cenário: tenta mover um slider com o polegar tremendo.
- **P5 Diego.** Usa TalkBack. Quer trocar de look sem pedir ajuda. Edge case: navega por foco linear, depende de rótulo e estado. Cenário: percorre a lista de looks e tenta saber qual está selecionado.

---

## 4. Simulação cognitiva (resumida)

Tudo desta fase é `simulado`, salvo onde marcado.

| Persona | Primeira impressão | Principal confusão | Risco de abandono | Conclusão (qualitativa) |
|---|---|---|---|---|
| P1 | "Ótimo, tem roda de cores." | Não vê a home mudar enquanto ajusta; precisa sair e voltar | baixo | alta |
| P2 | "Quanta coisa." | 25 controles; textos curtos de 11sp nos chips; não sabe o que é "Cor de destaque" vs "Cor secundária" | **alto** (some na primeira rolagem) | baixa |
| P3 | "Aqui sim." | Acha o Guia de temas só no fim; "ia tema" aparece no texto vazio sem explicação do que é | baixo | alta |
| P4 | "Tenho pouco tempo." | Slider de intensidade e roda de cores exigem precisão; tocar em um look aplica na hora, sem desfazer | médio | média |
| P5 | "Que lista." | Não sabe qual look está selecionado; o ">_" dos chips é lido como ruído | **alto** | baixa |

Monólogos (resumo):
- **P2:** "Fundo do dock, fundo das pastas, contorno da barra de busca... eu só queria o escuro."
- **P5:** "Vermelho, botão. Laranja, botão. Qual está ligado? Não me diz."
- **P4:** "Toquei num chip e mudou tudo. E agora, como volto ao que eu tinha?"

Duas confusões não se repetem entre as personas: P2 trava na quantidade e no jargão, P5 trava no estado e no nome acessível, P4 trava no desfazer e na precisão motora.

---

## 5. Problemas heurísticos

| ID | Achado | Categoria | Sev. | Evidência | Personas | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|
| H001 | A tela empilha 6 grupos e cerca de 25 controles na mesma página, todos com o mesmo peso (Temas 2, Cores 7, Papel de parede 3 a 5, Formatos 2, Fonte 2, Efeitos 3 a 7, Guia 1). | Estética e design minimalista (carga cognitiva, Hick) | high | `observado` em `AppearancePreferences.kt` | P2, P4, P5 | Mostrar por padrão só Temas, Cores principais (destaque, fundo, secundária) e Papel de parede. Mover "contorno da busca", "fundo do dock", "fundo das pastas" e Efeitos para uma seção "Avançado" recolhível. | baixo |
| H002 | Três chaves de papel de parede se sobrepõem: "Mostrar papel de parede", "Cor de fundo como papel de parede" e "Também na tela de bloqueio". O código as trata como independentes e a ordem não segue a lógica "primeiro escolho a fonte, depois onde vale". | Consistência e padrões | high | `observado` (blocos `colorAsWallpaper`, `colorOnLockScreen`, `showSystemWallpaper`) e `inferido` (combinações contraditórias) | P2, P4 | Trocar por uma escolha única com 3 estados ("Papel de parede do sistema", "Só a cor de fundo", "Cor de fundo, inclusive na tela de bloqueio") ou deixar "Também na tela de bloqueio" visível só quando "Cor de fundo como papel de parede" estiver ligada. | baixo |
| H003 | Nenhuma prévia da home enquanto se mudam cores, papel de parede, formato e efeitos. Só a animação de tela tem `ScreenAnimationPreview`. | Visibilidade do status do sistema | high | `observado`: não há prévia no arquivo. Existem `LauncherPreview.kt` e `WallpaperPreview.kt` no repo, que não li | P1, P2, P4 | Prévia compacta fixa no topo (ou botão "Ver na home" que abre a home e volta). | médio |
| H004 | Em pt-BR, `folder_shape_label` está como "Tamanho da pasta" para o formato da pasta (em en: "Folder shape"; em pt-PT: "Forma da pasta"). | Correspondência entre o sistema e o mundo real | medium | `observado` em `values-pt-rBR/strings.xml:175` | P2, P5 | Trocar para "Formato da pasta" (par de "Formato do ícone"). | baixo |
| H005 | Jargão de implementação nos textos: "fósforo", "glifo", "linhas de varredura", ".tthm", "ia tema", "Triade/Análogas". | Correspondência entre o sistema e o mundo real | medium | `observado` em `strings_terminal.xml` | P2, P4 | Descrever o efeito em palavras do cotidiano; explicar ".tthm" uma vez, no Guia. | baixo |
| H006 | O grupo Cores mistura cores globais (destaque, fundo, secundária) com cores de componentes (contorno da busca, dock, pastas). | Arquitetura da informação | medium | `observado` | P2, P1 | Separar: "Cores do tema" e "Cores por componente". | baixo |
| H007 | A fileira de looks é um `horizontalScroll` com a roda de cores primeiro e 12 matizes mais os pastéis depois, sem sinal de que há mais à direita. | Reconhecimento em vez de memorização (affordance) | medium | `observado` (`ComplementaryColorsPreference`) | P2, P4 | Cortar o último chip pela metade ou mostrar setas e o contador. | baixo |
| H008 | Aplicar um look ou tema salvo sobrescreve fundo, destaque e secundária sem prévia nem "Desfazer"; só um Toast confirma. A lista de temas salvos não marca o ativo. | Controle e liberdade do usuário | medium | `observado` (`applyPair`, `ThemeStore.apply`) e `inferido` (look atual perdido se não foi salvo) | P4, P1 | Snackbar "Look aplicado. Desfazer" e uma marca "Em uso" no tema ativo. | baixo |
| H009 | "Cor de fundo como papel de parede" troca o papel de parede do sistema sem confirmação, enquanto "Também na tela de bloqueio" tem diálogo. Não sei se a troca é recuperável. | Prevenção de erros | medium | `observado` (sem diálogo) e `inferido` (reversibilidade desconhecida) | P2, P4 | Dar o mesmo tratamento nas duas chaves, ou explicar no texto o que se perde. | baixo |
| H010 | "Compartilhar" aparece duas vezes com alvos diferentes: ícone por tema (compartilha aquele tema) e botão da fileira (compartilha o look atual). | Consistência e padrões | low | `observado` em `SavedThemesPreference` | P3, P5 | Rotular o botão da fileira "Compartilhar look atual". | baixo |
| H011 | O resumo do tema salvo é monoespaçado, 12sp, com `maxLines = 1` e reticências. | Reconhecimento em vez de memorização | low | `observado` | P2 | Mostrar as duas ou três cores como chips, não como texto. | baixo |
| H012 | Efeito dos ícones e efeito dos widgets não têm prévia, e o grupo "Efeitos do terminal" fica no fim, depois da fonte. | Ajuda e documentação | low | `observado` | P3, P2 | Miniatura antes/depois ao lado da chave. | médio |
| H013 | **Positivo.** `ModeSwatch` mostra o par claro/escuro antes de ligar "Acompanhar o modo do sistema"; `ExpandAndShrink` esconde o que só importa com a chave ligada; a tela de bloqueio pede confirmação; `LookChip` marca o selecionado; a animação tem prévia. | Várias | positive | `observado` | todas | Manter e replicar o padrão nos demais itens. | n/a |
| H014 | **Positivo.** Excluir tema tem diálogo de confirmação com o nome do tema. | Prevenção de erros | positive | `observado` | todas | Manter. | n/a |

**Heurísticas sem problema relevante:** Estética (parcial, ver H001), Flexibilidade e eficiência (a roda, os looks e o tema por IA atendem de iniciante a expert).

```json
{"heuristics_passed": ["7 Flexibilidade e eficiência de uso"], "heuristics_failed": ["1","2","3","4","5","6","8","10"], "usability_score": 53, "score_rationale": "3 high (−24), 5 medium (−20), 3 low (−3). 'Heurística 9' não tem achado: a tela não gera erros de validação visíveis no código lido."}
```

---

## 6. Acessibilidade (WCAG 2.2)

Nada aqui é `falha confirmada`: sem TalkBack, sem fonte 200% e sem medir o contraste em aparelho, o máximo que o código permite é apontar risco.

| ID | Achado | WCAG | Nível | Status | Sev. | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|
| A001 | `LookChip` e `WheelChip` usam só `clickable` e o estado "selecionado" é visual (borda de 2dp na cor primária). Sem `Role.RadioButton`, `selected` ou `stateDescription`, o TalkBack pode ler o nome e o ">_" sem dizer qual está em uso. | 4.1.2 Name, Role, Value; 1.4.1 Use of Color | A | risco potencial | medium | `Modifier.selectable(selected, role = Role.RadioButton)` e `semantics { stateDescription }`; esconder o ">_" com `clearAndSetSemantics` e narrar "Azul, selecionado". | baixo |
| A002 | A prévia da animação (`ScreenAnimationPreview`) roda sem parar, sem pausa e sem verificar a opção "remover animações" do sistema (não confirmado no código lido). | 2.2.2 Pause, Stop, Hide | A | risco potencial | medium | Respeitar `ANIMATOR_DURATION_SCALE = 0`, ou mostrar quadro parado com botão "Reproduzir". | médio |
| A003 | Texto de tamanho fixo em partes do layout: `fontSize = 12.sp`, `maxLines = 1` e reticências no resumo do tema; rótulo do chip com `maxLines = 1` numa coluna de 64dp. Com fonte em 200% o nome do look e o do tema podem ficar cortados. O nome é o único identificador além da cor. | 1.4.4 Resize Text; 1.4.10 Reflow | AA | risco potencial | medium | Permitir 2 linhas no chip; largura mínima em vez de fixa; testar com fonte 200%. | baixo |
| A004 | `ColorContrastWarning` só avisa quando o contraste fica abaixo de **1,5:1**, enquanto a WCAG pede 3:1 (componentes e texto grande) e 4,5:1 (texto). O launcher permite ao usuário combinações ilegíveis sem aviso. | 1.4.3 Contrast (Minimum); 1.4.11 Non-text Contrast | AA | risco potencial | medium | Avisar abaixo de 3:1 e destacar abaixo de 4,5:1 para rótulos de ícone e relógio. Medir os looks prontos com o validador. | baixo |
| A005 | Roda de cores: "arraste um ponto na roda" é a instrução do diálogo. Não li se há alternativa sem arrastar (campo hexadecimal, setas, sliders). | 2.5.7 Dragging Movements | AA | não avaliável: arquivo não lido | n/a | Garantir entrada por valor ou controles de toque único. | médio |
| A006 | `ModeSwatch` e `LookChip` têm o texto decorativo ">_" e ícones com `contentDescription = null` dentro de itens agrupados; o ">_" tende a ser lido como "maior que, sublinhado". | 1.1.1 Non-text Content; 4.1.2 | A | risco potencial | low | Marcar como decorativo e descrever o par ("claro e escuro") no item. | baixo |
| A007 | Textos como "fósforo", "glifo", "linhas de varredura" e ".tthm" exigem leitura técnica. | 3.1.5 Reading Level | AAA | risco potencial | low | Linguagem simples; explicar termos na primeira ocorrência. | baixo |

```json
{"wcag_a": "partial", "wcag_aa": "partial", "total_issues": 7, "critical_issues": 0, "high_issues": 0, "accessibility_score": 91, "score_rationale": "6 riscos potenciais (4 medium, 2 low) contam metade: (4×4 + 2×1)/2 = 9. 1 item não avaliável (A005) não pontua. Sem falha confirmada; a nota sobe ou cai depois de um teste real com TalkBack e fonte 200%."}
```

Pontos que a tela já atende: alvos de toque (chips de cerca de 72×64dp e `IconButton` de 48dp acima dos 24dp de 2.5.8), confirmação antes de apagar tema e antes de sobrescrever a tela de bloqueio (3.3.4).

---

## 7. Psicologia comportamental

| ID | Barreira | Framework | Gatilho | Sev. | Corrigível por design | Recomendação |
|---|---|---|---|---|---|---|
| B001 | Quem chega querendo "só o escuro" encara 13 chips de look, 7 cores e 5 efeitos. Cai no primeiro default que entender, ou sai. | Lei de Hick e fadiga de decisão | rolagem da tela | high | sim | Atalhos no topo ("Escuro", "Claro", "Seguir o sistema") antes de qualquer lista. Alinha-se a H001. |
| B002 | O receio de perder o papel de parede ou o look atual faz o usuário evitar as chaves de papel de parede, mesmo úteis. | Aversão à perda | "Também na tela de bloqueio", aplicar look | medium | sim | Dizer o que se perde, oferecer "Restaurar o anterior" por tempo limitado. |
| B003 | Sem prévia, o usuário só descobre o resultado depois de sair da tela, o que quebra o ciclo de tentativa e erro. | Confiança de processo | qualquer mudança de cor | high | sim | Prévia fixa (O1). Reforça H003. |
| B004 | Motivação alta para personalizar, mas a habilidade cai para quem tem tremor ou baixa visão (slider e roda). | Modelo de Fogg (habilidade) | sliders de intensidade, roda | medium | em parte | Passos discretos e entrada por valor; áreas de toque maiores. |

```json
{"initial_trust": 7, "process_trust": 6, "outcome_trust": 7,
 "trust_gaps": ["sem prévia da home", "sem desfazer ao aplicar look", "papel de parede e tela de bloqueio sem explicação do que se perde"],
 "trust_builders_present": ["confirmação na tela de bloqueio e ao apagar tema", "prévia da animação", "swatch claro/escuro", "chip selecionado com borda"],
 "trust_builders_missing": ["marca de tema ativo", "prévia da home", "Desfazer", "indicador de que a fileira rola"]}
```

**Mapa Motivação, Habilidade e Gatilho**

| Ação-chave | Motivação | Habilidade | Gatilho | Resultado | Recomendação |
|---|---|---|---|---|---|
| Aplicar um look pronto | alta | alta | forte (chip visível no topo) | provável conclusão | Adicionar Desfazer |
| Montar cores à mão | média | média | fraco (7 linhas iguais) | em risco | Agrupar e mostrar prévia |
| Trocar papel de parede e tela de bloqueio | média | baixa | fraco | em risco | Um fluxo único, com texto de consequência |
| Importar um tema por IA | média | média | fraco (Guia no fim da tela) | em risco | Atalho "Importar tema" ao lado de Salvar |

**Oportunidades comportamentais:** (1) atalhos "Escuro/Claro/Sistema" no topo reduzem a decisão inicial (Hick); (2) "Desfazer" transforma a aversão à perda em segurança para experimentar (Prospect Theory); (3) marcar o tema em uso dá feedback de competência (Autodeterminação).

---

## 8. Insights e padrões

| ID | Insight | Confiança | Sustentado por | Personas | Impacto no negócio | Impacto no usuário |
|---|---|---|---|---|---|---|
| I001 | A tela prioriza poder e mostra tudo ao mesmo tempo; quem quer o simples paga o custo de quem quer o avançado. | alta | H001, H006, B001 | P2, P4, P5 | menos personalização concluída pelo público geral | abandono ou escolha ao acaso |
| I002 | Todas as ações que mudam a home dão pouco retorno (sem prévia, sem desfazer, sem tema ativo). | alta | H003, H008, B003, B002 | P1, P2, P4 | menos experimentação e mais reversões | insegurança ao mexer |
| I003 | O mesmo conjunto de controles de papel de parede combina conceitos de sistema, tela inicial e tela de bloqueio. | média | H002, H009, B002 | P2, P4 | risco de suporte e avaliação ruim | medo de perder o papel de parede |
| I004 | A acessibilidade é razoável na base (alvos, confirmações), mas os componentes novos (chips, swatch) foram desenhados só para quem enxerga. | média | A001, A003, A006 | P2, P5 | risco de avaliação por TalkBack e fonte grande | P5 sem saber o estado |

**Conflito registrado:** a Fase 4 considera o `ExpandAndShrink` um acerto (esconde o que depende de uma chave). A Fase 5 vê risco: conteúdo que aparece e some precisa de anúncio ao leitor de tela (4.1.3), o que não pude confirmar. As duas perspectivas valem; o teste com TalkBack decide.

---

## 9. Oportunidades priorizadas

| ID | Oportunidade | Resolve | Impacto | Esforço | Prioridade |
|---|---|---|---|---|---|
| O1 | Prévia da home fixa no topo ou botão "Ver na home" | H003, B003, I002 | alto | médio | P1 |
| O2 | Unificar as chaves de papel de parede em um fluxo com texto de consequência | H002, H009, B002, I003 | alto | baixo | P0 |
| O3 | Recolher cores por componente e efeitos em "Avançado"; atalhos Escuro/Claro/Sistema no topo | H001, H006, B001, I001 | alto | baixo | P0 |
| O4 | Snackbar "Desfazer" ao aplicar look ou tema e marca "Em uso" | H008, I002 | médio | baixo | P1 |
| O5 | Corrigir strings (pt-BR "Tamanho da pasta") e trocar jargão | H004, H005, A007 | médio | baixo | P0 |
| O6 | Semântica dos chips (`selectable`, `stateDescription`), chip com 2 linhas, ">_" decorativo | A001, A003, A006, I004 | médio | baixo | P1 |
| O7 | Respeitar "remover animações" na prévia; avisar contraste abaixo de 3:1 | A002, A004 | médio | baixo a médio | P2 |
| O8 | Indicador de rolagem na fileira de looks; rotular "Compartilhar look atual" | H007, H010 | baixo | baixo | P3 |

---

## 10. Experimentos

| Oportunidade | Hipótese | Método | Participantes | Métrica | Baseline necessário | Meta (a calibrar) |
|---|---|---|---|---|---|---|
| O3 | Se a tela abrir só com Temas, Cores principais e atalhos, mais gente conclui "deixar tudo escuro" sem ajuda, porque cai a decisão inicial. | Teste de usabilidade moderado, comparando versão atual e nova | 5 a 6 usuários por versão, incluindo P2 e P4 | tarefa concluída sem ajuda; tempo até o primeiro look aplicado | medir a versão atual | a calibrar após o baseline |
| O2 | Se as três chaves viram uma escolha única, menos pessoas ligam "Também na tela de bloqueio" sem querer. | Teste de primeiro clique e análise de eventos | 5 a 8 usuários e telemetria anônima de ativação seguida de desativação | taxa de ativação seguida de desativação em 24h | contagem atual | a calibrar |
| O1 | Se houver prévia da home, menos pessoas voltam à home para conferir. | Protótipo e teste não moderado | 8 usuários | número de idas e voltas à home por sessão | contar na versão atual | a calibrar |
| O6 | Se os chips têm estado acessível, usuários de TalkBack identificam o look em uso. | Teste moderado com TalkBack | 3 a 5 usuários de leitor de tela | acerto ao dizer qual look está ativo | nenhum (versão atual deve falhar) | a calibrar |

As correções `observado` de esforço baixo (O5, O8 e parte de O6) podem ir direto para implementação sem experimento.

---

## 11. Lacunas e premissas

- **`missing_context`:** sem analytics, sem a ordem real de uso, sem TalkBack, sem fonte 200%, sem medição de contraste em aparelho. Não li a roda de cores, a seleção de cor e a fonte.
- **Achados só `simulado`:** as confusões de P2, P4 e P5 vêm da simulação. "Afeta a maioria dos perfis simulados" não vale como percentual da população.
- **Metas numéricas:** os experimentos não trazem números prometidos de propósito; calibre com o baseline.
- **Premissas a validar com usuários reais:**
  1. Quem chega à tela busca um atalho (escuro, claro, sistema) e não uma paleta.
  2. O Guia de temas no fim da tela é suficiente para quem usa IA.
  3. As três chaves de papel de parede geram erro e medo de perda na prática (telemetria de ativar e desativar).
  4. Os chips se comportam em TalkBack como o código sugere.
- **Observação sobre o projeto:** mudanças nesta tela que criem nova customização visual devem atualizar `ThemeSpec`, "ia tema" e o prompt de temas (regra do `CLAUDE.md`). As recomendações acima não criam customização nova, então não disparam essa regra.
