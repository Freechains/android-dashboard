# Sync Tool - Freechains

The *sync tool* sets up a dedicated chain to persist and replicate peers and
chains of interest in the network.

- A *sync chain* is just a normal chain, but it is recommended to be private
  so that only the key holders can read and write to it:

```
$ freechains-host start <path> &        # starts freechains, if not yet started
$ freechains crypto shared <password>   # creates a <KEY> for the sync chain
$ freechains chains join \$sync <KEY>   # creates a private chain
$ freechains-sync \$sync &              # keeps the host up-to-date
```

The tool should be started every time freechains is started (lines `1` and `4`
above)

- Posts in a sync chain are interpreted as commands that can add/remove
  peers/chains of interest:

```
freechains chain \$sync post inline "peers lcc-uerj.duckdns.org ADD"
freechains chain \$sync post inline "peers francisco-santanna.duckdns.org ADD"
freechains chain \$sync post inline "chains # ADD"
freechains chain \$sync post inline "chains @7EA6E8E2DD5035AAD58AE761899D2150B9FB06F0C8ADC1B5FE817C4952AC06E6 ADD"
```

Follows the list of available commands:

- `peers <addr:port> ADD`
- `peers <addr:port> REM`
- `chains <name> (<key> | ADD)`
- `chains <name> REM`

The tool interprets all existing posts in the chain from the oldest to newest
and then listens for new posts which are also interpreted in real time.
The tool applies a `join` to added chains and a `leave` to removed chains
automatically.
It also listens for incoming data in all chains and synchronizes them with the
added peers.
