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
import static vip.floatationdevice.mc2gforward.I18nUtil.translate;

import java.io.*;
import java.util.Properties;

public final class MC2GForward extends JavaPlugin implements Listener
{
    public static MC2GForward instance;
    final static String cfgPath="."+File.separator+"plugins"+File.separator+"MC2GForward"+File.separator;
    static String lang,token,server,channel;
    static Boolean mc2gRunning=false;
    G4JClient g4JClient;
    BindManager bindMgr;
    Boolean forwardJoinLeaveEvents=true;
    Boolean debug=false;

    boolean isNull(String... s)
    {
        for(String a:s)
            if(a==null || a.isEmpty())
                return true;
        return false;
    }

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
                bw.write("language=en_US\ntoken=\nserver=\nchannel=\nforwardJoinLeaveEvents=true\ndebug=false\n");
                bw.flush();
                bw.close();
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            BufferedReader cfg=new BufferedReader(new FileReader(file));
            Properties p=new Properties();
            p.load(cfg);
            lang=p.getProperty("language");
            token=p.getProperty("token");
            server=p.getProperty("server");
            channel=p.getProperty("channel");
            if(isNull(lang,token,server,channel,p.getProperty("forwardJoinLeaveEvents"),p.getProperty("debug"))
                    || !lang.matches("^[a-z]{2}_[A-Z]{2}$") || channel.length()!=36 || server.length()!=8
                    || !p.getProperty("forwardJoinLeaveEvents").toLowerCase().matches("^(true|false)$")
                    || !p.getProperty("debug").toLowerCase().matches("^(true|false)$"))
            {
                getLogger().severe(translate("invalid-config"));
                g4JClient=null;
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            forwardJoinLeaveEvents=Boolean.parseBoolean(p.getProperty("forwardJoinLeaveEvents"));
            debug=Boolean.parseBoolean(p.getProperty("debug"));
            I18nUtil.setLanguage(lang);
            g4JClient=new G4JClient(token);
            Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable()
            {// fuck lambdas all my codes are lambda-free
                @Override
                public void run()
                {
                    bindMgr=new BindManager();
                    Bukkit.getPluginManager().registerEvents(bindMgr, instance);
                    getCommand("mc2g").setExecutor(bindMgr);
                    mc2gRunning=true;
                }
            });
            sendGuildedMsg(translate("mc2g-started"));
        }catch (Throwable e)
        {
            getLogger().severe("Failed to initialize plugin!");
            g4JClient=null;
            bindMgr=null;
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override
    public void onDisable()
    {
        mc2gRunning=false;
        if(bindMgr!=null)
        {
            bindMgr.ws.close();
            bindMgr=null;
        }
        if(g4JClient!=null)
        {
            ChatMessage result=null;
            try {result=g4JClient.createChannelMessage(channel, translate("mc2g-stopped"),null,null);}
            catch (Exception e) {getLogger().severe(translate("msg-send-failed").replace("%EXCEPTION%",e.toString()));}
            g4JClient=null;
            if(debug&&result!=null)
                getLogger().info("\n"+new JSONObject(result.toString()).toStringPretty());
        }
    }
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event)
    {
        Bukkit.getScheduler().runTaskAsynchronously(this,new Runnable()
        {
            @Override
            public void run()
            {
                String message=event.getMessage();
                if(!message.startsWith("/"))
                    sendGuildedMsg("<"+event.getPlayer().getName()+"> "+message);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        if(forwardJoinLeaveEvents)
            sendGuildedMsg(translate("player-connected").replace("%PLAYER%",event.getPlayer().getName()));
    }

    @EventHandler
    public void onUnusualLeave(PlayerKickEvent event)
    {
        if(forwardJoinLeaveEvents)
            sendGuildedMsg(translate("player-disconnected-unusual").replace("%PLAYER%",event.getPlayer().getName()).replace("%REASON%",event.getReason()));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event)
    {
        if(forwardJoinLeaveEvents)
            sendGuildedMsg(translate("player-disconnected").replace("%PLAYER%",event.getPlayer().getName()));
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
                    catch(Exception e) {getLogger().severe(translate("msg-send-failed").replace("%EXCEPTION%",e.toString()));}
                    if(debug&&result!=null) getLogger().info("\n"+new JSONObject(result.toString()).toStringPretty());
                }
            }
        });
    }
}
