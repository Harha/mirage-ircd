package io.github.harha.ircd.server;

import io.github.harha.ircd.util.Macros;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Server
{

    private Connection m_connection;

    public Server(Connection connection)
    {
        m_connection = connection;
    }

    public void updateIdentifiedServer()
    {
        /* Read input from client */
        List<String> input_data = new ArrayList<String>();

        try
        {
            while (m_connection.getInput().ready())
            {
                input_data.add(m_connection.getInput().readLine());
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
        Macros.LOG("Input from (%s): %s", m_connection.getHost().getHostName(), input_data);

        /* Parse input and handle it appropriately */
        for (String l : input_data)
        {
            CliMessage message = new CliMessage(l);

            switch (message.getCommand())
            {
            }
        }
    }

    public Connection getConnection()
    {
        return m_connection;
    }

}
