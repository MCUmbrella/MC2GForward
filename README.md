# MC2GForward
#### Forward messages between Minecraft and Guilded server
### Note:
- When the plugin is first installed on the server it will create an empty configuration file. You need to fill in your bot token and channel ID.
- After setting up, **RESTART the server.**<br>
*Did I make it clear? DO, NOT, RELOAD*
### Configuration: `config.properties`
```properties
token=
channel=
forwardJoinLeaveEvents=true
debug=false
```
- `token`: your bot token
- `channel`: target channel ID. All message forwarding occurs on this channel
- `forwardJoinLeaveEvents`: whether forward player join/quit messages or not
- `debug`: print the response after forwarding message to Guilded. If you got a JSON with "Exception" or "code" key that means message forwarding not successful<br>
