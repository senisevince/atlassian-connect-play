package com.atlassian.connect.play.java;

import com.atlassian.connect.play.java.oauth.OAuthSignatureCalculator;
import com.atlassian.connect.play.java.util.Environment;
import com.atlassian.fugue.Option;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import play.Play;
import play.libs.WS;
import play.mvc.Http;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static com.atlassian.connect.play.java.util.Environment.OAUTH_LOCAL_PRIVATE_KEY;
import static com.atlassian.connect.play.java.util.Environment.OAUTH_LOCAL_PUBLIC_KEY;
import static com.atlassian.connect.play.java.util.Utils.LOGGER;
import static com.atlassian.fugue.Option.option;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Suppliers.memoize;
import static java.lang.String.format;
import static play.mvc.Http.Context.Implicit.request;

public final class AC
{
    private static final Long DEFAULT_TIMEOUT = TimeUnit.SECONDS.convert(5, TimeUnit.MILLISECONDS);

    public static final String USER_ID_QUERY_PARAMETER = "user_id";

    public static String PLUGIN_KEY = Play.application().configuration().getString("ac.key", Play.isDev() ? "_add-on_key" : null);
    public static String PLUGIN_NAME = Option.option(Play.application().configuration().getString("ac.name", Play.isDev() ? "Atlassian Connect Play Add-on" : null)).getOrElse(PLUGIN_KEY);

    // the base URL
    public static BaseUrl baseUrl;

    public static final Supplier<String> publicKey = memoize(new Supplier<String>()
    {
        @Override
        public String get()
        {
            return getKey(OAUTH_LOCAL_PUBLIC_KEY, "public-key.pem");
        }
    });

    public static final Supplier<String> privateKey = memoize(new Supplier<String>()
    {
        @Override
        public String get()
        {
            return getKey(OAUTH_LOCAL_PRIVATE_KEY, "private-key.pem");
        }
    });

    private static String getKey(String envKey, String fileName)
    {
        String key = Environment.getOptionalEnv(envKey, null);
        if (key == null && Play.isDev())
        {
            try
            {
                key = getFileContent(fileName);
            }
            catch (IOException e)
            {
                LOGGER.warn(format("Could not read '%s' file.", fileName), e);
            }
        }
        if (key != null)
        {
            if (Play.isDev())
            {
                LOGGER.debug(format("Loaded key '%s' as:\n%s", envKey, key));
            }
            return key;
        }
        throw new IllegalStateException(format("Could NOT find %s for OAuth!", envKey));
    }

    private static String getFileContent(String pathname) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        Files.copy(new File(pathname), Charset.forName("UTF-8"), sb);
        return sb.toString();
    }

    public static Option<String> getUser()
    {
        return option(request().getQueryString(USER_ID_QUERY_PARAMETER));
    }

    public static WS.WSRequestHolder url(String url)
    {
        checkState(!url.matches("^[\\w]+:.*"), "Absolute request URIs are not supported for host requests");

        final AcHost acHost = getAcHost();
        final Option<String> user = getUser();

        final String absoluteUrl = acHost.getBaseUrl() + url;

        LOGGER.debug(format("Creating request to '%s'", absoluteUrl));

        final WS.WSRequestHolder request = WS.url(absoluteUrl)
                .setTimeout(DEFAULT_TIMEOUT.intValue())
                .setFollowRedirects(false) // because we need to sign again in those cases.
                .sign(new OAuthSignatureCalculator());

        return user.fold(
                Suppliers.ofInstance(request),
                new Function<String, WS.WSRequestHolder>()
                {
                    @Override
                    public WS.WSRequestHolder apply(String user)
                    {
                        return request.setQueryParameter(USER_ID_QUERY_PARAMETER, user);
                    }
                });
    }

    public static AcHost getAcHost()
    {
        return (AcHost) Http.Context.current().args.get("ac_host");
    }
}
