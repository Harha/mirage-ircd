package io.github.harha.ircd.server;

import java.util.List;

public class UserInfo
{

    private String m_username;
    private String m_hostname;
    private String m_servername;
    private String m_realname;

    public UserInfo(String username, String hostname, String servername, String realname)
    {
        m_username = username;
        m_hostname = hostname;
        m_servername = servername;
        m_realname = realname;
    }

    public UserInfo(List<String> parameters)
    {
        if (parameters.size() >= 1)
        {
            m_username = parameters.get(0);
        }
        else
        {
            m_username = "";
        }

        if (parameters.size() >= 2)
        {
            m_hostname = parameters.get(1);
        }
        else
        {
            m_hostname = "";
        }

        if (parameters.size() >= 3)
        {
            m_servername = parameters.get(2);
        }
        else
        {
            m_servername = "";
        }

        if (parameters.size() >= 4)
        {
            m_realname = parameters.get(3);
        }
        else
        {
            m_realname = "";
        }
    }

    public String getUserName()
    {
        return m_username;
    }

    public String getHostName()
    {
        return m_hostname;
    }

    public String getServerName()
    {
        return m_servername;
    }

    public String getRealName()
    {
        return m_realname;
    }

}
