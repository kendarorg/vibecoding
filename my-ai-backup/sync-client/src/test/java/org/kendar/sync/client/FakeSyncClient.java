package org.kendar.sync.client;

import org.kendar.sync.lib.network.TcpConnection;

import java.io.IOException;
import java.net.Socket;

public class FakeSyncClient extends SyncClient{
    @Override
    protected TcpConnection getTcpConnection(TcpConnection connection, CommandLineArgs args, int i) throws IOException {
        return connection;
    }
}
