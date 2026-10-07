---
name: gerar-comando
description: Gera arquivos de comandos (.tcmd) importáveis na barra de comando do Terminal Launcher, como "imdb [filme/ator]", "yt [busca]" ou "w [contato] [mensagem]". Use quando o usuário pedir um arquivo/pacote de comando, atalho de busca em um site ou app, ou um apelido para a barra de comando.
---

# Gerar arquivo de comando (.tcmd)

Um `.tcmd` é um JSON lido por `CommandPacks` (`lawnchair/src/app/lawnchair/command/CommandPack.kt`) e importado pelo app em `ImportCommandsActivity`. Os arquivos gerados ficam em `comandos/`.

## Passos

1. Descubra com o usuário (só pergunte o que não der para inferir):
   - **letra**: palavra curta, só letras e números (ex.: `imdb`, `yt`).
   - **rótulo**: nome mostrado na lista (ex.: `IMDb · filme/ator`).
   - **URL/URI** de destino com placeholders (veja abaixo). Para buscas em sites, pesquise o formato da URL de busca do site.
   - **argumento**: o que o usuário digita depois da letra.
2. Rode o gerador (ele valida e escreve o arquivo):

   ```
   python3 .claude/skills/gerar-comando/scripts/gerar_tcmd.py \
     --letter imdb --label "IMDb · filme/ator" \
     --template "https://www.imdb.com/find/?q={text}" --arg TEXT
   ```

   Opções: `--package` (repetível; força o app, ex. `com.spotify.music`), `--sms-fallback`, `--action` (padrão `android.intent.action.VIEW`), `--mime`, `--extra`, `--kind` (`INTENT`|`SHORTCUT`), `--out` (padrão `comandos/<letra>.tcmd`), `--alias NOME=EXPANSÃO` (repetível).
   Para vários comandos no mesmo pacote, rode o script uma vez por comando com `--merge --out comandos/pacote.tcmd`.
3. Confirme a saída (`OK: ...`) e entregue o arquivo ao usuário com `SendUserFile`. Explique que ele importa pela tela de importar comandos do app.
4. Só faça commit se o usuário pedir (mensagens em português, prefixo da área, ex.: `Barra de comando: ...`).

## Regras do formato

- `arg`: `NONE` (só a letra), `TEXT` (`letra [texto]`), `CONTACT`, `CONTACT_AND_TEXT`.
- Placeholders do template, todos codificados na URL: `{text}`, `{name}`, `{number}` (dígitos com DDI), `{phone}` (número como salvo). Em `SHORTCUT`, o template é o id do atalho e `--package` é obrigatório (um pacote só).
- `packages` vazio: abre no app que atender o link ou no navegador. Com pacotes: só nesses apps.
- Letras reservadas (não use): `abrir`, `alarme`, `calc`, `ligar`, `rota`, `t`, `c`. Na importação, letra inválida, reservada ou já em uso é ignorada, nunca substitui o que o usuário tem.
- Limites do pacote: 256 KB e 200 itens por lista.
- Campos fixos: `"format": "terminal-commands"`, `"version": 1`.

## Exemplo de saída

Veja `comandos/imdb.tcmd`.
