package vip.floatationdevice.mc2gforward;

import cn.hutool.json.JSONObject;
import com.google.common.eventbus.Subscribe;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import vip.floatationdevice.guilded4j.G4JClient;
import vip.floatationdevice.guilded4j.G4JWebSocketClient;
import vip.floatationdevice.guilded4j.event.ChatMessageCreatedEvent;
import vip.floatationdevice.guilded4j.event.GuildedWebsocketClosedEvent;
import vip.floatationdevice.guilded4j.event.GuildedWebsocketInitializedEvent;
import vip.floatationdevice.guilded4j.object.ChatMessage;

import java.io.*;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static vip.floatationdevice.mc2gforward.MC2GForward.*;

@SuppressWarnings("UnstableApiUsage") public class BindManager implements Listener, CommandExecutor
{//TODO: cleanups and optimizations when im began to feel better
    public static HashMap<String, UUID> bindMap=new HashMap<String, UUID>();// key: guilded userId; value: mc player uuid
    public static HashMap<String, UUID> pendingMap=new HashMap<String, UUID>();// key: bind code; value: mc player uuid
    public static final Random r=new Random();
    G4JClient client;

    public BindManager()
    {
        loadBindMap();
        this.client=new G4JClient(MC2GForward.token);
        instance.getLogger().info("Connecting to Guilded server");
        client.ws.connect();
        client.ws.eventBus.register(this);
    }

    @Subscribe
    public void onG4JConnectionOpened(GuildedWebsocketInitializedEvent event)
    {
        instance.getLogger().info("Connection to Guilded server opened");
    }
    @Subscribe
    public void onG4JConnectionClosed(GuildedWebsocketClosedEvent event)
    {
        instance.getLogger().warning("Connection to Guilded server lost. Reconnecting");
        client.ws=new G4JWebSocketClient(token);
        client.ws.connect();
        client.ws.eventBus.register(this);
    }
    @Subscribe
    public void onGuildedChat(ChatMessageCreatedEvent event)
    {
        if(event.getChatMessageObject().getContent().startsWith("/mc2g mkbind"))
        {
            String[] args=event.getChatMessageObject().getContent().split(" ");
            // args.length=3; args[0]="/mc2g", args[1]="bind", args[2]="<code>"
            if(args.length!=3)// incorrect command format?
            {
                sendGuildedMsg("[X] Usage: /mc2g mkbind <CODE>", event.getChatMessageObject().getMsgId());
            }
            else// right usage?
            {
                if(bindMap.containsKey(event.getChatMessageObject().getCreatorId()))// player already bound?
                {
                    sendGuildedMsg("[X] Your account has been bound to "+getPlayerName(bindMap.get(event.getChatMessageObject().getCreatorId()))+" before", event.getChatMessageObject().getMsgId());
                }
                else// player not bound?
                {
                    if(pendingMap.containsKey(args[2]))// code matched?
                    {
                        bindMap.put(event.getChatMessageObject().getCreatorId(), pendingMap.get(args[2]));
                        pendingMap.remove(args[2]);
                        try{Bukkit.getPlayer(bindMap.get(event.getChatMessageObject().getCreatorId())).sendMessage("[MC2GForward] Binding process completed. Now you can send messages from Guilded server to Minecraft server");}catch(Exception ignored){}
                        sendGuildedMsg("[i] Successfully bound your account to "+getPlayerName(bindMap.get(event.getChatMessageObject().getCreatorId())), event.getChatMessageObject().getMsgId());
                        instance.getLogger().info(getPlayerName(bindMap.get(event.getChatMessageObject().getCreatorId()))+" bound successfully");
                        saveBindMap();
                    }
                    else// code not in pending list?
                    {
                        sendGuildedMsg("[X] Invalid bind code. Please check the code you typed or use \"/mc2g mkbind\" again in Minecraft to request a new code", event.getChatMessageObject().getMsgId());
                    }
                }
            }
        }
        else if(event.getChatMessageObject().getContent().equals("/mc2g rmbind"))
        {
            if(bindMap.containsKey(event.getChatMessageObject().getCreatorId()))// player bound?
            {
                try{Bukkit.getPlayer(bindMap.get(event.getChatMessageObject().getCreatorId())).sendMessage("[MC2GForward] Successfully unbound your Guilded account");}catch(Exception ignored){}
                UUID removed=bindMap.remove(event.getChatMessageObject().getCreatorId());
                sendGuildedMsg("[i] Successfully unbound your Guilded account", event.getChatMessageObject().getMsgId());
                instance.getLogger().info(getPlayerName(removed)+" unbound successfully");
                saveBindMap();
            }
            else// player not bound?
            {
                sendGuildedMsg("[X] You are not bound to any Minecraft account", event.getChatMessageObject().getMsgId());
            }
        }
        else
        {
            if(bindMap.containsKey(event.getChatMessageObject().getCreatorId()))
                Bukkit.broadcastMessage("<"+getPlayerName(bindMap.get(event.getChatMessageObject().getCreatorId()))+"> "+event.getChatMessageObject().getContent());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if(!(sender instanceof Player))
        {
            sender.sendMessage("This command can only be used in-game");
            return false;
        }

        if(args.length==1&&args[0].equals("mkbind"))
        {
            String code="";// 10-digit random bind code from ASCII x21(!) to x7E(~)
            for(int i=0;i!=10;i++) code+=String.valueOf((char)(r.nextInt(94)+33));
            pendingMap.put(code, ((Player)sender).getUniqueId());
            sender.sendMessage("[MC2GForward] Your bind code is: "+code+"\n  Type \"/mc2g mkbind "+code+"\" in Guilded server to complete the bind process");
            instance.getLogger().info(sender.getName()+" requested a bind code: "+code);
            return true;
        }
        else if(args.length==1&&args[0].equals("rmbind"))
        {
            for(String u:bindMap.keySet())
            {
                if(bindMap.get(u).equals(((Player)sender).getUniqueId()))
                {
                    bindMap.remove(u);
                    sender.sendMessage("[MC2GForward] Successfully unbound your Guilded account");
                    instance.getLogger().info(sender.getName()+" unbound successfully");
                    saveBindMap();
                    return true;
                }
            }
            sender.sendMessage("[MC2GForward] You must bind a Guilded account first");
            return false;
        }
        else
        {
            sender.sendMessage("[MC2GForward] Usage: /mc2g <mkbind/rmbind>");
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
                    catch(Exception e) {instance.getLogger().severe("Failed to send message to Guilded server: "+e);}
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
            instance.getLogger().info("Bind map saved");
        }
        catch(Exception e) {instance.getLogger().severe("Failed to save bind map: "+e);}
    }
    public void loadBindMap()
    {
        try
        {
            ObjectInputStream o=new ObjectInputStream(new FileInputStream(cfgPath+"bindMap.dat"));
            BindMapContainer temp=(BindMapContainer)o.readObject();
            o.close();
            bindMap=temp.saveBindMap;
            instance.getLogger().info("Bind map loaded");
        }
        catch(FileNotFoundException ignored){}
        catch(Exception e) {instance.getLogger().severe("Failed to load bind map: "+e);}
    }
}
