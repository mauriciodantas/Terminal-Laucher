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
#
# Variáveis opcionais:
#   OP_VAULT     cofre do 1Password (padrão: Android)
#   PLAY_TRACK   faixa(s) da Play Store, separadas por vírgula (padrão: internal).
#                O build é publicado na primeira e promovido às demais, então
#                todas recebem o mesmo versionCode. Apelidos aceitos:
#                closed-testing/closed -> alpha, open-testing/open -> beta,
#                prod -> production. Faixas personalizadas valem pelo nome.
#   GRADLE_TASK  tarefa de publicação (padrão: publishLawnWithQuickstepPlayReleaseBundle)

BUILD_ONLY=0
BUILD_APK=0
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]}; i++)); do
    arg="${ARGS[$i]}"
    case "$arg" in
        --tracks) i=$((i + 1)); PLAY_TRACK="${ARGS[$i]:?ERRO: --tracks precisa de um valor}" ;;
        --build-only) BUILD_ONLY=1 ;;
        --apk) BUILD_APK=1 ;;
        --tracks=*) PLAY_TRACK="${arg#--tracks=}" ;;
        -h|--help) sed -n '4,24p' "$0"; exit 0 ;;
        *) echo "ERRO: argumento desconhecido: $arg" >&2; exit 1 ;;
    esac
done

cd "$(dirname "$0")"

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
op document get "playconsole-keystore" --vault "$OP_VAULT" --out-file "$SECRETS_DIR/keystore.jks" >/dev/null
# A conta de serviço do Play só é necessária para publicar.
if [ "$BUILD_ONLY" -eq 0 ] && [ "$BUILD_APK" -eq 0 ]; then
    op document get "play-service-account" --vault "$OP_VAULT" --out-file "$SECRETS_DIR/service-account.json" >/dev/null
fi
chmod 600 "$SECRETS_DIR"/*

# Senhas vão por ORG_GRADLE_PROJECT_*, e não por -P, para não ficarem
# visíveis no `ps` da máquina.
export ORG_GRADLE_PROJECT_RELEASE_STORE_FILE="$SECRETS_DIR/keystore.jks"
[ -f "$SECRETS_DIR/service-account.json" ] && export ORG_GRADLE_PROJECT_PLAY_STORE_KEY="$SECRETS_DIR/service-account.json"
ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD="$(op read "op://$OP_VAULT/Play Signing/store_password")"
ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS="$(op read "op://$OP_VAULT/Play Signing/key_alias")"
ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD="$(op read "op://$OP_VAULT/Play Signing/key_password")"
export ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD \
       ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS \
       ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD

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
    exit 0
fi

# Execute build and publish using Gradle Play Publisher
echo "Building and Publishing to Play Store (faixa: $PRIMARY_TRACK)..."
./gradlew "${GRADLE_TASK:-publishLawnWithQuickstepPlayReleaseBundle}" --no-daemon

# As demais faixas recebem o MESMO build, promovido a partir da primeira. Uma
# faixa que falhe (nome inexistente, por exemplo) não impede as outras; o
# resumo no fim diz o que foi publicado e o script sai com erro.
FAILED=()
for track in ${TRACKS[@]+"${TRACKS[@]:1}"}; do
    echo "Promovendo $PRIMARY_TRACK -> $track..."
    if ! ./gradlew promoteArtifact --from-track "$PRIMARY_TRACK" --promote-track "$track" --no-daemon; then
        FAILED+=("$track")
    fi
done

echo "Release $VERSION_NAME (code $VERSION_CODE) publicado em: $PRIMARY_TRACK"
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
