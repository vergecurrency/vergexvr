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

    final private String host;
    final private int port;
    final private Proxy proxy;
    final private Protocol protocol;

    public ServerAddress(String host, int port) {
        this.host = host;
        this.port = port;
        this.proxy = null;
        this.protocol = Protocol.LEGACY_ELECTRUM;
    }

    public ServerAddress(String host, int port, Proxy proxy) {
        this(host, port, proxy, Protocol.LEGACY_ELECTRUM);
    }

    public ServerAddress(String host, int port, Protocol protocol) {
        this(host, port, null, protocol);
    }

    public ServerAddress(String host, int port, Proxy proxy, Protocol protocol) {
        this.host = host;
        this.port = port;
        this.proxy = proxy;
        this.protocol = protocol;
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
}
