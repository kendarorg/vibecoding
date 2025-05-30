package org.kendar.sync.client;

import org.kendar.sync.lib.network.TcpConnection;

import java.io.IOException;

public class FakeSyncClient extends SyncClient {
    protected TcpConnection getTcpConnection(TcpConnection connection,
                                             CommandLineArgs args, int i, int maxPacketSize) throws IOException {
        return connection;
    }
}
