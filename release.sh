#!/bin/bash
set -e

# ── Release do Terminal Launcher (Play Store) ────────────────────────────────
# Mesma estrutura do release.sh do overlook-watchface: credenciais via
# 1Password CLI, versão gerada a partir da data e publicação pelo Gradle Play
# Publisher.
#
# Uso:
#   ./release.sh              compila o bundle assinado e publica na Play Store
#   ./release.sh --tracks internal,closed-testing
#                             publica em várias faixas com UM só build
#   ./release.sh --build-only compila e assina o bundle (.aab), sem publicar
#   ./release.sh --apk        compila e assina um APK para instalar/testar
#   ./release.sh --no-wear    não gera nem publica o app do relógio (Wear OS)
#   ./release.sh --promote-only
#                             sem compilar, promove o build atual da faixa internal
#                             para o teste fechado; com --tracks, da primeira faixa
#                             para as demais (ex.: --tracks closed-testing,production)
#
# Variáveis opcionais:
#   OP_VAULT     cofre do 1Password (padrão: Android)
#   PLAY_TRACK   faixa(s) da Play Store, separadas por vírgula (padrão: internal).
#                O build é publicado na primeira e promovido às demais, então
#                todas recebem o mesmo versionCode. Apelidos aceitos:
#                closed-testing/closed -> alpha, open-testing/open -> beta,
#                prod -> production. Faixas personalizadas valem pelo nome.
#   GRADLE_TASK  tarefa de publicação (padrão: publishLawnWithQuickstepPlayReleaseBundle)
#   PROMOTE_TASK tarefa de promoção (padrão: promoteLawnWithQuickstepPlayReleaseArtifact)

BUILD_ONLY=0
BUILD_APK=0
WEAR=1
PROMOTE_ONLY=0
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]}; i++)); do
    arg="${ARGS[$i]}"
    case "$arg" in
        --tracks) i=$((i + 1)); PLAY_TRACK="${ARGS[$i]:?ERRO: --tracks precisa de um valor}" ;;
        --build-only) BUILD_ONLY=1 ;;
        --apk) BUILD_APK=1 ;;
        --no-wear) WEAR=0 ;;
        --promote-only) PROMOTE_ONLY=1 ;;
        --tracks=*) PLAY_TRACK="${arg#--tracks=}" ;;
        -h|--help) sed -n '4,30p' "$0"; exit 0 ;;
        *) echo "ERRO: argumento desconhecido: $arg" >&2; exit 1 ;;
    esac
done

cd "$(dirname "$0")"

if [ "$PROMOTE_ONLY" -eq 1 ]; then
    [ "$BUILD_ONLY" -eq 0 ] && [ "$BUILD_APK" -eq 0 ] || {
        echo "ERRO: --promote-only não combina com --build-only nem com --apk." >&2; exit 1; }
    # Sem --tracks, leva o build da faixa interna para o teste fechado.
    PLAY_TRACK="${PLAY_TRACK:-internal,closed-testing}"
fi

# ── Faixas de publicação ─────────────────────────────────────────────────────
# Normaliza a lista (apelidos, espaços, repetidas) e valida antes de gastar
# minutos compilando ou buscando credenciais.
TRACKS=()
IFS=',' read -r -a RAW_TRACKS <<< "${PLAY_TRACK:-internal}"
for raw in "${RAW_TRACKS[@]}"; do
    track="$(echo "$raw" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
    case "$track" in
        "") continue ;;
        closed|closed-testing|closedtesting|teste-fechado) track="alpha" ;;
        open|open-testing|opentesting|teste-aberto) track="beta" ;;
        prod) track="production" ;;
    esac
    [[ "$track" =~ ^[a-z0-9_-]+$ ]] || { echo "ERRO: faixa inválida: '$raw'" >&2; exit 1; }
    already=0
    for t in ${TRACKS[@]+"${TRACKS[@]}"}; do [ "$t" = "$track" ] && already=1; done
    [ "$already" -eq 0 ] && TRACKS+=("$track")
done
[ "${#TRACKS[@]}" -gt 0 ] || TRACKS=(internal)
PRIMARY_TRACK="${TRACKS[0]}"
if [ "$PROMOTE_ONLY" -eq 1 ] && [ "${#TRACKS[@]}" -lt 2 ]; then
    echo "ERRO: --promote-only precisa de uma faixa de origem e ao menos uma de destino (ex.: --tracks internal,closed-testing)." >&2
    exit 1
fi

# ── Credenciais via 1Password CLI ────────────────────────────────────────────
# Keystore, service account e senhas de assinatura vêm do 1Password em tempo
# de execução, para um diretório temporário com permissão 700, apagado por
# trap ao final (inclusive em erro ou Ctrl-C).
#
# Requer: `brew install 1password-cli` + 1Password → Settings → Developer →
#         "Integrate with 1Password CLI".
OP_VAULT="${OP_VAULT:-Android}"

command -v op >/dev/null 2>&1 || {
    echo "ERRO: 1Password CLI (op) não encontrado. Instale com: brew install 1password-cli" >&2; exit 1; }
# `op whoami` falha quando a sessão vem da integração com o app desktop,
# mesmo com o CLI funcionando; testar um comando real.
op vault list --format json >/dev/null 2>&1 || {
    echo "ERRO: 1Password bloqueado ou não autenticado. Desbloqueie o app e tente de novo." >&2; exit 1; }

SECRETS_DIR="$(mktemp -d)"; chmod 700 "$SECRETS_DIR"
trap 'rm -rf "$SECRETS_DIR"' EXIT INT TERM

echo "-> Buscando credenciais no 1Password (cofre: $OP_VAULT)..."
# Promover não compila nada, então dispensa o keystore e as senhas de assinatura.
if [ "$PROMOTE_ONLY" -eq 0 ]; then
    op document get "playconsole-keystore" --vault "$OP_VAULT" --out-file "$SECRETS_DIR/keystore.jks" >/dev/null
fi
# A conta de serviço do Play só é necessária para publicar ou promover.
if [ "$BUILD_ONLY" -eq 0 ] && [ "$BUILD_APK" -eq 0 ]; then
    op document get "play-service-account" --vault "$OP_VAULT" --out-file "$SECRETS_DIR/service-account.json" >/dev/null
fi
chmod 600 "$SECRETS_DIR"/*

# Senhas vão por ORG_GRADLE_PROJECT_*, e não por -P, para não ficarem
# visíveis no `ps` da máquina.
[ -f "$SECRETS_DIR/service-account.json" ] && export ORG_GRADLE_PROJECT_PLAY_STORE_KEY="$SECRETS_DIR/service-account.json"
if [ "$PROMOTE_ONLY" -eq 0 ]; then
    export ORG_GRADLE_PROJECT_RELEASE_STORE_FILE="$SECRETS_DIR/keystore.jks"
    ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD="$(op read "op://$OP_VAULT/Play Signing/store_password")"
    ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS="$(op read "op://$OP_VAULT/Play Signing/key_alias")"
    ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD="$(op read "op://$OP_VAULT/Play Signing/key_password")"
    export ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD \
           ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS \
           ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD
fi

# O Gradle precisa localizar o SDK; este repo não versiona local.properties.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

# JDK: usa o JAVA_HOME se existir; senão tenta o do macOS ou o JDK que o
# próprio Gradle baixou em ~/.gradle/jdks.
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
fi
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME="$(ls -d "$HOME"/.gradle/jdks/*/*/Contents/Home "$HOME"/.gradle/jdks/*/Contents/Home 2>/dev/null | tail -1)"
fi
[ -n "$JAVA_HOME" ] || { echo "ERRO: JDK não encontrado. Defina JAVA_HOME." >&2; exit 1; }
export JAVA_HOME

# O submódulo das libs do AOSP precisa estar presente para compilar.
if [ -z "$(ls -A platform_frameworks_libs_systemui 2>/dev/null)" ]; then
    echo "-> Inicializando submódulo platform_frameworks_libs_systemui..."
    git submodule update --init --depth 1
fi
echo "-> Credenciais carregadas."

# As demais faixas recebem o MESMO build, promovido a partir da primeira. Uma
# faixa que falhe (nome inexistente, por exemplo) não impede as outras; o
# resumo no fim diz o que foi promovido e o script sai com erro.
FAILED=()
promote_tracks() {
    for track in ${TRACKS[@]+"${TRACKS[@]:1}"}; do
        echo "Promovendo $PRIMARY_TRACK -> $track..."
        if ! ./gradlew "${PROMOTE_TASK:-promoteLawnWithQuickstepPlayReleaseArtifact}" \
            --from-track "$PRIMARY_TRACK" --promote-track "$track" --no-daemon; then
            FAILED+=("$track")
        fi
    done
    promote_wear_tracks
}

# O relógio sobe na mesma ficha, na faixa Wear OS ("wear:<faixa>"), com o mesmo build do celular.
wear_enabled() { [ "$WEAR" -eq 1 ] && [ -f wear/app/build.gradle.kts ]; }

promote_wear_tracks() {
    wear_enabled || return 0
    for track in ${TRACKS[@]+"${TRACKS[@]:1}"}; do
        echo "Promovendo relógio wear:$PRIMARY_TRACK -> wear:$track..."
        if ! ./gradlew ":wear-app:promoteLauncherReleaseArtifact" \
            --from-track "wear:$PRIMARY_TRACK" --promote-track "wear:$track" --no-daemon; then
            FAILED+=("wear:$track")
        fi
    done
}

# Lista o resultado de cada faixa e sai com erro se alguma falhou.
report_promotions() {
    for track in ${TRACKS[@]+"${TRACKS[@]:1}"}; do
        case " ${FAILED[*]-} " in
            *" $track "*) echo "  FALHOU: $track" ;;
            *) echo "  promovido: $track" ;;
        esac
    done
    if [ "${#FAILED[@]}" -gt 0 ]; then
        echo "ERRO: não foi possível promover para: ${FAILED[*]}" >&2
        exit 1
    fi
}

if [ "$PROMOTE_ONLY" -eq 1 ]; then
    # O plugin lê a faixa padrão da configuração; a de origem vem por --from-track.
    export ORG_GRADLE_PROJECT_PLAY_TRACK="$PRIMARY_TRACK"
    promote_tracks
    echo "Build atual de $PRIMARY_TRACK:"
    report_promotions
    exit 0
fi


# Dynamically generate versions
# versionCode = epoch em segundos (sempre crescente); versionName = data/hora.
EPOCH=$(date +%s)
VERSION_CODE=$((EPOCH + 0))
VERSION_NAME=$(date +%Y%m%d%H%M%S)

echo "Generating new release version: Code $VERSION_CODE | Name $VERSION_NAME"

# As versões entram no build.gradle por propriedades do Gradle, então o
# código-fonte não é alterado (diferente do sed do watchface).
export ORG_GRADLE_PROJECT_RELEASE_VERSION_CODE="$VERSION_CODE"
export ORG_GRADLE_PROJECT_RELEASE_VERSION_NAME="$VERSION_NAME"
export ORG_GRADLE_PROJECT_PLAY_TRACK="$PRIMARY_TRACK"

# Builds anteriores deixam artefatos do KSP e do dex que quebram o build
# seguinte; um clean evita o problema (`classes2.dex` / erros de KSP).
./gradlew --stop >/dev/null 2>&1 || true
./gradlew clean --no-daemon

if [ "$BUILD_APK" -eq 1 ]; then
    echo "Building signed APK..."
    ./gradlew assembleLawnWithQuickstepPlayRelease --no-daemon
    if wear_enabled; then
        ./gradlew :wear-app:assembleLauncherRelease --no-daemon
        WEAR_APK="$(find wear/app/build/outputs/apk/launcher/release -name '*.apk' | head -1)"
        [ -n "$WEAR_APK" ] || { echo "ERRO: APK do relógio não encontrado." >&2; exit 1; }
        mkdir -p releases
        cp "$WEAR_APK" "releases/terminal-wear-$VERSION_NAME.apk"
        echo "APK do relógio: releases/terminal-wear-$VERSION_NAME.apk"
    fi
    APK="$(find build/outputs/apk/lawnWithQuickstepPlay/release -name '*.apk' | head -1)"
    [ -n "$APK" ] || { echo "ERRO: APK não encontrado em build/outputs/apk." >&2; exit 1; }
    mkdir -p releases
    cp "$APK" "releases/terminal-$VERSION_NAME.apk"
    echo "APK assinado: releases/terminal-$VERSION_NAME.apk"
    exit 0
fi

if [ "$BUILD_ONLY" -eq 1 ]; then
    echo "Building signed bundle (sem publicar)..."
    ./gradlew bundleLawnWithQuickstepPlayRelease --no-daemon
    mkdir -p releases
    BUNDLE="$(find build/outputs/bundle -name '*.aab' | head -1)"
    [ -n "$BUNDLE" ] || { echo "ERRO: bundle não encontrado em build/outputs/bundle." >&2; exit 1; }
    cp "$BUNDLE" "releases/terminal-$VERSION_NAME.aab"
    echo "Bundle assinado: releases/terminal-$VERSION_NAME.aab"
    if wear_enabled; then
        ./gradlew :wear-app:bundleLauncherRelease --no-daemon
        WEAR_BUNDLE="$(find wear/app/build/outputs/bundle/launcherRelease -name '*.aab' | head -1)"
        [ -n "$WEAR_BUNDLE" ] || { echo "ERRO: bundle do relógio não encontrado." >&2; exit 1; }
        cp "$WEAR_BUNDLE" "releases/terminal-wear-$VERSION_NAME.aab"
        echo "Bundle do relógio: releases/terminal-wear-$VERSION_NAME.aab"
    fi
    exit 0
fi

# Execute build and publish using Gradle Play Publisher
echo "Building and Publishing to Play Store (faixa: $PRIMARY_TRACK)..."
./gradlew "${GRADLE_TASK:-publishLawnWithQuickstepPlayReleaseBundle}" --no-daemon
if wear_enabled; then
    echo "Publicando o app do relógio (faixa: wear:$PRIMARY_TRACK)..."
    ./gradlew :wear-app:publishLauncherReleaseBundle --no-daemon
fi

# As demais faixas recebem o MESMO build, promovido a partir da primeira. Uma
# faixa que falhe (nome inexistente, por exemplo) não impede as outras; o
# resumo no fim diz o que foi publicado e o script sai com erro.
promote_tracks

echo "Release $VERSION_NAME (code $VERSION_CODE) publicado em: $PRIMARY_TRACK"
report_promotions
