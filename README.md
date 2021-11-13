# MC2GForward
#### Forward messages between Minecraft and Guilded server
### Note:
- When the plugin is first installed on the server it will create an empty configuration file. You need to fill in your bot token and channel ID.
- After setting up, **RESTART the server.**<br>
*Did I make it clear? DO, NOT, RELOAD*
### Configuration: `config.properties`
```properties
language=en_US
token=
channel=
forwardJoinLeaveEvents=true
debug=false
```
- `language`: the language to use for the plugin. Default: `en_US`.
- `token`: your bot token.
- `channel`: target channel ID. All message forwarding occurs in this channel.
- `forwardJoinLeaveEvents`: whether forward player join/quit messages or not.
- `debug`: print the response after forwarding a message to Guilded.
### Binding your Guilded account:
1. Log into the Minecraft server and type `/mc2g mkbind` and you will get a 10-digit random binding code.
2. Open Guilded client and type `/mc2g mkbind <code>`.
- If you want to unbind, type `/mc2g rmbind` at any side.
