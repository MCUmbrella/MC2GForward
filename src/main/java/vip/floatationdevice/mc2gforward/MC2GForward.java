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

import java.io.*;
import java.util.Properties;

public final class MC2GForward extends JavaPlugin implements Listener
{
    final static String cfgPath="."+File.separator+"plugins"+File.separator+"MC2GForward"+File.separator;
    String token,channel;
    G4JClient g4JClient;
    Boolean forwardJoinLeaveEvents=true;
    Boolean debug=false;

    @Override
    public void onEnable()
    {
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
            /*
            TODO:
             add a command (like '/mc2g bind <verification code>') on MC side and Guilded side
             to verify Guilded users' MC player name. once a Guilded account has been bound to
             a Minecraft player, he/she will have ability to forward messages to Minecraft side
             */
            //getLogger().info("Connecting to Guilded server");
            g4JClient=new G4JClient(token);
            //g4JClient.connect();
            sendGuildedMsg("--- MC2GForward started ---");
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
            String result=g4JClient.createChannelMessage(channel,"--- MC2GForward stopped ---");
            //g4JClient.close();
            g4JClient=null;
            if(debug)getLogger().info("\n"+new JSONObject(result).toStringPretty());
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
                    sendGuildedMsg("<"+event.getPlayer().getName()+"> "+message.replace("\\", "\\\\").replace("\"", "\\\""));
                }
            }
        }.start();
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("[+] "+event.getPlayer().getName()+" connected");
    }
    @EventHandler
    public void onUnusualLeave(PlayerKickEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("("+event.getPlayer().getName()+" lost connection: "+event.getReason()+")");
    }
    @EventHandler
    public void onLeave(PlayerQuitEvent event)
    {
        if(forwardJoinLeaveEvents)sendGuildedMsg("[-] "+event.getPlayer().getName()+" disconnected");
    }
    public void sendGuildedMsg(String msg)
    {
        new Thread()
        {
            String result="{}";
            @Override
            public void run()
            {
                if(g4JClient!=null)
                {
                    result=g4JClient.createChannelMessage(channel,msg);
                    if(debug)getLogger().info("\n"+new JSONObject(result).toStringPretty());
                }
            }
        }.start();
    }
}
