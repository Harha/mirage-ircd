package io.github.harha.ircd.server;

import io.github.harha.ircd.util.FileUtils;
import io.github.harha.ircd.util.Macros;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class IRCServer implements Runnable
{

    private InetAddress                   m_host;
    private InetAddress                   m_ip;
    private int                           m_port;
    private ServerSocket                  m_socket;
    private Map<String, List<Connection>> m_connections;
    private Map<String, Client>           m_clients;
    private Map<String, Server>           m_servers;
    private Map<String, Channel>          m_channels;
    private List<String>                  m_motd;
    private int                           m_deltaTime;

    public IRCServer(String ip, String port) throws NumberFormatException, IOException
    {
        /* Initialize all member variables and objects */
        m_host = InetAddress.getLocalHost();
        m_ip = InetAddress.getByName(ip);
        m_port = Integer.parseInt(port);
        m_socket = new ServerSocket(m_port, 1000, m_ip);
        m_connections = Collections.synchronizedMap(new ConcurrentHashMap<String, List<Connection>>());
        m_clients = Collections.synchronizedMap(new ConcurrentHashMap<String, Client>());
        m_servers = Collections.synchronizedMap(new ConcurrentHashMap<String, Server>());
        m_channels = Collections.synchronizedMap(new ConcurrentHashMap<String, Channel>());
        m_motd = new ArrayList<String>();
        m_deltaTime = 100;

        /* Configure the main server socket */
        m_socket.setSoTimeout(0);

        /* Load the message of the day */
        m_motd = FileUtils.loadTextFile("motd.txt");

        if (m_motd == null || m_motd.isEmpty())
        {
            m_motd = new ArrayList<String>();
            m_motd.add("No message of the day set on this server.");
        }
    }

    @Override
    public void run()
    {
        while (m_socket.isBound() && !m_socket.isClosed())
        {
            try
            {
                /* Wait for the next connection request... */
                Socket socket = m_socket.accept();

                /* Once a connection arrives, create input / output listeners for the socket */
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

                /* Create the initial connection object */
                Connection connection = new Connection(this, socket, input, output);
                connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Connection accepted. Looking up your hostname..."));

                /* Add it to the connections hashmap-list */
                String key = connection.getHost().getHostName();
                connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Found your hostname."));

                if (!m_connections.containsKey(key))
                {
                    List<Connection> connections = new ArrayList<Connection>();
                    connections.add(connection);
                    m_connections.put(key, connections);
                }
                else
                {
                    m_connections.get(key).add(connection);
                }

                /* Log the event to console */
                Macros.LOG("New incoming " + connection + ".");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void updateConnections()
    {
        Iterator<Entry<String, List<Connection>>> it_con_map = getConnections().entrySet().iterator();

        while (it_con_map.hasNext())
        {
            Entry<String, List<Connection>> entry_map = it_con_map.next();
            List<Connection> con_list = (ArrayList<Connection>) entry_map.getValue();
            Iterator<Connection> it_con_list = con_list.iterator();

            while (it_con_list.hasNext())
            {
                Connection c = it_con_list.next();

                /* First check, if the socket was closed for some reason */
                if (c.getSocket().isClosed() || c.getOutput().checkError())
                {
                    c.setState(ConnectionState.DISCONNECTED);
                }

                /* Handle unidentified connections */
                if (c.getState() == ConnectionState.UNIDENTIFIED)
                {
                    c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Checking ident..."));
                    c.updateUnidentified();
                }

                /* Handle identified client connections */
                if (c.getState() == ConnectionState.IDENTIFIED_AS_CLIENT)
                {
                    /* Add the connection as a client and inform them for the success */
                    c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Found your ident, identified as a client."));
                    c.setState(ConnectionState.CONNECTED_AS_CLIENT);
                    Client client = new Client(c);
                    c.setParentClient(client);
                    m_clients.put(c.getNick(), client);

                    /* Send MOTD to the client */
                    c.sendMsg(new ServMessage(this, CMDs.RPL_MOTDSTART, c.getNick(), "- Message of the day -"));
                    List<String> MOTD = getMotd();
                    for (String s : MOTD)
                    {
                        c.sendMsg(new ServMessage(this, CMDs.RPL_MOTD, c.getNick(), "- " + s));
                    }
                    c.sendMsgAndFlush(new ServMessage(this, CMDs.RPL_ENDOFMOTD, c.getNick(), "End of /MOTD command."));
                }

                /* Handle identified server connections */
                if (c.getState() == ConnectionState.IDENTIFIED_AS_SERVER)
                {
                    /* Add the connection as a client and inform them for the success */
                    c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getServer().getServerName(), "*** Found your ident, identified as a server."));
                    c.setState(ConnectionState.CONNECTED_AS_SERVER);
                    Server server = new Server(c);
                    c.setParentServer(server);
                    m_servers.put(c.getNick(), server);

                    /* Send MOTD to the client */
                    c.sendMsg(new ServMessage(this, CMDs.RPL_MOTDSTART, c.getServer().getServerName(), "- Message of the day -"));

                    for (String s : m_motd)
                    {
                        c.sendMsg(new ServMessage(this, CMDs.RPL_MOTD, c.getServer().getServerName(), "- " + s));
                    }

                    c.sendMsgAndFlush(new ServMessage(this, CMDs.RPL_ENDOFMOTD, c.getServer().getServerName(), "End of /MOTD command."));
                }

                /* Handle connected client connections */
                if (c.getState() == ConnectionState.CONNECTED_AS_CLIENT)
                {
                    Client client = c.getParentClient();

                    /* Send a PING request between intervals */
                    if (client.getPingTimer() <= 0)
                    {
                        c.sendMsgAndFlush(new ServMessage("", "PING", c.getNick()));
                    }

                    client.updateIdentifiedClient();

                    /* Disconnect if it didn't respond to the PING request */
                    if (client.getPingTimer() <= -100)
                    {
                        c.setState(ConnectionState.DISCONNECTED);
                    }

                    client.setPingTimer(client.getPingTimer() - 1);
                }

                /* Handle connected server connections */
                if (c.getState() == ConnectionState.CONNECTED_AS_SERVER)
                {
                    c.getParentServer().updateIdentifiedServer();
                }

                /* Unregister the connection if it was closed */
                if (c.getState() == ConnectionState.DISCONNECTED)
                {
                    /* Is it a client? */
                    if (c.getParentClient() != null)
                    {
                        Client client = c.getParentClient();

                        client.quitChannels("For an unknown reason.");
                    }

                    /* Is it a server? Should't be both. */
                    if (c.getParentServer() != null)
                    {
                    }

                    c.kill();
                    it_con_list.remove();
                    Macros.LOG(c + " Has disconnected.");
                }
            }

            /* Remove the key from connection list map if the list is empty */
            if (con_list.isEmpty())
            {
                it_con_map.remove();
            }
        }
    }

    public void setDeltaTime(int deltaTime)
    {
        m_deltaTime = deltaTime;
    }

    public InetAddress getHost()
    {
        return m_host;
    }

    public InetAddress getIp()
    {
        return m_ip;
    }

    public int getPort()
    {
        return m_port;
    }

    public ServerSocket getSocket()
    {
        return m_socket;
    }

    public synchronized Map<String, List<Connection>> getConnections()
    {
        return m_connections;
    }

    public synchronized List<Connection> getConnection(String key)
    {
        return m_connections.get(key);
    }

    public synchronized Map<String, Client> getClients()
    {
        return m_clients;
    }

    public synchronized Client getClient(String key)
    {
        return m_clients.get(key);
    }

    public synchronized Map<String, Server> getServers()
    {
        return m_servers;
    }

    public synchronized Server getServer(String key)
    {
        return m_servers.get(key);
    }

    public synchronized Map<String, Channel> getChannels()
    {
        return m_channels;
    }

    public synchronized Channel getChannel(String key)
    {
        return m_channels.get(key);
    }

    public List<String> getMotd()
    {
        return m_motd;
    }

    public int getDeltaTime()
    {
        return m_deltaTime;
    }

}