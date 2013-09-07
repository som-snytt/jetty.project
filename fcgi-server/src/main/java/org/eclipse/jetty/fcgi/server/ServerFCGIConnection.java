//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.fcgi.server;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jetty.fcgi.FCGI;
import org.eclipse.jetty.fcgi.generator.Flusher;
import org.eclipse.jetty.fcgi.parser.ServerParser;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ByteBufferQueuedHttpInput;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ServerFCGIConnection extends AbstractConnection
{
    private static final Logger LOG = Log.getLogger(ServerFCGIConnection.class);

    private final ConcurrentMap<Integer, HttpChannelOverFCGI> channels = new ConcurrentHashMap<>();
    private final Connector connector;
    private final Flusher flusher;
    private final HttpConfiguration configuration;
    private final ServerParser parser;

    public ServerFCGIConnection(Connector connector, EndPoint endPoint, HttpConfiguration configuration)
    {
        super(endPoint, connector.getExecutor());
        this.connector = connector;
        this.flusher = new Flusher(endPoint);
        this.configuration = configuration;
        this.parser = new ServerParser(new ServerListener());
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        fillInterested();
    }

    @Override
    public void onFillable()
    {
        EndPoint endPoint = getEndPoint();
        ByteBufferPool bufferPool = connector.getByteBufferPool();
        ByteBuffer buffer = bufferPool.acquire(configuration.getResponseHeaderSize(), true);
        try
        {
            while (true)
            {
                int read = endPoint.fill(buffer);
                if (LOG.isDebugEnabled()) // Avoid boxing of variable 'read'
                    LOG.debug("Read {} bytes from {}", read, endPoint);
                if (read > 0)
                {
                    parse(buffer);
                }
                else if (read == 0)
                {
                    fillInterested();
                    break;
                }
                else
                {
                    shutdown();
                    break;
                }
            }
        }
        catch (Exception x)
        {
            LOG.debug(x);
            // TODO: fail and close ?
        }
        finally
        {
            bufferPool.release(buffer);
        }
    }

    private void parse(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
            parser.parse(buffer);
    }

    private void shutdown()
    {
        // TODO
    }

    private class ServerListener implements ServerParser.Listener
    {
        @Override
        public void onStart(int request, FCGI.Role role)
        {
            HttpChannelOverFCGI channel = new HttpChannelOverFCGI(connector, configuration, getEndPoint(),
                    new HttpTransportOverFCGI(connector.getByteBufferPool(), flusher, request), new ByteBufferQueuedHttpInput());
            HttpChannelOverFCGI existing = channels.putIfAbsent(request, channel);
            if (existing != null)
                throw new IllegalStateException();
        }

        @Override
        public void onHeader(int request, HttpField field)
        {
            HttpChannelOverFCGI channel = channels.get(request);
            if (channel != null)
                channel.header(field);
            else
                noChannel(request);
        }

        @Override
        public void onHeaders(int request)
        {
            HttpChannelOverFCGI channel = channels.get(request);
            if (channel != null)
                channel.headerComplete();
            else
                noChannel(request);
        }

        @Override
        public void onContent(int request, FCGI.StreamType stream, ByteBuffer buffer)
        {
        }

        @Override
        public void onEnd(int request)
        {
            HttpChannelOverFCGI channel = channels.get(request);
            if (channel != null)
                if (channel.messageComplete())
                    channel.dispatch();
            else
                noChannel(request);
        }

        private void noChannel(int request)
        {
            // TODO
        }
    }
}
