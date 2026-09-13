# Changelog

## [1.1] - 2026-09-13

### Adicionado
- Cores no rank: nome do #1 em verde, #2 e #3 em amarelo (local e global).
- Título do ranking global agora mostra o nome do modpack quando `modpackDisplayName` está
  configurado (ex: "Global Leaderboard - ATM 11"). Puramente cosmético, novo config opcional.
- Limite de requisições por IP no worker (30/min em `/leaderboard`, 10/min em `/submit`) usando o
  binding nativo de Rate Limiting da Cloudflare - gratuito em qualquer plano, protege contra
  alguém martelando os endpoints diretamente.
- Detecção de adulteração de atributos de combate (ex: `/attribute ... base set`): um hit só conta
  pro ranking se os atributos de combate do jogador (dano, velocidade de ataque, vida máxima,
  armadura, resistência a knockback, sorte) baterem com os valores padrão do vanilla. Mods legítimos
  sempre aplicam seus bônus como modifiers, nunca alterando a base diretamente - então uma base fora
  do padrão é sinal forte de comando/exploit, não falso positivo. Checado tanto no mod (bloqueia o
  hit por completo, local e global) quanto de forma independente no worker (rejeita a submissão
  mesmo que alguém tente pular a checagem do mod com um client modificado ou POST direto).
- Jogadores em modo Creative não têm mais hits contabilizados em nenhum ranking.

## [1.0.1] - 2026-09-12

### Corrigido
- Submissão de recorde para o ranking global falhava silenciosamente sempre que o item capturado
  no snapshot de auditoria (arma, armadura, etc.) tinha encantamento, brasão ou outro dado
  vinculado a registry. O motivo: o snapshot era codificado com `JsonOps` puro, que não tem acesso
  aos registries do jogo - o encode falhava (`Can't access registry ResourceKey[...]`) e o mod
  tratava isso como se o jogador simplesmente não tivesse batido o recorde, mostrando a mensagem
  genérica "o ranking foi atualizado e seu recorde não entrou :(", sem indicar que era um erro.
  Corrigido usando `RegistryOps` (que carrega o acesso aos registries do servidor) tanto no envio
  pro worker quanto na persistência local em disco.

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
