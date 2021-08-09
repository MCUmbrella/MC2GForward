package vip.floatationdevice.mc2gforward;

import cn.hutool.json.JSONObject;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vip.floatationdevice.guilded4j.G4JClient;

import java.io.*;
import java.util.Properties;

public final class MC2GForward extends JavaPlugin implements Listener
{
    final static String cfgPath="."+ File.separator+"plugins"+File.separator+"MC2GForward"+ File.separator+"config.properties";
    String token,channel;
    static G4JClient g4JClient;
    Boolean forwardJoinLeaveEvents=true;
    Boolean debug=false;

    @Override
    public void onEnable()
    {
        Bukkit.getPluginManager().registerEvents(this, this);
        try
        {
            new File(new File(cfgPath).getParent()).mkdirs();
            File file=new File(cfgPath);
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
            BufferedReader cfg = new BufferedReader(new FileReader(file));
            Properties p = new Properties();
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
            getLogger().info("Connecting to Guilded server");
            g4JClient=new G4JClient(token);
            //g4JClient.connect();
            g4JClient.createChannelMessage(channel,"*** MC2GForawrd started ***");
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
        if(g4JClient!=null&&!g4JClient.isClosed())
        {
            String result=g4JClient.createChannelMessage(channel,"*** MC2GForawrd stopped ***");
            //g4JClient.close();
            g4JClient=null;
            if(debug) getLogger().info("\n"+new JSONObject(result).toStringPretty());
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
                    String result=g4JClient.createChannelMessage(channel,"<"+event.getPlayer().getName()+"> "+new String(message.replace("\\","\\\\").replace("\"","\\\"")));
                    if(debug)getLogger().info("\n"+new JSONObject(result).toStringPretty());
                }
            }
        }.start();
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        if(forwardJoinLeaveEvents)
            new Thread()
            {
                @Override
                public void run()
                {
                    g4JClient.createChannelMessage(channel,"[+] "+event.getPlayer().getName()+" connected");
                }
            }.start();
    }
    @EventHandler
    public void onLeave(PlayerQuitEvent event)
    {
        if(forwardJoinLeaveEvents)
            new Thread()
            {
                @Override
                public void run()
                {
                    g4JClient.createChannelMessage(channel,"[-] "+event.getPlayer().getName()+" disconnected");
                }
            }.start();
    }
}
