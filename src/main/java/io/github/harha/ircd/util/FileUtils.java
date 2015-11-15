package io.github.harha.ircd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FileUtils
{

    public static List<String> loadTextFile(URL resource, boolean trimws)
    {
        File file = null;

        try
        {
            file = new File(resource.toURI());
        } catch (URISyntaxException e1)
        {
            e1.printStackTrace();
        }

        if (file == null || !file.exists() || file.isDirectory())
        {
            Macros.ERR("Input file <%s> either doesn't exist or is a directory.", resource.getPath());
            return null;
        }

        List<String> result = new ArrayList<String>();
        InputStream input;
        BufferedReader reader;

        try
        {
            input = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(input));
            String line;

            while ((line = reader.readLine()) != null)
            {
                line = trimws ? line.trim() : line;

                if (line.isEmpty())
                    continue;

                result.add(line);
            }

            reader.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        }

        Macros.LOG("File <%s> has been loaded successfully.", resource.getPath());

        return result;
    }

}
