# Customizações do Terminal (fork do Lawnchair 16)

Este documento lista o que foi alterado em relação ao projeto original, o Lawnchair 16-dev, mantido em `upstream` (`github.com/LawnchairLauncher/lawnchair`).

## Base e escopo

- Ponto de bifurcação: `43a21b43d7` (2026-10-02, "fix: keyboard closing when clearing app search (#7353)").
- O branch `16-dev` tem 22 commits próprios sobre essa base, todos de Mauricio, com 168 arquivos alterados (+8518 / −1706 linhas) no diff de `43a21b43d7..HEAD`.
- Há ainda alterações não commitadas (ver a seção final).
- Os números acima vêm do histórico do git. Como o `upstream` não tem commits novos em relação à base, qualquer diferença do Lawnchair oficial está listada aqui.

## 1. Identidade e build

- **Nome do app:** "Terminal" (release) e "Terminal (Debug)" (debug), via `derived_app_name` em `build.gradle`.
- **Application IDs:** `net.mdantas.terminal` (canal `play`, padrão), `net.mdantas.terminal.github` e `net.mdantas.terminal.nightly`. No original eram `app.lawnchair`, `app.lawnchair.play` e `app.lawnchair.nightly`. O `launcher_component` gerado aponta para o mesmo `app.lawnchair.LawnchairLauncher`, então o código Kotlin mantém o pacote original.
- **Versão:** `versionCode` e `versionName` podem ser sobrescritos por `RELEASE_VERSION_CODE` e `RELEASE_VERSION_NAME`.
- **Assinatura:** quando `RELEASE_STORE_FILE` existe, o `signingConfig` de release é criado a partir de propriedades injetadas por `ORG_GRADLE_PROJECT_*`. Nenhuma senha é escrita em disco.
- **Publicação:** plugin `com.github.triplet.play` 4.0.0, configurado com track `internal` por padrão (`PLAY_TRACK`) e App Bundles.
- **`release.sh`:** script que busca o keystore e a conta de serviço no 1Password CLI, gera a versão a partir da data e publica pelo Gradle Play Publisher. Tem modos `--build-only` e `--apk`. O texto do script está em português e cita "Terminal Launcher" e o projeto "overlook-watchface". Parece copiado de outro projeto, então vale revisar antes de usar.
- **Testes JVM:** o source set `test` aponta para `tests/unit/src`, com dependência `libs.junit`.
- **Submódulo:** `platform_frameworks_libs_systemui` aponta para o branch `16-dev` do fork do próprio autor.
- **Baseline profile:** dependência removida do módulo `baseline-profile/build.gradle`.
- **Workflow de release:** `.github/workflows/release_update.yml` foi alterado (ver diff do arquivo).

## 2. Identidade visual (tema "terminal de fósforo")

Estética de terminal retrô, inspirada no MU/TH/UR 6000. A primeira versão está no commit "Estado inicial customizações" (`6b6b7727a8`).

- **Cor do fósforo:** configurável, com cor padrão de fósforo em `ui/theme/Color.kt` e tokens em `theme/color/tokens/`.
- **Tipografia e formas:** `ui/theme/Type.kt`, `Shape.kt` e `Theme.kt` foram reescritos.
- **Overlay CRT:** `views/CrtOverlayView.kt` (novo), aplicado sobre a tela inicial.
- **Ícone:** os PNGs `ic_launcher_home*` de todas as densidades foram removidos e substituídos por drawables vetoriais (`ic_terminal_background`, `ic_terminal_foreground`, `ic_terminal_monochrome`). Os `mipmap-anydpi-v26` foram atualizados.
- **Tema de ícones padronizado:** `icons/TerminalIconThemeController.kt` (novo).
- **Textos em caixa alta e termos temáticos** em `lawnchair/res/values/strings.xml` (ex.: "FILTRAR PROGRAMAS", "COMANDOS", "CONTATOS", "ARQUIVOS", "AJUSTES", "CÁLCULO", "RASTREADOR DE MOVIMENTO", "DIGITE UM COMANDO"). Essas strings são `translatable="false"`.
- **Aviso de fork:** `ui/preferences/about/ForkNotice.kt` (novo), seção "Sobre este projeto" em Sobre. Diz que o Terminal é um fork do Lawnchair e que o mantenedor não reivindica direitos sobre a propriedade intelectual do projeto original.
- **Configurações:** o título e o resumo da tela de configurações foram reescritos (ex.: "Cor do fósforo", "Grade, bloqueio de layout, rótulos").

## 3. At a Glance (widget e painel da tela inicial)

O radar decorativo original virou um painel de alvos. Quase tudo está em `lawnchair/src/app/lawnchair/smartspace/`.

- **Painel de alvos com abas:** agenda, clima, mídia, alarme e aviso. `RadarView.kt` (novo) e `SmartspaceViewContainer.kt` foram alterados.
- **Prioridade:** eventos que começam em até 30 minutos sobem para a frente e ficam em vermelho. A antecedência do destaque é configurável (15, 30 ou 60 min).
- **Valor grande:** a hora ou a temperatura do alvo aparece em fonte pixelada, à esquerda. `glance/GlanceEngine.kt` (novo) extrai esse valor.
- **Regras puras:** `glance/GlanceEngine.kt`, `glance/GlanceSetup.kt` e `glance/StatusLine.kt` concentram a lógica sem dependência do Android, para facilitar os testes.
- **Linha de status dinâmica:** sob a data, mostra perfil de som, armazenamento e memória, atualizada a cada minuto. Não lê Wi-Fi, para não exigir `ACCESS_NETWORK_STATE`. Pode ser desligada para voltar ao texto fixo.
- **Linha de atalhos:** lanterna, calc, câmera e relógio no lugar das abas. Tocar no título passa para o próximo alvo.
- **Alvos novos:**
  - Bateria Bluetooth: `glance/BluetoothBattery.kt` e `provider/BluetoothBatteryProvider.kt` (novos). Exige `BLUETOOTH_CONNECT` e tem um cartão de permissão nas configurações.
  - Lembretes: `FEATURE_REMINDER` virou tipo próprio.
  - Atalhos de conversa do WhatsApp: `glance/ChatShortcuts.kt` e `glance/WhatsAppChatSource.kt`, com `chats/ChatsStripView.kt`.
- **Efeito de digitação:** `smartspace/TypewriterEffect.kt` (novo).
- **Radar substituído pelo painel** em `smartspace_widget.xml`, com espaçamento maior.
- **Provedor de Now Playing** ajustado em `NowPlayingProvider.kt`.
- **Configurações:** `ui/preferences/destinations/SmartspacePreferences.kt` foi reescrito. Mostra alvos por tipo, número máximo, prioridade automática e cartões que dizem o que falta para configurar e abrem a tela certa do sistema, sem diálogo automático.
- **Correção de tradução:** a frase "ative os pontos de notificação" estava invertida em pt-BR.

## 4. Barra de comando (terminal de comandos na tela inicial)

Tocar na barra da tela inicial abre o terminal de comandos. É a maior funcionalidade nova, e está em `lawnchair/src/app/lawnchair/command/`.

- **Comandos embutidos:** abrir, alarme, calc (`c`), ligar, rota, tarefa (`t`) e mensagem (`w`).
- **Motor:** `CommandEngine.kt` faz o parsing e as regras; `CommandExecutor.kt` executa as ações; `CalcEvaluator.kt` avalia expressões.
- **Interface:** `CommandActivity.kt` com texto fantasma, sugestões, pré-visualização, histórico e teclas [▲] [TAB] [▼].
- **Autocompletar** e execução de sugestões: tocar numa sugestão já executável a executa sem Enter. Comando falado que já é executável roda sozinho.
- **Voz:** `VoiceCommand.kt` traduz a fala (chamar, vezes, para...). O botão [MIC] da barra abre a barra já ouvindo.
- **`w` (mensagem):** o nome do contato é a maior sequência de palavras que casa com um contato. O resto vira o texto, que abre o WhatsApp (ou Business) já preenchido, ou o SMS se não houver WhatsApp.
- **`ligar`:** pede `CALL_PHONE` e liga direto. Sem a permissão, abre o discador.
- **Ações personalizadas:** `CustomAction.kt` e `CustomActionStore.kt`. Tela "Ações da barra de comando" em `destinations/CommandActionsPreferences.kt` com receitas prontas (WhatsApp, Telegram, SMS, YouTube, Spotify), atalhos do launcher e intents detectadas por sondagem. Modo avançado aceita URI com `{number}`, `{phone}`, `{name}` e `{text}`, pacote e extra de texto. Exporta e importa em JSON; a importação não troca letras já em uso.
- **Atalho de calculadora no Samsung:** o Samsung não declara a categoria de calculadora, então o app tenta pacotes conhecidos e qualquer app com nome de calculadora, avisando se nada for encontrado.
- **Configuração:** em Pesquisa > Dock (Barra de comando).
- **Permissões novas:** `com.android.alarm.permission.SET_ALARM`, `CALL_PHONE` (usada em tempo de execução) e `BLUETOOTH_CONNECT`.
- **Manifesto:** `CommandActivity` com `excludeFromRecents` e `adjustResize`.

## 5. Pastas grandes na tela inicial

- **Tamanhos:** pastas podem ocupar 1×1, 2×2 ou 4×2 células. Os tamanhos têm nomes (Pequena, Média, Grande). O seletor fica no rodapé da pasta aberta e usa o `AlertDialog` do framework, porque o do AppCompat derrubava o launcher.
- **Regras puras:** `folder/LargeFolderMath.kt` (novo) com ciclo de tamanho, grade, contador "+N", posição ao crescer, `PRESETS`, `presetIndex` e `opensOnTap`.
- **Visual:** `folder/LargeFolderView.java` (novo) mostra os apps direto na tela, encolhendo os ícones antes de recorrer ao "+N". Tocar num app o abre; tocar no painel abre a pasta; segurar arrasta a pasta inteira.
- **Toque:** tocar numa pasta grande com todos os apps visíveis não a abre. Só abre se houver "+N" ou pelo título, para renomear.
- **Layout:** a pasta grande ocupa as células inteiras, sem o padding de centralização de 1×1.
- **Tamanho salvo** nas colunas de span já usadas pelos widgets e lido ao carregar a tela inicial.
- **Drop:** `Workspace.java` aceita soltar um app em qualquer ponto de uma pasta grande.
- **Correção de crash:** "Cannot set 'scaleX' to Float.NaN" ao abrir pasta grande. A prévia não era calculada porque não era desenhada, e a animação dividia por zero.
- **Ícones de pasta:** `FolderIcon.java` foi alterado (+140 linhas).

## 6. Gaveta de apps e busca

- **Estilo diretório:** fundo preto por padrão (cor personalizada continua valendo). Linhas numeradas tipo "A-01" com líder pontilhado e destaque na linha tocada. Implementado em `allapps/DirectoryIndexView.java` (novo) e `ActivityAllAppsContainerView.java`.
- **Índice A–Z lateral:** `DirectoryIndexView` pula para a seção ao tocar ou arrastar.
- **Previsão de apps desligada** por padrão, o que remove o app de destaque do topo.
- **Busca:** resultados usam as mesmas linhas numeradas, em uma coluna. O primeiro resultado fica em destaque fósforo e sem o fundo cinza do tema. Menos espaço entre a barra de busca e as correspondências.
- **Seções de busca** com nomes temáticos, em `search/adapter/SearchTargetFactory.kt`, `search/algorithms/LawnchairSearchAlgorithm.kt`, `LawnchairAppSearchAlgorithm.kt` e `engine/SectionBuilder.kt`. Inclui a seção "CÁLCULO" para resultados da calculadora.
- **Textos da busca:** `allapps/views/SearchResultText.kt` e `qsb/LawnQsbUi.kt`.

## 7. Onboarding de primeira execução

- **Tela nova:** `ui/onboarding/OnboardingActivity.kt` (novo). Etapas: boot, launcher padrão, cor do fósforo, notificações e resumo.
- **Abertura:** só na primeira execução, e o launcher só a dispara numa partida real, não ao recriar a tela.
- **Tarefa própria:** `singleTask`, `excludeFromRecents` e `taskAffinity` próprio. Antes, abrir dentro da tarefa do launcher mantinha uma segunda instância viva ao voltar das configurações do sistema.
- **Retomada:** reabre até 3 vezes se for interrompido. Pode ser refeito pelo menu de Configurações.
- **Card de launcher padrão:** o aviso no topo de Configurações virou um card com botão "Definir agora".

## 8. Remoções em relação ao original

- **Serviço de acessibilidade:** `LawnchairAccessibilityService.kt`, `res/xml/accessibility_service_config.xml` e o serviço no manifesto. Os gestos que dependiam dele foram removidos, incluindo `gestures/handlers/RecentsGestureHandler.kt`.
- **Upload de relatórios de bug:** `bugreport/UploaderService.kt` e o serviço `:bugReport` no manifesto.
- **Permissões removidas:** `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` e `FOREGROUND_SERVICE_DATA_SYNC`.
- **Tela de configurações reorganizada:** `PreferencesDashboard.kt`, `GeneralPreferences.kt`, `HomeScreenPreferences.kt` e `AppDrawerPreferences.kt` tiveram remoções grandes. Quem for mesclar com upstream deve revisar esses arquivos com cuidado.

## 9. Layout padrão da tela inicial

- `lawnchair/res/xml/default_workspace_4x5.xml` (183 linhas alteradas) define a disposição inicial dos ícones. É o arquivo que define o que o usuário vê na primeira execução.
- `src/com/android/launcher3/DeviceProfile.java` e `Workspace.java` foram ajustados para as pastas grandes e para o layout.

## 10. Alterações no núcleo do Launcher3

Além das áreas acima, o código AOSP de `src/` foi alterado. Estes são os pontos de maior risco em futuras sincronizações com o upstream:

- `src/com/android/launcher3/BubbleTextView.java` (+65): ajustes no rótulo dos ícones.
- `src/com/android/launcher3/folder/FolderIcon.java` (+140): pastas grandes.
- `src/com/android/launcher3/Workspace.java`: drop em pastas grandes.
- `src/com/android/launcher3/allapps/BaseAllAppsAdapter.java`, `search/AppsSearchContainerLayout.java`: gaveta e busca.
- `src/com/android/launcher3/deviceprofile/AllAppsProfile.kt`, `graphics/ThemeManager.kt`, `model/GridSizeMigrationDBController.java`, `model/GridSizeMigrationLogic.kt`, `model/LoaderCursor.java`: ajustes pontuais.

## 11. Testes adicionados

Testes puros para as regras de negócio, sem dispositivo:

- `tests/unit/src/app/lawnchair/command/`: `CommandEngineTest`, `CalcEvaluatorTest`, `VoiceCommandTest`.
- `lawnchair/src/.../smartspace/glance/`: `GlanceEngineTest`, `GlanceSetupTest`, `StatusLineTest`, `BluetoothBatteryTest`, `ChatShortcutsTest`.
- `tests/unit/src/com/android/launcher3/folder/LargeFolderMathTest.kt`: regras das pastas grandes.

## 12. Pendências no working tree (não commitado)

Estes arquivos têm alterações ainda não commitadas:

- `.github/workflows/release_update.yml`, `build.gradle`, `baseline-profile/build.gradle`
- `lawnchair/res/values/strings.xml`, `lawnchair/res/xml/default_workspace_4x5.xml`
- `lawnchair/src/app/lawnchair/LawnchairLauncher.kt`, `chats/ChatsStripView.kt`, `icons/LawnchairThemeManager.kt`, `preferences2/PreferenceManager2.kt`, `views/CrtOverlayView.kt`
- `lawnchair/.../destinations/AppDrawerPreferences.kt`, `HomeScreenPreferences.kt`
- `src/com/android/launcher3/DeviceProfile.java`, `Workspace.java`

Há também um arquivo não rastreado: `store-assets/query-all-packages-demo.mp4`.

## Pontos de conflito prováveis em merges com o upstream

Arquivos alterados tanto pelo fork quanto pelo upstream, onde conflitos são mais prováveis:

- `src/com/android/launcher3/Workspace.java`, `DeviceProfile.java`, `folder/FolderIcon.java`, `BubbleTextView.java`
- `lawnchair/src/app/lawnchair/LawnchairLauncher.kt`, `preferences2/PreferenceManager2.kt`
- `lawnchair/res/values/strings.xml`, `lawnchair/res/xml/default_workspace_4x5.xml`
- `lawnchair/AndroidManifest.xml`, `build.gradle`
