package io.github.harha.ircd.util;

@Deprecated
public class Macros
{

    public static void LOG(String msg, Object... args)
    {
        System.out.println("LOG: " + String.format(msg, args));
    }

    public static void ERR(String msg, Object... args)
    {
        System.out.println("ERR: " + String.format(msg, args));
    }

}
