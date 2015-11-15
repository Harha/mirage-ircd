package io.github.harha.ircd.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class FileUtils
{

    public static List<String> loadTextFile(String filepath, boolean trimws)
    {
        File file = new File(filepath);

        if (!file.exists() || file.isDirectory())
        {
            Macros.ERR("Input file <%s> either doesn't exist or is a directory.", filepath);
            return null;
        }

        List<String> result = new ArrayList<String>();
        FileInputStream input;
        BufferedReader reader;

        try
        {
            input = new FileInputStream(filepath);
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

        Macros.LOG("File <%s> has been loaded successfully.", filepath);

        return result;
    }

}
