package io.github.harha.ircd.server;

import io.github.harha.ircd.util.Macros;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Connection
{

    private IRCServer       m_ircserver;
    private Socket          m_socket;
    private BufferedReader  m_input;
    private PrintWriter     m_output;
    private String          m_nick;
    private UserInfo        m_user;
    private ServerInfo      m_server;
    private String          m_pass;
    private ConnectionState m_state;
    private Client          m_parent_client;
    private Server          m_parent_server;

    public Connection(IRCServer ircserver, Socket socket, BufferedReader input, PrintWriter output)
    {
        /* Initialize all member variables and objects */
        m_ircserver = ircserver;
        m_socket = socket;
        m_input = input;
        m_output = output;
        m_nick = "*";
        m_user = null;
        m_server = null;
        m_pass = null;
        m_state = ConnectionState.UNIDENTIFIED;
        m_parent_client = null;
        m_parent_server = null;
    }

    @Override
    public String toString()
    {
        return String.format("Connection[%s, %s, %s, %s, %s, %s]", getHost().getHostName(), getIp(), m_nick, (m_user == null) ? "null" : m_user.getUserName(), m_server, m_pass);
    }

    public void updateUnidentified()
    {
        /* Read input from client */
        List<String> input_data = new ArrayList<String>();

        try
        {
            while (m_input.ready())
            {
                input_data.add(m_input.readLine());
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        /* Return if input data was empty */
        if (input_data.isEmpty())
        {
            return;
        }

        /* Log the input to console */
        Macros.LOG("Input from (%s): %s", getHost().getHostName(), input_data);

        /* Parse input and handle it appropriately */
        for (String l : input_data)
        {
            CliMessage message = new CliMessage(l);

            switch (message.getCommand())
            {
                case "NICK":
                    if (message.getParameters().size() > 0)
                    {
                        m_nick = message.getParameter(0);
                    }
                    break;
                case "USER":
                    if (message.getParameters().size() > 0)
                    {
                        m_user = new UserInfo(message.getParameters());
                    }
                    break;
                case "SERVER":
                    if (message.getParameters().size() > 0)
                    {
                        m_server = new ServerInfo(message.getParameters());
                    }
                    break;
                case "PASS":
                    if (message.getParameters().size() > 0)
                    {
                        m_pass = message.getParameter(0);
                    }
                    break;
            }
        }

        /* Have we received enough info in order to try client identification? */
        if (m_nick != "*")
        {
            /* Nick length must be in-between 1 and 9 characters */
            if (m_nick.length() < 1 || m_nick.length() > 9)
            {
                sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_nick, "*** NICK length must be in-between 1 and 9 characters. Disconnecting."));
                m_state = ConnectionState.DISCONNECTED;
                return;
            }

            /* Nick must not exist on the server */
            if (m_ircserver.getClients().containsKey(m_nick))
            {
                sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_nick, "*** NICK already exists on this server. Disconnecting."));
                m_state = ConnectionState.DISCONNECTED;
                return;
            }

            /* Accept the connection as identified client */
            m_state = ConnectionState.IDENTIFIED_AS_CLIENT;
        }

        /* Have we received enough info in order to try server identification? */
        if (m_server != null)
        {
            /* Server name length must be in-between 5 and 32 characters */
            if (m_server.getServerName().length() < 5 || m_server.getServerName().length() > 32)
            {
                sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_server.getServerName(), "*** SERVER <servername> length must be in-between 5 and 32 characters. Disconnecting."));
                m_state = ConnectionState.DISCONNECTED;
                return;
            }

            /* Server name must not exist on the server */
            if (m_ircserver.getServers().containsKey(m_server.getServerName()))
            {
                sendMsgAndFlush(new ServMessage(m_ircserver, "NOTICE", m_server.getServerName(), "*** SERVER <servername> already exists on this server. Disconnecting."));
                m_state = ConnectionState.DISCONNECTED;
                return;
            }

            /* Accept the connection as identified server */
            m_state = ConnectionState.IDENTIFIED_AS_SERVER;
        }
    }

    public void sendMsg(ServMessage message)
    {
        m_output.print(message);
    }

    public void sendMsgAndFlush(ServMessage message)
    {
        m_output.print(message);
        m_output.flush();
    }

    public void flush()
    {
        m_output.flush();
    }

    public void kill()
    {
        try
        {
            m_socket.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        if (m_server == null)
        {
            m_ircserver.getClients().remove(m_nick);
        }
        else
        {
            m_ircserver.getServers().remove(m_server.getServerName());
        }
    }

    public void setState(ConnectionState state)
    {
        m_state = state;
    }

    public void setParentClient(Client client)
    {
        m_parent_client = client;
    }

    public void setParentServer(Server server)
    {
        m_parent_server = server;
    }

    public IRCServer getIRCServer()
    {
        return m_ircserver;
    }

    public InetAddress getHost()
    {
        return m_socket.getInetAddress();
    }

    public String getIp()
    {
        return getHost().getHostAddress();
    }

    public Socket getSocket()
    {
        return m_socket;
    }

    public BufferedReader getInput()
    {
        return m_input;
    }

    public PrintWriter getOutput()
    {
        return m_output;
    }

    public String getNick()
    {
        return m_nick;
    }

    public UserInfo getUser()
    {
        return m_user;
    }

    public ServerInfo getServer()
    {
        return m_server;
    }

    public String getPass()
    {
        return m_pass;
    }

    public ConnectionState getState()
    {
        return m_state;
    }

    public Client getParentClient()
    {
        return m_parent_client;
    }

    public Server getParentServer()
    {
        return m_parent_server;
    }

}
