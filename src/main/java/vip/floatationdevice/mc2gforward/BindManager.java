package vip.floatationdevice.mc2gforward;

import cn.hutool.json.JSONObject;
import com.google.common.eventbus.Subscribe;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import vip.floatationdevice.guilded4j.G4JWebSocketClient;
import vip.floatationdevice.guilded4j.event.ChatMessageCreatedEvent;
import vip.floatationdevice.guilded4j.event.GuildedWebsocketClosedEvent;
import vip.floatationdevice.guilded4j.event.GuildedWebsocketInitializedEvent;
import vip.floatationdevice.guilded4j.object.ChatMessage;

import java.io.*;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static vip.floatationdevice.mc2gforward.MC2GForward.instance;
import static vip.floatationdevice.mc2gforward.MC2GForward.mc2gRunning;
import static vip.floatationdevice.mc2gforward.MC2GForward.token;
import static vip.floatationdevice.mc2gforward.MC2GForward.cfgPath;
import static vip.floatationdevice.mc2gforward.I18nUtil.getLocalizedMessage;

@SuppressWarnings("UnstableApiUsage") public class BindManager implements Listener, CommandExecutor
{
    public static HashMap<String, UUID> bindMap=new HashMap<String, UUID>();// key: guilded userId; value: mc player uuid
    public static HashMap<String, UUID> pendingMap=new HashMap<String, UUID>();// key: bind code; value: mc player uuid
    public static HashMap<UUID, String> pendingPlayerMap =new HashMap<UUID, String>();// pendingMap but with upside down
    public static final Random r=new Random();
    G4JWebSocketClient ws;

    public BindManager()
    {
        loadBindMap();
        ws=new G4JWebSocketClient(MC2GForward.token);
        instance.getLogger().info(getLocalizedMessage("connecting"));
        ws.eventBus.register(this);
        ws.connect();
    }

    @Subscribe
    public void onG4JConnectionOpened(GuildedWebsocketInitializedEvent event)
    {
        instance.getLogger().info(getLocalizedMessage("connected"));
    }
    @Subscribe
    public void onG4JConnectionClosed(GuildedWebsocketClosedEvent event)
    {
        if(mc2gRunning)
        {
            // if the plugin is running normally but the connection was closed
            // then we can consider it as unexpected and do a reconnection
            instance.getLogger().warning(getLocalizedMessage("disconnected-unexpected"));
            ws=new G4JWebSocketClient(token);
            ws.connect();
            ws.eventBus.register(this);
        }
        else
            // the plugin is being disabled or the server is stopping, so we can just ignore this
            instance.getLogger().info(getLocalizedMessage("disconnected"));
    }
    @Subscribe
    public void onGuildedChat(ChatMessageCreatedEvent event)
    {
        ChatMessage msg=event.getChatMessageObject();// the received ChatMessage object
        if(msg.getChannelId().equals(MC2GForward.channel))// in chat-forwarding channel?
            if(msg.getContent().startsWith("/mc2g mkbind"))
            {
                String[] args=msg.getContent().split(" ");
                // args.length=3; args[0]="/mc2g", args[1]="bind", args[2]="<code>"
                if(args.length!=3)// incorrect command format?
                {
                    sendGuildedMsg(getLocalizedMessage("g-usage"), msg.getMsgId());
                }
                else// right usage?
                {
                    if(bindMap.containsKey(msg.getCreatorId()))// player already bound?
                    {
                        sendGuildedMsg(getLocalizedMessage("g-already-bound").replace("%PLAYER%",getPlayerName(bindMap.get(msg.getCreatorId()))), msg.getMsgId());
                    }
                    else// player not bound?
                    {
                        if(pendingMap.containsKey(args[2]))// code matched?
                        {
                            bindMap.put(msg.getCreatorId(), pendingMap.get(args[2]));
                            pendingPlayerMap.remove(pendingMap.get(args[2]));
                            pendingMap.remove(args[2]);
                            try{Bukkit.getPlayer(bindMap.get(msg.getCreatorId())).sendMessage(getLocalizedMessage("m-bind-success"));}catch(Exception ignored){}
                            sendGuildedMsg(getLocalizedMessage("g-bind-success").replace("%PLAYER%",getPlayerName(bindMap.get(msg.getCreatorId()))), msg.getMsgId());
                            instance.getLogger().info(getLocalizedMessage("c-bind-success").replace("%PLAYER%",getPlayerName(bindMap.get(msg.getCreatorId()))));
                            saveBindMap();
                        }
                        else// code not in pending list?
                        {
                            sendGuildedMsg(getLocalizedMessage("invalid-code"), msg.getMsgId());
                        }
                    }
                }
            }
            else if(msg.getContent().equals("/mc2g rmbind"))
            {
                if(bindMap.containsKey(msg.getCreatorId()))// player bound?
                {
                    try{Bukkit.getPlayer(bindMap.get(msg.getCreatorId())).sendMessage(getLocalizedMessage("m-unbind-success"));}catch(Exception ignored){}
                    UUID removed=bindMap.remove(msg.getCreatorId());
                    sendGuildedMsg(getLocalizedMessage("g-unbind-success"), msg.getMsgId());
                    instance.getLogger().info(getLocalizedMessage("c-unbind-success").replace("%PLAYER%",getPlayerName(removed)));
                    saveBindMap();
                }
                else// player not bound?
                {
                    sendGuildedMsg(getLocalizedMessage("g-no-bind"), msg.getMsgId());
                }
            }
            else
            {
                if(!msg.getContent().startsWith("/")&&bindMap.containsKey(msg.getCreatorId()))
                    Bukkit.broadcastMessage("<"+getPlayerName(bindMap.get(msg.getCreatorId()))+"> "+msg.getContent());
            }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(!(sender instanceof Player))
        {
            sender.sendMessage(getLocalizedMessage("non-player-executor"));
            return false;
        }

        if(args.length==1&&args[0].equals("mkbind"))
        {
            String code="";// 10-digit random bind code from ASCII x21(!) to x7E(~)
            for(int i=0;i!=10;i++) code+=String.valueOf((char)(r.nextInt(94)+33));// StringBuilder not needed
            if(pendingPlayerMap.containsKey(((Player)sender).getUniqueId())) // remove old bind code if exists
                pendingMap.remove(pendingPlayerMap.get(((Player)sender).getUniqueId()));
            pendingMap.put(code, ((Player)sender).getUniqueId());
            pendingPlayerMap.put(((Player)sender).getUniqueId(), code);
            sender.sendMessage(getLocalizedMessage("m-code-requested").replace("%CODE%",code));
            instance.getLogger().info(getLocalizedMessage("c-code-requested").replace("%PLAYER%",sender.getName()).replace("%CODE%",code));
            return true;
        }
        else if(args.length==1&&args[0].equals("rmbind"))
        {
            for(String u:bindMap.keySet())
            {
                if(bindMap.get(u).equals(((Player)sender).getUniqueId()))
                {
                    bindMap.remove(u);
                    sender.sendMessage(getLocalizedMessage("m-unbind-success"));
                    instance.getLogger().info(getLocalizedMessage("c-unbind-success").replace("%PLAYER%",sender.getName()));
                    saveBindMap();
                    return true;
                }
            }
            sender.sendMessage(getLocalizedMessage("m-no-bind"));
            return false;
        }
        else
        {
            sender.sendMessage(getLocalizedMessage("m-usage"));
            return false;
        }
    }

    public void sendGuildedMsg(String msg, String replyTo)
    {
        Bukkit.getScheduler().runTaskAsynchronously(instance, new Runnable()
        {
            @Override
            public void run()
            {
                if(instance.g4JClient!=null)
                {
                    ChatMessage result=null;
                    try {result=instance.g4JClient.createChannelMessage(MC2GForward.channel,msg,new String[]{replyTo},false);}
                    catch(Exception e) {instance.getLogger().severe(getLocalizedMessage("msg-send-failed").replace("%EXCEPTION%",e.toString()));}
                    if(instance.debug&&result!=null) instance.getLogger().info("\n"+new JSONObject(result.toString()).toStringPretty());
                }
            }
        });
    }

    public static String getPlayerName(final UUID u)
    {
        try{return Bukkit.getPlayer(u).getName();}
        catch(NullPointerException e){return Bukkit.getOfflinePlayer(u).getName();}
    }

    public static class BindMapContainer implements Serializable
    {
        private static final long serialVersionUID=1L;
        public HashMap<String, UUID> saveBindMap;
        public BindMapContainer(HashMap<String, UUID> bindMap){saveBindMap=bindMap;}
    }
    public void saveBindMap()
    {
        try
        {
            ObjectOutputStream o=new ObjectOutputStream(new FileOutputStream(cfgPath+"bindMap.dat"));
            o.writeObject(new BindMapContainer(bindMap));
            o.close();
            instance.getLogger().info(getLocalizedMessage("bindmap-save-success"));
        }
        catch(Exception e) {instance.getLogger().severe(getLocalizedMessage("bindmap-save-failure").replace("%EXCEPTION%",e.toString()));}
    }
    public void loadBindMap()
    {
        try
        {
            ObjectInputStream o=new ObjectInputStream(new FileInputStream(cfgPath+"bindMap.dat"));
            BindMapContainer temp=(BindMapContainer)o.readObject();
            o.close();
            bindMap=temp.saveBindMap;
            instance.getLogger().info(getLocalizedMessage("bindmap-load-success"));
        }
        catch(FileNotFoundException ignored){}
        catch(Exception e) {instance.getLogger().severe(getLocalizedMessage("bindmap-load-failure").replace("%EXCEPTION%",e.toString()));}
    }
}
