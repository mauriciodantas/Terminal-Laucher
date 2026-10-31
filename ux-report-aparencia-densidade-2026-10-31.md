# Análise de UX: densidade de informação na tela Aparência

- **Produto:** Terminal Launcher (`net.mdantas.terminal`)
- **Tela:** Configurações > Aparência, no estado do commit `cb9f6af43a`
- **Data:** 2026-10-31
- **Modo:** análise unificada, rápida (5 personas), focada em carga de informação e divulgação progressiva
- **Fonte:** código lido e capturas de tela do emulador (`emulator-5554`) com o commit instalado. Sem teste com usuários, sem TalkBack, sem telemetria.
- **Relação com o relatório anterior:** este relatório parte da tela já alterada. A nota não é comparável 1:1 com a de 67, porque o conjunto de achados mudou e aquele relatório tinha dois achados errados (A002 e A004 daquele relatório, que o código já tratava).

---

## 1. Sumário executivo

### UX Score: 78/100

`0,45 × Usabilidade + 0,25 × Acessibilidade + 0,30 × Comportamento/Confiança`

| Componente | Conta | Nota |
|---|---|---|
| Usabilidade | 100 − 1 high×8 − 4 medium×4 − 2 low×1 = 100 − 8 − 16 − 2 | **74** |
| Acessibilidade | 100 − metade de (3 medium×4 + 1 low×1) = 100 − 6,5 (só riscos potenciais) | **93,5 ≈ 94** |
| Comportamento/Confiança | média(7, 7, 7) × 10 = 70, sem barreira critical | **70** |

0,45×74 + 0,25×93,5 + 0,30×70 = 33,3 + 23,4 + 21,0 = **77,7 ≈ 78**

A sensação de "carregada" é real e tem causas mensuráveis no que está na tela. A maior não é o número de opções da tela, e sim **uma lista que cresce sem limite dentro dela** (os temas salvos) e **controles repetidos em lugares diferentes**. Atacar isso reduz mais a carga do que esconder mais configurações.

### Ressalva sobre o que foi medido

As capturas vêm de um aparelho de desenvolvimento com **cerca de 18 temas salvos**. Quem acabou de instalar o app tem zero, e a tela dessa pessoa é bem mais curta. A estimativa de comprimento abaixo vale para o perfil pesado e precisa ser confirmada num perfil limpo antes de decidir o tamanho da mudança.

### Inventário observado (estado atual)

| Região | O que aparece | Observação |
|---|---|---|
| Temas | modo de cor (3 segmentos), fileira de cerca de 40 chips (27 looks prontos + 12 matizes + a roda, contados no código), **uma linha por tema salvo**, Salvar/Importar/Compartilhar atual | cada linha salva tem 2 botões e um resumo de 2 a 3 linhas, perto de 200 px de altura na captura |
| Cores | destaque, fundo, secundária, **chave "Acompanhar o modo do sistema"** | a chave repete o segmento "Sistema" do modo de cor |
| Papel de parede | fundo da tela inicial (1 linha), escurecer, escolher | até 3 linhas quando o papel de parede do sistema está ativo |
| Formatos | formato do ícone, formato da pasta | grupo com cabeçalho próprio, prévia de 48 dp em cada linha |
| Fonte | fonte da interface, relógio pixelado | outro grupo com cabeçalho próprio; a descrição do relógio ocupa 3 linhas |
| Avançado (recolhido) | 1 linha | ao abrir: 3 cores, 3 efeitos, 2 controles de intensidade, tipo e prévia da animação |
| Guia de temas | 1 linha, sozinha no fim | fora de qualquer grupo |
| Rodapé fixo | prévia de 88 dp + nota de 2 linhas | cerca de 16% da altura da tela, sempre visível |

**Estimativa** (inferida das capturas): no perfil pesado, o título "Cores" só aparece depois de cerca de 2 telas de rolagem, e o "Fundo da tela inicial" depois de cerca de 2,5.

---

## 2. Contexto do produto

```json
{
  "product_summary": "Launcher Android com estética de terminal, barra de comando e temas (.tthm) feitos à mão ou por IA. Aparência é a tela de personalização visual.",
  "product_category": "App mobile de personalização",
  "market_context": "Concorrentes costumam separar a personalização em subtelas curtas (tema, ícones, fundo). A expectativa é trocar de visual em poucos toques.",
  "primary_users": ["entusiastas de personalização", "usuários que só querem tema escuro legível", "usuários de temas por IA"],
  "business_goals": ["retenção via personalização", "mostrar o diferencial (temas, efeitos de terminal)"],
  "user_goals": ["trocar o visual rápido", "achar o que quero sem rolar muito", "não perder o que já tenho"],
  "constraints": ["a barra de comando e a theme spec precisam acompanhar toda nova customização (CLAUDE.md)", "a tela existe no Lawnchair base e no ciclo de releases do launcher"],
  "risks": [
    {"risk": "Quem não é entusiasta abandona a tela por parecer um painel técnico", "likelihood": "medium", "impact": "medium"},
    {"risk": "Esconder demais faz os recursos de terminal (efeitos, temas por IA) não serem descobertos", "likelihood": "medium", "impact": "high"}
  ],
  "assumptions": [
    {"assumption": "A maioria das visitas à tela busca um atalho (modo, look pronto) e não ajuste fino", "validation_status": "unvalidated"},
    {"assumption": "O perfil limpo (0 temas salvos) é o mais comum entre os usuários novos", "validation_status": "unvalidated"}
  ],
  "key_success_metrics": ["tempo até o primeiro look aplicado", "profundidade de rolagem por visita", "uso de Avançado e do Guia de temas", "taxa de Desfazer"],
  "missing_context": ["telemetria de uso por região da tela", "distribuição de temas salvos por usuário", "teste num perfil limpo", "comportamento com TalkBack"]
}
```

---

## 3. Personas

| ID | Nome | Idade | Papel | Nível técnico | Acessibilidade |
|---|---|---|---|---|---|
| P1 | Rafaela | 34 | Designer de produto, São Paulo | avançado | não |
| P2 | Antônio | 67 | Aposentado, Recife | iniciante | baixa visão, fonte em 200% |
| P3 | Lucas | 19 | Estudante de computação, Curitiba | expert, tem cerca de 18 temas salvos | não |
| P4 | Camila | 41 | Enfermeira de plantão, Belo Horizonte | intermediário, usa com uma mão | tremor leve |
| P5 | Diego | 28 | Desenvolvedor cego, Porto Alegre | avançado, TalkBack | leitor de tela |

---

## 4. Simulação (resumo, tudo `simulado`)

| Persona | Primeira impressão | Onde a densidade pesa | Risco de abandono | Conclusão (qualitativa) |
|---|---|---|---|---|
| P1 | "Tem tudo aqui." | Gosta do poder, mas rola muito para chegar nas cores | baixo | alta |
| P2 | "Que lista grande." | A fileira de chips e as linhas longas de tema salvo escondem o que ele quer (letra maior, fundo escuro); descrições de 3 e 4 linhas | **alto** | baixa |
| P3 | "Casa." | Com cerca de 18 temas salvos, a lista vira a tela inteira; apagar e compartilhar ficam a toda hora no caminho | médio | alta |
| P4 | "Tenho pouco tempo." | Rolar com o polegar até o fundo da tela inicial; uma mão só | médio | média |
| P5 | "Muita coisa." | Percorre linha por linha: 18 temas × (nome, resumo, compartilhar, apagar) antes de chegar em Cores | **alto** | baixa |

Monólogos:
- **P2:** "Eu só queria o escuro e a letra maior. Mas aqui tem dock, pasta, efeito... melhor deixar como está."
- **P3:** "Quero trocar o tema, mas passo por dezoito linhas até a próxima seção."
- **P5:** "Garoa, Volta, Teste, Parceiro... quantos mais? Quando chego em Cores?"

---

## 5. Problemas heurísticos

| ID | Achado | Categoria | Sev. | Evidência | Personas | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|
| H001 | A lista de temas salvos é ilimitada e cada tema ocupa uma linha de cerca de 200 px com 2 botões e um resumo de 2 a 3 linhas. Com muitos temas, tudo abaixo (Cores, Fundo, Formatos, Fonte) fica a 2 ou mais telas de distância. | Estética e design minimalista (carga cognitiva, Hick) | high | `observado` (capturas, perfil de 18 temas) e `inferido` (comprimento) | P2, P3, P5 | Trocar as linhas por uma fileira horizontal de chips (o mesmo componente da fileira de looks), com o tema em uso marcado, e mandar compartilhar/apagar/renomear para uma tela "Meus temas" aberta por um botão "Gerenciar". | médio |
| H002 | O modo de cor aparece duas vezes: o segmento "Sistema" no topo e a chave "Acompanhar o modo do sistema" em Cores. Os dois gravam a mesma preferência, mas um tem o seletor claro/escuro e o outro tem o ícone com as duas versões. | Consistência e padrões | medium | `observado` (código e captura) | todas | Deixar um só controle: o segmento de modo. A chave sai de Cores e vai para Avançado, ou a miniatura claro/escuro passa a ilustrar o segmento "Sistema". | baixo |
| H003 | Descrições longas sob as chaves: relógio pixelado (3 linhas), "Acompanhar o modo" (3 a 4), efeito do ícone (3), efeito do widget (4). Em fonte maior, mais ainda. | Estética e design minimalista | medium | `observado` (captura) | P2, P4, P5 | Limitar a uma linha curta (até cerca de 60 caracteres) e levar a explicação para um ícone de ajuda que abre uma folha. | baixo |
| H004 | Seis grupos com cabeçalho mais um "Avançado" e o "Guia de temas" soltos. Formatos (2 linhas) e Fonte (2 linhas) são dois grupos pequenos. O Guia de temas, que serve a quem importa temas, fica separado do Importar. | Arquitetura da informação | medium | `observado` | P1, P2, P3 | Juntar Formatos e Fonte em um grupo "Estilo" de 4 linhas, sem subcabeçalho. Colocar "Criar com IA" ao lado de Importar, no grupo de temas. | baixo |
| H005 | O rodapé fixo ocupa cerca de 16% da altura (prévia de 88 dp mais nota de 2 linhas) o tempo todo, inclusive quando o usuário mexe em algo que a prévia não mostra (fonte, formato, efeitos). | Estética e design minimalista | medium | `observado` | P4, P2 | Reduzir a prévia (cerca de 64 dp), encurtar a nota para uma linha ("Prévia aproximada") e esconder o rodapé enquanto o teclado ou uma folha estiverem abertos. | baixo |
| H006 | O Desfazer cobre aplicar look, tema salvo e modo de cor, mas não o ajuste de uma cor individual nem as chaves de papel de parede. A pessoa não sabe o que se desfaz. | Controle e liberdade do usuário | low | `observado` (código) | P4 | Dizer no aviso o que foi alterado ("Modo escuro aplicado"), e decidir se as cores individuais entram. | médio |
| H007 | A fileira de looks tem cerca de 40 chips, sem agrupamento nem ordem aprendível além do degradê no fim. | Lei de Hick | low | `observado` (código, captura) | P2, P4 | Mostrar os usados por último e os favoritos, com "Ver todos" abrindo uma folha em grade com seções (trios, clássicos, pastéis, matizes). | médio |
| H008 | **Positivo.** O que já foi esconder o que raramente se usa (Avançado), atalhos de modo no topo, prévia fixa que acompanha a cor, Desfazer visível e o tema em uso marcado. A divulgação progressiva do papel de parede (escurecer e escolher só quando o papel de parede do sistema está ativo) funciona e deve ser o padrão. | Várias | positive | `observado` | todas | Manter e estender o padrão. | n/a |

```json
{"heuristics_passed": ["3 Controle e liberdade (parcial)", "5 Prevenção de erros", "7 Flexibilidade e eficiência de uso"], "heuristics_failed": ["4 Consistência e padrões", "8 Estética e design minimalista", "6 Reconhecimento em vez de memorização (parcial)"], "usability_score": 74, "score_rationale": "1 high (−8), 4 medium (−16), 2 low (−2)."}
```

---

## 6. Acessibilidade (WCAG 2.2)

Itens introduzidos pelas mudanças do último commit. Nenhum é falha confirmada: faltou teste com TalkBack.

| ID | Achado | WCAG | Nível | Status | Sev. | Recomendação | Esforço |
|---|---|---|---|---|---|---|---|
| A001 | O aviso "Look alterado · Desfazer" desaparece sozinho em 10 segundos, sem como estender. Quem demora (baixa visão, tremor, leitor de tela) perde a chance. | 2.2.1 Timing Adjustable | A | risco potencial | medium | Manter o aviso até a próxima alteração ou até ser dispensado; se houver prazo, deixar o usuário estender. | baixo |
| A002 | O aviso aparece sem anúncio programático: não há `liveRegion` nem foco. Quem usa TalkBack pode não saber que o look mudou nem que há Desfazer. | 4.1.3 Status Messages | AA | risco potencial | medium | `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` no aviso, e o texto com o que mudou. | baixo |
| A003 | O cabeçalho "Avançado" abre e fecha, mas só a seta muda; falta estado expandido/recolhido para o leitor de tela. | 4.1.2 Name, Role, Value | A | risco potencial | medium | `semantics { stateDescription = ... }` ou `Modifier.semantics { role = Role.Button; ... }` com "expandido/recolhido". | baixo |
| A004 | O botão de compartilhar de cada tema e o de apagar só têm descrição genérica ("Compartilhar tema", "Apagar"), sem o nome do tema; em 18 linhas o leitor repete a mesma frase. | 2.4.6 Headings and Labels; 4.1.2 | AA | risco potencial | low | Incluir o nome: "Compartilhar tema Garoa Paulista". | baixo |

```json
{"wcag_a": "partial", "wcag_aa": "partial", "total_issues": 4, "critical_issues": 0, "high_issues": 0, "accessibility_score": 94, "score_rationale": "4 riscos potenciais (A001 a A003 medium, A004 low) contam metade: (3×4 + 1×1)/2 = 6,5, logo 93,5, arredondado para 94 no quadro."}
```

---

## 7. Psicologia comportamental

| ID | Barreira | Framework | Gatilho | Sev. | Corrigível por design | Recomendação |
|---|---|---|---|---|---|---|
| B001 | Quem chega querendo uma coisa simples (escuro, letra maior) precisa ignorar dezenas de elementos até achar o que serve. | Teoria da carga cognitiva (carga extrínseca) | abrir a tela | high | sim | Subir os atalhos e deixar para depois o que é raro. Liga com H001. |
| B002 | Linhas longas e parecidas (tema salvo, descrições) cansam a leitura e a pessoa cai no default mais fácil: sair da tela. | Fadiga de decisão | rolar a lista de temas | medium | sim | Chips no lugar de linhas; descrições curtas. |
| B003 | O recurso diferencial (efeitos de terminal) fica atrás de "Avançado" e pode não ser descoberto. | Modelo de Fogg (gatilho fraco) | tela de efeitos | medium | em parte | Uma linha de "Efeitos" visível no corpo, com uma chave geral, e o detalhe dentro de Avançado. |

```json
{"initial_trust": 7, "process_trust": 7, "outcome_trust": 7,
 "trust_gaps": ["não está claro o que o Desfazer cobre", "a tela é longa para o perfil pesado"],
 "trust_builders_present": ["prévia fixa", "Desfazer", "marca de tema em uso", "confirmação na tela de bloqueio"],
 "trust_builders_missing": ["o que mudou, dito no aviso", "resumo do estado atual no topo"]}
```

| Ação-chave | Motivação | Habilidade | Gatilho | Resultado | Recomendação |
|---|---|---|---|---|---|
| Deixar o launcher escuro | alta | alta | forte (modo no topo) | provável conclusão | manter |
| Trocar para um tema salvo | média | média (lista longa) | fraco | em risco | fileira de chips |
| Ajustar fundo e papel de parede | média | média (longe do topo) | fraco | em risco | subir ou ancorar no resumo |
| Descobrir efeitos | média | média | fraco | em risco | linha visível com chave geral |

---

## 8. Insights

| ID | Insight | Confiança | Sustentado por | Personas | Impacto no negócio | Impacto no usuário |
|---|---|---|---|---|---|---|
| I001 | O maior ganho de densidade está em tirar a lista de temas do fluxo da página, não em esconder mais configurações. | alta | H001, B002 | P2, P3, P5 | mais gente chega às cores e ao fundo | menos rolagem |
| I002 | O mesmo controle em dois lugares (modo e chave do sistema) soma ruído sem acrescentar função. | alta | H002 | todas | menos dúvida e menos suporte | menos linhas |
| I003 | Texto de apoio longo pesa tanto quanto controle extra. | média | H003, B002 | P2, P4 | tela percebida como técnica | leitura menor |
| I004 | Os pontos de acessibilidade novos vieram de componentes recém-criados, que ainda não foram testados com leitor de tela. | média | A001 a A004 | P5, P2 | risco de avaliação | P5 sem saber o que mudou |

**Conflito registrado:** a Fase 4 pede esconder mais (Avançado, chips), e a Fase 6 (B003) avisa que esconder demais enterra os efeitos de terminal, que são o diferencial. As duas valem; a saída é uma linha de resumo visível com o detalhe dentro, e o teste decide.

---

## 9. Oportunidades

| ID | Oportunidade | Resolve | Impacto | Esforço | Prioridade |
|---|---|---|---|---|---|
| O1 | Temas salvos em fileira de chips; compartilhar/apagar/renomear numa tela "Meus temas" | H001, B001, B002, I001 | alto | médio | P1 |
| O2 | Remover a chave "Acompanhar o modo do sistema" de Cores (ela fica só no segmento de modo, ou em Avançado) | H002, I002 | alto | baixo | P0 |
| O3 | Descrições de uma linha, com ícone de ajuda para o detalhe | H003, I003 | médio | baixo | P1 |
| O4 | Juntar Formatos e Fonte em "Estilo"; levar "Criar com IA" para junto de Importar | H004 | médio | baixo | P1 |
| O5 | Rodapé menor (cerca de 64 dp), nota de uma linha, escondido com teclado | H005 | médio | baixo | P1 |
| O6 | Aviso de Desfazer persistente, com liveRegion, dizendo o que mudou; estado expandido em Avançado; nomes nos botões de cada tema | A001 a A004, H006 | médio | baixo | P1 |
| O7 | Linha "Efeitos" visível com chave geral; detalhe em Avançado | B003 | médio | baixo | P2 |
| O8 | Fileira de looks com recentes e favoritos, mais "Ver todos" em grade com seções | H007 | baixo | médio | P2 |
| O9 | Dividir a tela em subtelas (hub com 4 linhas: Look e temas, Cores, Fundo, Estilo e efeitos), só se O1 a O5 não bastarem | H001, H004 | alto | alto | P3 |

### Estrutura proposta (opções O1 a O5 aplicadas)

| Grupo | Conteúdo | Linhas fixas (sem expandir) |
|---|---|---|
| **Look** | modo (3 segmentos); fileira de looks; fileira "Meus temas"; ações: Salvar, Importar, Criar com IA | 3 linhas de conteúdo e 1 de ações |
| **Cores** | destaque, fundo, secundária | 3 |
| **Fundo** | fundo da tela inicial (1 linha; escurecer e escolher só quando o papel de parede do sistema estiver ativo) | 1 (a 3) |
| **Estilo** | formato do ícone, formato da pasta, fonte, relógio pixelado | 4 |
| **Avançado** (recolhido) | cores de busca/dock/pastas, chave de acompanhar o sistema, efeitos | 1 |

Contagem das linhas fixas (conta de projeto, não medida): hoje 20 linhas e botões visíveis com 0 temas salvos, e até cerca de 74 com 18 temas salvos (2 botões por tema). Na proposta: 15, independente de quantos temas haja.

---

## 10. Experimentos

| Oportunidade | Hipótese | Método | Participantes | Métrica | Baseline necessário | Meta (a calibrar) |
|---|---|---|---|---|---|---|
| O1 | Se os temas salvos viram uma fileira de chips, quem tem muitos chega mais rápido a Cores e Fundo, porque cai a distância de rolagem. | Teste moderado, versão atual e nova, com perfil pesado (cerca de 18 temas) | 5 a 6 por versão, incluindo P2 e P3 | tempo e distância de rolagem até "Fundo" | medir a versão atual no perfil pesado | a calibrar |
| O1/O2 | Se a tela tem um controle por função, menos gente erra ao ligar o modo do sistema. | Teste de primeiro clique ("deixe o launcher seguir o modo do celular") | 8 a 10 | acerto no primeiro clique | versão atual | a calibrar |
| O3 | Se as descrições ficam em uma linha, a leitura não piora o entendimento. | Teste não moderado com 3 perguntas de compreensão | 10 | acerto nas perguntas | versão atual | sem queda frente ao baseline |
| O5 | Se o rodapé fica menor, a pessoa ainda entende que ele é uma prévia. | Teste de cinco segundos | 8 | resposta a "o que é isto?" | versão atual | sem queda frente ao baseline |
| Telemetria | Qual região da tela é usada de fato? | Análise de eventos (rolagem e toques por grupo), com consentimento | todos os usuários | profundidade de rolagem e taxa por grupo | nenhum | definir depois do baseline |

As correções `observado` de esforço baixo (O2, O3 na parte de texto, O6) podem ir direto para implementação sem experimento.

---

## 11. Lacunas e premissas

- **Perfil medido:** só o aparelho de desenvolvimento, com cerca de 18 temas salvos. O comprimento da tela num perfil limpo não foi medido.
- **Telemetria:** não existe. Sem ela não dá para saber quais grupos são usados, e as prioridades acima assumem que os atalhos (modo, looks) dominam.
- **Achados só `simulado`:** as reações de P2, P3, P4 e P5. Não valem como percentuais da população.
- **Contagens:** os cerca de 40 chips e as linhas por perfil vêm do código e das capturas, não de medição automatizada. O número de temas salvos (cerca de 18) foi contado nas capturas.
- **Acessibilidade:** A001 a A004 são riscos potenciais, sem teste com TalkBack. A roda de cores continua sem entrada por valor (2.5.7), por ser uma tela que não analisei aqui.
- **Premissas a validar:**
  1. A maioria das visitas à tela busca um atalho, não ajuste fino.
  2. O perfil limpo é o mais comum.
  3. Esconder os efeitos em Avançado não derruba o uso deles (B003).
  4. Uma tela de "Meus temas" separada não cria um passo a mais que irrite quem usa muito.
