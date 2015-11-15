package io.github.harha.ircd.server;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import io.github.harha.ircd.util.CaseIMap;
import io.github.harha.ircd.util.Consts;
import io.github.harha.ircd.util.FileUtils;
import io.github.harha.ircd.util.Macros;
import io.github.harha.ircd.util.VarMap;
import org.ini4j.Ini;
import org.ini4j.IniPreferences;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.prefs.Preferences;

public class IRCServer extends VarMap implements Runnable {

    private InetAddress m_host;
    private InetAddress m_ip;
    private int m_port;
    private ServerSocket m_socket;
    private Map<String, List<Connection>> m_connections;
    private Map<String, Client> m_clients;
    private Map<String, Server> m_servers;
    private Map<String, Channel> m_channels;

    private List<String> m_motd;
    private int m_deltaTime;

    public IRCServer() throws IOException {
        m_connections = Collections.synchronizedMap(new CaseIMap<>());
        m_clients = Collections.synchronizedMap(new CaseIMap<>());
        m_servers = Collections.synchronizedMap(new CaseIMap<>());
        m_channels = Collections.synchronizedMap(new CaseIMap<>());
        m_deltaTime = 0;
        initializeProperties(this);
        m_motd = getMOTD();
    }

    public IRCServer(String ip, String port) throws NumberFormatException, IOException {
        this();

        /* Initialize all member variables and objects */
        m_host = InetAddress.getLocalHost();
        m_ip = InetAddress.getByName(ip);
        m_port = Integer.parseInt(port);
        m_socket = new ServerSocket(m_port, 1000, m_ip);

        /* Configure the main server socket */
        m_socket.setSoTimeout(0);
    }

    protected List<String> getMOTD() throws IOException {
        List<String> motd = new ArrayList<>();
        System.out.println(getString("sMOTD"));
        try (InputStream is = this.getClass().getResourceAsStream(getString("sMOTD"));
             Scanner scanner = new Scanner(is)) {
            /* Load the message of the day */
            while (scanner.hasNext()) {
                motd.add(scanner.nextLine());
            }
        }
        if (motd.isEmpty()) {
            motd.add("No message of the day set on this server.");
        }

        return motd;
    }

    protected void initializeProperties(VarMap varMap) throws IOException {
    /* Load the main .ini file */
        IniPreferences ini = new IniPreferences(this.getClass().getResourceAsStream("/main.ini"));
        Preferences server = ini.node("server");
        Preferences client = ini.node("client");

        putString("sName", server.get("sName", "mirage-ircd"));
        putString("sMOTD", server.get("sMOTD", "/motd.txt"));
        putInteger("sMaxConns", server.getInt("sMaxConns", 1028));
        putInteger("cMaxConns", client.getInt("cMaxConns", 10));
        putInteger("cPingTime", client.getInt("cPingTime", 600));
        putInteger("cIdentTime", client.getInt("cIdentTime", 300));

        putString("sCreationDate", new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));
    }

    @Override
    public void run() {
        while (m_socket.isBound() && !m_socket.isClosed()) {
            try {
                /* Wait for the next connection request... */
                Socket socket = m_socket.accept();

                /* Once a connection arrives, create input / output listeners for the socket */
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

                /* Create the initial connection object */
                Connection connection = new Connection(this, socket, input, output);
                connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Connection accepted. Looking up your hostname..."));

                if (m_connections.size() < getInteger("sMaxConns")) {
                    /* Add it to the connections hashmap-list */
                    String key = connection.getHost().getHostName();
                    connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Found your hostname."));

                    if (!m_connections.containsKey(key)) {
                        List<Connection> connections = new ArrayList<Connection>();
                        connections.add(connection);
                        m_connections.put(key, connections);
                    } else {
                        m_connections.get(key).add(connection);
                    }

                    Macros.LOG("New incoming " + connection + ".");

                    if (m_connections.get(key).size() > getInteger("cMaxConns")) {
                        connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Sorry, but your ip exceeds max connections per ip."));
                        connection.setState(ConnState.DISCONNECTED);
                        Macros.LOG("Too many connections from " + connection + ", disconnecting...");
                    }

                } else {
                    /* Server cannot accept any more connections, close the socket. */
                    connection.sendMsgAndFlush(new ServMessage(this, "NOTICE", connection.getNick(), "*** Server connection limit reached. " + m_connections.size() + "/" + getInteger("sMaxConns")));
                    connection.kill();

                    Macros.LOG("Too many connections on the server, " + connection + " disconnected.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateConnections() {
        Iterator<Entry<String, List<Connection>>> it_con_map = getConnections().entrySet().iterator();

        while (it_con_map.hasNext()) {
            Entry<String, List<Connection>> entry_map = it_con_map.next();
            List<Connection> con_list = (ArrayList<Connection>) entry_map.getValue();
            Iterator<Connection> it_con_list = con_list.iterator();

            while (it_con_list.hasNext()) {
                Connection c = it_con_list.next();

                /* First check, if the socket was closed for some reason */
                if (c.getSocket().isClosed() || c.getOutput().checkError()) {
                    c.setState(ConnState.DISCONNECTED);
                }

                /* Handle unidentified connections */
                if (c.getState() == ConnState.UNIDENTIFIED) {
                    if (c.getIdentTime() == -1)
                        c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Checking ident..."));

                    c.updateUnidentified();

                    /* Wait for x seconds before disconnecting the connection */
                    if (c.getIdentTime() > getInteger("cIdentTime")) {
                        c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Failed to identify the connection, disconnected."));
                        c.setState(ConnState.DISCONNECTED);
                    }

                    c.setIdentTime(c.getIdentTime() + 1);
                }

                /* Handle identified client connections */
                if (c.getState() == ConnState.IDENTIFIED_AS_CLIENT) {
                    /* Add the connection as a client and inform them for the success */
                    c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getNick(), "*** Found your ident, identified as a client."));
                    c.setState(ConnState.CONNECTED_AS_CLIENT);
                    c.getUser().setHostName(c.getHost().getHostName());
                    Client client = new Client(c);
                    c.setParentClient(client);
                    m_clients.put(c.getNick(), client);

                    /* Send some info about the server */
                    c.sendMsg(new ServMessage(this, "001", c.getNick(), "Welcome to the " + getString("sName") + " IRC network, " + c.getNick()));
                    c.sendMsg(new ServMessage(this, "002", c.getNick(), "Your host is " + getHost().getHostName() + ", running version mirage-ircd-" + Consts.VERSION));
                    c.sendMsg(new ServMessage(this, "003", c.getNick(), "This server was created on " + getString("sCreationDate")));
                    c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERCLIENT, c.getNick(), "There are " + m_connections.size() + " users and 0 invisible on 1 server."));
                    c.sendMsg(new ServMessage(this, CMDs.RPL_LUSEROP, c.getNick(), "0", "IRC Operators online."));
                    c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERUNKNOWN, c.getNick(), "0", "Unknown connections."));
                    c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERCHANNELS, c.getNick(), Integer.toString(m_channels.size()), "Channels formed."));
                    c.sendMsg(new ServMessage(this, CMDs.RPL_LUSERME, c.getNick(), "I have " + m_clients.size() + " clients and " + m_servers.size() + " servers."));

                    /* Send MOTD to the client */
                    c.sendMsg(new ServMessage(this, CMDs.RPL_MOTDSTART, c.getNick(), "- Message of the day -"));

                    for (String s : m_motd) {
                        c.sendMsg(new ServMessage(this, CMDs.RPL_MOTD, c.getNick(), "- " + s));
                    }

                    c.sendMsgAndFlush(new ServMessage(this, CMDs.RPL_ENDOFMOTD, c.getNick(), "End of /MOTD command."));
                }

                /* Handle identified server connections */
                else if (c.getState() == ConnState.IDENTIFIED_AS_SERVER) {
                    /* Add the connection as a server and inform them for the success */
                    c.sendMsgAndFlush(new ServMessage(this, "NOTICE", c.getServer().getName(), "*** Found your ident, identified as a server."));
                    c.setState(ConnState.CONNECTED_AS_SERVER);
                    c.getUser().setHostName(c.getHost().getHostName());
                    Server server = new Server(c);
                    c.setParentServer(server);
                    m_servers.put(c.getServer().getName(), server);

                    /* Send MOTD to the server */
                    c.sendMsg(new ServMessage(this, CMDs.RPL_MOTDSTART, c.getServer().getName(), "- Message of the day -"));

                    for (String s : m_motd) {
                        c.sendMsg(new ServMessage(this, CMDs.RPL_MOTD, c.getServer().getName(), "- " + s));
                    }

                    c.sendMsgAndFlush(new ServMessage(this, CMDs.RPL_ENDOFMOTD, c.getServer().getName(), "End of /MOTD command."));
                }

                /* Handle connected client connections */
                if (c.getState() == ConnState.CONNECTED_AS_CLIENT) {
                    Client client = c.getParentClient();

                    /* Send a PING request between intervals */
                    int pingtime = getInteger("cPingTime");
                    if (client.getPingTimer() >= pingtime && client.getPingTimer() % (pingtime / 10) == 0) {
                        c.sendMsgAndFlush(new ServMessage("", "PING", c.getNick()));
                    }

                    client.updateIdentifiedClient();

                    /* Disconnect if it didn't respond to the PING request given enough time */
                    if (client.getPingTimer() > (int) (pingtime * 1.5)) {
                        c.setState(ConnState.DISCONNECTED);
                    }

                    client.setPingTimer(client.getPingTimer() + 1);
                }

                /* Handle connected server connections */
                else if (c.getState() == ConnState.CONNECTED_AS_SERVER) {
                    c.getParentServer().updateIdentifiedServer();
                }

                /* Unregister the connection if it was closed */
                if (c.getState() == ConnState.DISCONNECTED) {
                    Client client = c.getParentClient();
                    Server server = c.getParentServer();

                    /* Is it a client? */
                    if (client != null) {
                        client.quitChannels("Connection reset by peer...");
                        m_clients.remove(c.getNick());
                    }

                    /* Is it a server? Should't be both. */
                    if (server != null) {
                        m_servers.remove(c.getServer().getName());
                    }

                    c.kill();
                    it_con_list.remove();
                    Macros.LOG(c + " Has disconnected.");
                }
            }

            /* Remove the key from connection list map if the list is empty */
            if (con_list.isEmpty()) {
                it_con_map.remove();
            }
        }
    }

    public void updateChannels() {
        Iterator<Entry<String, Channel>> it_chan_map = m_channels.entrySet().iterator();

        while (it_chan_map.hasNext()) {
            Entry<String, Channel> e = it_chan_map.next();
            Channel c = (Channel) e.getValue();

            /* Delete empty channels */
            if (c.getState() == ChanState.EMPTY) {
                it_chan_map.remove();
            }
        }
    }

    public void addClient(Client client) {
        m_clients.put(client.getConnection().getNick().toLowerCase(), client);
    }

    public void setDeltaTime(int deltaTime) {
        m_deltaTime = deltaTime;
    }

    public InetAddress getHost() {
        return m_host;
    }

    public InetAddress getIp() {
        return m_ip;
    }

    public int getPort() {
        return m_port;
    }

    public ServerSocket getSocket() {
        return m_socket;
    }

    public synchronized Map<String, List<Connection>> getConnections() {
        return m_connections;
    }

    public synchronized List<Connection> getConnection(String key) {
        return m_connections.get(key);
    }

    public synchronized Map<String, Client> getClients() {
        return m_clients;
    }

    public synchronized Client getClient(String key) {
        return m_clients.get(key);
    }

    public synchronized Map<String, Server> getServers() {
        return m_servers;
    }

    public synchronized Server getServer(String key) {
        return m_servers.get(key);
    }

    public synchronized Map<String, Channel> getChannels() {
        return m_channels;
    }

    public synchronized Channel getChannel(String key) {
        return m_channels.get(key);
    }

    public List<String> getMotd() {
        return m_motd;
    }

    public int getDeltaTime() {
        return m_deltaTime;
    }

}
