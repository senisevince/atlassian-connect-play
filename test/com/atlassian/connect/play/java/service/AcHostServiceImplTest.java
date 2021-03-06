package com.atlassian.connect.play.java.service;

import com.atlassian.connect.play.java.AcHost;
import com.atlassian.connect.play.java.auth.InvalidAuthenticationRequestException;
import com.atlassian.connect.play.java.auth.MismatchPublicKeyException;
import com.atlassian.connect.play.java.auth.PublicKeyVerificationFailureException;
import com.google.common.base.Charsets;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.w3c.dom.Document;
import play.libs.XML;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

import play.libs.ws.WSResponse;
import play.libs.ws.WSRequestHolder;

import static com.atlassian.fugue.Option.none;
import static com.atlassian.fugue.Option.option;
import static org.apache.commons.lang.StringUtils.stripToEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static play.libs.F.Promise;

@RunWith(MockitoJUnitRunner.class)
public class AcHostServiceImplTest {
    private static final String TEST_PUBLIC_KEY = "REAL-PK-GOES-HERE";
    private static final String BASE_URL = "base";
    private static Document testClientInfoDocument;
    private static Document testDodgyClientInfoDocument;
    private static Document testMismatchedPKClientInfoDocument;


    @Mock
    private AcHostHttpClient httpClient;

    @Mock
    private WSRequestHolder requestHolder;

    @Mock
    private WSResponse response;

    @Mock
    private AcHostRepository acHostRepository;

    private AcHostServiceImpl acHostService;
    private AcHost acHost;

    @BeforeClass
    public static void loadTestData() throws FileNotFoundException {
        testClientInfoDocument = fetchTestDocument("consumer-info.xml");
        testDodgyClientInfoDocument = fetchTestDocument("dodgy-consumer-info.xml");
        testMismatchedPKClientInfoDocument = fetchTestDocument("mismatched-pk-consumer-info.xml");
    }

    private static Document fetchTestDocument(String filename) throws FileNotFoundException {
        return XML.fromInputStream(new FileInputStream("test/resources/" + filename), Charsets.UTF_8.name());
    }


    @Before
    public void init() throws Throwable {
        acHostService = new AcHostServiceImpl(httpClient, acHostRepository);
        acHost = new AcHost();
        acHost.setPublicKey(TEST_PUBLIC_KEY);
        acHost.setBaseUrl(BASE_URL);

        when(httpClient.url(anyString(), any(AcHost.class), anyBoolean())).thenReturn(requestHolder);
        when(requestHolder.get()).thenReturn(Promise.pure(response));
        when(response.getStatus()).thenReturn(200);
        when(response.asXml()).thenReturn(testClientInfoDocument);
        when(acHostRepository.findByKey(any(String.class))).thenReturn(none(AcHost.class));
        when(acHostRepository.findByUrl(eq(acHost.getBaseUrl()))).thenReturn(option((AcHost) acHost));
    }

    @Test
    public void sendsCorrectHttpRequest() {
        acHostService.fetchPublicKeyFromRemoteHost(acHost);
        verify(httpClient).url(BASE_URL + AcHost.CONSUMER_INFO_URL, acHost, false);
        verify(requestHolder).get();
    }

    @Test
    public void extractsCorrectPublicKey() {
        Promise<String> publicKeyPromise = acHostService.fetchPublicKeyFromRemoteHost(acHost);
        String publicKey = stripToEmpty(publicKeyPromise.get(1, TimeUnit.SECONDS));

        assertThat(publicKey, is(TEST_PUBLIC_KEY));
    }

    @Test(expected = PublicKeyVerificationFailureException.class)
    public void returnsFailurePromiseWhenFailToFetchPublicKey() {
        when(response.getStatus()).thenReturn(401);
        Promise<String> publicKeyPromise = acHostService.fetchPublicKeyFromRemoteHost(acHost);
        publicKeyPromise.get(1, TimeUnit.SECONDS);
    }

    @Test(expected = RuntimeException.class)
    public void returnsFailurePromiseWhenFailToParseXml() {
        when(response.asXml()).thenThrow(new RuntimeException("blah"));
        Promise<String> publicKeyPromise = acHostService.fetchPublicKeyFromRemoteHost(acHost);
        publicKeyPromise.get(1, TimeUnit.SECONDS);
    }

    @Test(expected = PublicKeyVerificationFailureException.class)
    public void returnsFailurePromiseWhenPublicKeyNotFoundInXml() {
        when(response.asXml()).thenReturn(testDodgyClientInfoDocument);
        Promise<String> publicKeyPromise = acHostService.fetchPublicKeyFromRemoteHost(acHost);
        publicKeyPromise.get(1, TimeUnit.SECONDS);
    }


    @Test
    public void savesAcHost() throws Throwable {
        acHostService.registerHost("empty", acHost.getBaseUrl(), acHost.getPublicKey(), "", "").get(1, TimeUnit.SECONDS);
        verify(acHostRepository).save(acHost);
    }

}
