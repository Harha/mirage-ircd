package io.github.harha.ircd.server;

import java.util.List;

public class ServerInfo
{

    private String m_servername;
    private int    m_hopcount;
    private String m_info;

    public ServerInfo(String servername, String hopcount, String info)
    {
        m_servername = servername;
        m_hopcount = Integer.parseInt(hopcount);
        m_info = info;
    }

    public ServerInfo(List<String> parameters)
    {
        if (parameters.size() >= 1)
        {
            m_servername = parameters.get(0);
        }
        else
        {
            m_servername = "";
        }

        if (parameters.size() >= 2)
        {
            m_hopcount = Integer.parseInt(parameters.get(1));
        }
        else
        {
            m_hopcount = 0;
        }

        if (parameters.size() >= 3)
        {
            m_info = parameters.get(2);
        }
        else
        {
            m_info = "";
        }
    }

    public String getServerName()
    {
        return m_servername;
    }

    public int getHopCount()
    {
        return m_hopcount;
    }

    public String getInfo()
    {
        return m_info;
    }

}
