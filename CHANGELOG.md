# Changelog

## [Unreleased]

### Adicionado
- Receita de craft pro Scoreboard Dummy Spawner: um Training Dummy Spawner + 1 diamante (sem
  formato fixo, tanto faz a posição na bancada).
- Modpack padrão embutido no mod: se `modpackId`/`workerBaseUrl`/`workerApiKey` ficarem em branco,
  o mod agora usa um modpack e um worker padrão já embutidos no jar (não expostos no config), em
  vez de cair pro modo local-only. Modpacks com config próprio (como o ATM 11) continuam com seu
  próprio ranking dedicado normalmente. O nome de exibição desse modpack padrão fica vazio de
  propósito, então nenhum nome aparece no título do ranking global nesse caso.
- Reset semanal automático (opt-in por modpack) no worker: toda semana, domingo 00:00 UTC, um cron
  trigger zera o ranking de qualquer modpack marcado com `auto_reset_weekly`. Desativado por padrão
  para modpacks com nome próprio (ex: ATM 11) - ativado só no modpack padrão embutido.
- A tela do ranking agora reabre sempre na última aba (Local/Global) que o jogador deixou aberta,
  em vez de sempre voltar pro Global. Só um comportamento fixo em memória - não é uma config, então
  não aparece em nenhuma tela de opções.

### Corrigido
- Um problema de comunicação com o worker (rede fora do ar, chave errada, resposta inesperada) era
  mostrado com a mesma mensagem genérica de "seu recorde não entrou", como se o jogador tivesse
  sido superado por outro. Agora esses casos mostram uma mensagem própria informando que houve um
  problema de comunicação, em vez de parecer uma disputa de rank perdida.

### Notas
- Como consequência do modpack padrão, deixar o config em branco não significa mais "sem ranking
  global" - significa "participa do ranking padrão compartilhado". Não existe mais um jeito de só
  usar o ranking local sem reportar nada pra nenhum worker.

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
- Chave de submissão do worker agora é **por modpack** (coluna `api_key` na tabela `modpacks`),
  em vez de uma única chave global - se a chave de um modpack vazar, só aquele modpack é afetado.

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
