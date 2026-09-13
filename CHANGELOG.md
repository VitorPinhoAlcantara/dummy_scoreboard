# Changelog

## [1.0] - 2026-09-12

Primeira release de teste. Ainda em fase experimental - use em servidores de teste antes de
depender disso num modpack "de verdade".

### Adicionado
- `ScoreboardDummyEntity`: novo boneco de treino que, ao clicar com o botão direito, abre uma
  tela de ranking em vez do menu de equipamento do dummy padrão.
- Ranking do **maior hit** (não soma dano, não é DPS - só o golpe mais forte já dado).
- Ranking **local** (por servidor, ao vivo) e ranking **global** (compartilhado entre servidores
  do mesmo modpack), com botão pra alternar entre os dois na tela.
- Aviso no chat quando o jogador bate um recorde, e uma segunda mensagem confirmando (ou não)
  quando o recorde é validado contra o ranking global mais atualizado.
- Snapshot de auditoria completo por recorde (inventário, armadura, curios, efeitos, atributos,
  mão principal/secundária) - guardado só para auditoria; o ranking público mostra apenas nick e dano.
- Backend próprio: Cloudflare Worker + D1, com endpoint de leitura (`GET /leaderboard/:modpackId`)
  e escrita (`POST /submit`), cache/debounce configuráveis no lado do servidor pra não martelar o
  worker a cada hit.
- Allowlist de modpacks: o worker só aceita/lista um `modpackId` que tenha sido pré-cadastrado à
  mão no banco - qualquer id desconhecido é ignorado silenciosamente, pra impedir que alguém
  "chute" o id de um modpack real e polua o ranking dele.
- Persistência local do ranking em disco (sobrevive a restart do servidor).
- Config do servidor: `modpackId`, `workerBaseUrl`, `workerApiKey`, `leaderboardRefreshSeconds`
  (default 600s) e `recordDebounceSeconds` (default 20s) - todos vazios/no valor padrão de fábrica
  até o admin configurar.

### Notas
- Sem `modpackId`/`workerBaseUrl` configurados, o mod usa um stub local em vez de falar com um
  worker de verdade - então o mod funciona (só com ranking local) mesmo sem nenhuma configuração.
