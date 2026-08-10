package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.EndpointProbeObservation;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import com.ftk.tpip.provider.domain.service.EndpointConnectivityProbe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

@Component
public final class JdkEndpointConnectivityProbe implements EndpointConnectivityProbe {
    @Override
    public EndpointProbeObservation probe(ProviderEndpoint endpoint) {
        long started = System.nanoTime();
        try {
            URI uri = URI.create(endpoint.baseUrl());
            int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            if ("https".equalsIgnoreCase(uri.getScheme())) probeTls(uri.getHost(), port, endpoint.connectTimeoutMs());
            else probeTcp(uri.getHost(), port, endpoint.connectTimeoutMs());
            return observation(true, "CONNECTED", started);
        } catch (SocketTimeoutException exception) {
            return observation(false, "CONNECT_TIMEOUT", started);
        } catch (javax.net.ssl.SSLException exception) {
            return observation(false, "TLS_HANDSHAKE_FAILED", started);
        } catch (java.net.UnknownHostException exception) {
            return observation(false, "DNS_RESOLUTION_FAILED", started);
        } catch (IOException | IllegalArgumentException exception) {
            return observation(false, "CONNECTION_FAILED", started);
        }
    }

    private static void probeTcp(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(host, port), timeoutMs); }
    }

    private static void probeTls(String host, int port, int timeoutMs) throws IOException {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket()) {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.startHandshake();
        }
    }

    private static EndpointProbeObservation observation(boolean success, String code, long started) {
        return new EndpointProbeObservation(success, code,
                Math.max(0, java.time.Duration.ofNanos(System.nanoTime() - started).toMillis()));
    }
}
