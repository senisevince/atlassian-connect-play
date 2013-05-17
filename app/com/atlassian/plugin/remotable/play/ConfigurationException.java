package com.atlassian.plugin.remotable.play;

public final class ConfigurationException extends IllegalStateException
{
    public ConfigurationException()
    {
        super();
    }

    public ConfigurationException(String s)
    {
        super(s);
    }

    public ConfigurationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ConfigurationException(Throwable cause)
    {
        super(cause);
    }
}
