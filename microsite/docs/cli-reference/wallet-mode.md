---
sidebar_position: 5
---

# Wallet Mode

```                           
Command: wallet [sync|init|recover-keys|current-address|export-vk|import-vks] [options]
Wallet mode
Command: wallet sync
Sync wallet
  -n, --network <value>    Network name: Possible values: mainnet, testnet, private. (mandatory)
  -h, --host <value>       The host of the node. (mandatory)
  --port <value>   Port Bifrost node. (mandatory)
  --party-name <value>     Name of the party. (mandatory)
  --contract-name <value>  Name of the contract. (mandatory)
  --walletdb <value>       Wallet DB file. (mandatory)
Command: wallet init
Initialize wallet
  -n, --network <value>    Network name: Possible values: mainnet, testnet, private. (mandatory)
  -w, --password <value>   Password for the encrypted key. (mandatory)
  -o, --output <value>     The output file. (optional)
  --newwallet <value>      Wallet DB file. (mandatory)
  --mnemonicfile <value>   Mnemonic output file. (mandatory)
  -P, --passphrase <value>
                           Passphrase for the encrypted key. (optional))
Command: wallet recover-keys
Recover Wallet Main Key
  -n, --network <value>    Network name: Possible values: mainnet, testnet, private. (mandatory)
  -m, --mnemonic <value>   Mnemonic for the key. (mandatory)
  -w, --password <value>   Password for the encrypted key. (mandatory)
  -o, --output <value>     The output file. (optional)
  --walletdb <value>       Wallet DB file. (mandatory)
  -P, --passphrase <value>
                           Passphrase for the encrypted key. (optional))
Command: wallet current-address
Obtain current address
  --walletdb <value>       Wallet DB file. (mandatory)
Command: wallet export-vk
Export verification key
  -k, --keyfile <value>    The key file.
  -w, --password <value>   Password for the encrypted key. (mandatory)
  --walletdb <value>       Wallet DB file. (mandatory)
  -o, --output <value>     The output file.
  --walletdb <value>       Wallet DB file. (mandatory)
  --party-name <value>     Name of the party. (mandatory)
  --contract-name <value>  Name of the contract. (mandatory)
  --state <value>          State from where we are sending the funds from
Command: wallet import-vks
Import verification key
  --walletdb <value>       Wallet DB file. (mandatory)
  --party-name <value>     Name of the party. (mandatory)
  --contract-name <value>  Name of the contract. (mandatory)
  --input-vks <value>      The keys to import. (mandatory)
  
```