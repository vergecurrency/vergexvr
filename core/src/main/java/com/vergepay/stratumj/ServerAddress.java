package com.vergepay.stratumj;

import java.net.Proxy;

/**
 * @author John L. Jegutanis
 */
final public class ServerAddress {
    public enum Protocol {
        AUTO,
        LEGACY_ELECTRUM,
        ELECTRUMX
    }

    public enum Transport {
        PLAIN_TCP,
        SSL_TLS
    }

    final private String host;
    final private int port;
    final private Proxy proxy;
    final private Protocol protocol;
    final private Transport transport;

    public ServerAddress(String host, int port) {
        this.host = host;
        this.port = port;
        this.proxy = null;
        this.protocol = Protocol.LEGACY_ELECTRUM;
        this.transport = Transport.PLAIN_TCP;
    }

    public ServerAddress(String host, int port, Proxy proxy) {
        this(host, port, proxy, Protocol.LEGACY_ELECTRUM, Transport.PLAIN_TCP);
    }

    public ServerAddress(String host, int port, Protocol protocol) {
        this(host, port, null, protocol, Transport.PLAIN_TCP);
    }

    public ServerAddress(String host, int port, Proxy proxy, Protocol protocol) {
        this(host, port, proxy, protocol, Transport.PLAIN_TCP);
    }

    public ServerAddress(String host, int port, Proxy proxy, Protocol protocol, Transport transport) {
        this.host = host;
        this.port = port;
        this.proxy = proxy;
        this.protocol = protocol;
        this.transport = transport;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "ServerAddress{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }

    public Proxy getProxy() {
        return proxy;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Transport getTransport() {
        return transport;
    }
}
