package vip.floatationdevice.mc2gforward;

import cn.hutool.json.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vip.floatationdevice.guilded4j.G4JClient;
import vip.floatationdevice.guilded4j.object.ChatMessage;

import java.io.*;
import java.util.Properties;

public final class MC2GForward extends JavaPlugin implements Listener
{
    public static MC2GForward instance;
    final static String cfgPath="."+File.separator+"plugins"+File.separator+"MC2GForward"+File.separator;
    static String token,channel;
    G4JClient g4JClient;
    BindManager bindMgr;
    Boolean forwardJoinLeaveEvents=true;
    Boolean debug=false;

    @Override
    public void onEnable()
    {
        instance=this;
        Bukkit.getPluginManager().registerEvents(this, this);
        try
        {
            new File(new File(cfgPath+"config.properties").getParent()).mkdirs();
            File file=new File(cfgPath+"config.properties");
            if(!file.exists())
            {
                getLogger().severe("Config file not found and a empty one will be created. Set the token and channel UUID and RESTART server.");
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write("token="+System.getProperty("line.separator")+"channel="+System.getProperty("line.separator")+"forwardJoinLeaveEvents=true"+System.getProperty("line.separator")+"debug=false"+System.getProperty("line.separator"));
                bw.flush();
                bw.close();
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            BufferedReader cfg=new BufferedReader(new FileReader(file));
            Properties p=new Properties();
            p.load(cfg);
            token=p.getProperty("token");
            channel=p.getProperty("channel");
            forwardJoinLeaveEvents=Boolean.parseBoolean(p.getProperty("forwardJoinLeaveEvents"));
            debug=Boolean.parseBoolean(p.getProperty("debug"));
            if(token==null||channel.length()!=36)
            {
                getLogger().severe("Invalid config. Check the config file and RESTART the server.");
                g4JClient=null;
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            g4JClient=new G4JClient(token);
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable()
            {// fuck lambdas all my codes are lambda-free
                @Override
                public void run()
                {
                    bindMgr=new BindManager();
                    Bukkit.getPluginManager().registerEvents(bindMgr, instance);
                    getCommand("mc2g").setExecutor(bindMgr);
                }
            });
            sendGuildedMsg("`*** MC2GForward started ***`");
        }catch (Throwable e)
        {
            getLogger().severe("Failed to initialize plugin!");
            g4JClient=null;
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override
    public void onDisable()
    {
        if(g4JClient!=null)
        {
            ChatMessage result=null;
            try {result=g4JClient.createChannelMessage(channel,"`*** MC2GForward stopped ***`",null,null);}
            catch (Exception e) {getLogger().severe("Failed to send message to Guilded server: "+e);}
            //g4JClient.close();
            g4JClient=null;
            bindMgr.client.ws.close();
            bindMgr=null;
            if(debug&&result!=null)getLogger().info("\n"+new JSONObject(result.toString()).toStringPretty());
        }
    }
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event)
    {
        new Thread()
        {
            @Override
            public void run()
            {
                String message=event.getMessage();
                if(!message.startsWith("/"))
                {
                    sendGuildedMsg("<"+event.getPlayer().getName()+"> "+message);
                }
            }
        }.start();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("`[+] "+event.getPlayer().getName()+" connected`");
    }

    @EventHandler
    public void onUnusualLeave(PlayerKickEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("`("+event.getPlayer().getName()+" lost connection: "+event.getReason()+")`");
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("`[-] "+event.getPlayer().getName()+" disconnected`");
    }

    public void sendGuildedMsg(String msg)
    {
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable()
        {
            @Override
            public void run()
            {
                if(g4JClient!=null)
                {
                    ChatMessage result=null;
                    try {result=g4JClient.createChannelMessage(channel,msg,null,null);}
                    catch(Exception e) {getLogger().severe("Failed to send message to Guilded server: "+e);}
                    if(debug&&result!=null) getLogger().info("\n"+new JSONObject(result.toString()).toStringPretty());
                }
            }
        });
    }
}
