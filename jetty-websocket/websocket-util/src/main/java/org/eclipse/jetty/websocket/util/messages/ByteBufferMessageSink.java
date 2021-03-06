//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.util.messages;

import java.io.ByteArrayOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.exception.MessageTooLargeException;
import org.eclipse.jetty.websocket.util.InvalidSignatureException;

public class ByteBufferMessageSink extends AbstractMessageSink
{
    private static final int BUFFER_SIZE = 65535;
    private ByteArrayOutputStream out;
    private int size;

    public ByteBufferMessageSink(CoreSession session, MethodHandle methodHandle)
    {
        super(session, methodHandle);

        // Validate onMessageMethod
        Objects.requireNonNull(methodHandle, "MethodHandle");
        MethodType onMessageType = MethodType.methodType(Void.TYPE, ByteBuffer.class);
        if (methodHandle.type() != onMessageType)
        {
            throw InvalidSignatureException.build(onMessageType, methodHandle.type());
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            if (frame.hasPayload())
            {
                ByteBuffer payload = frame.getPayload();
                size += payload.remaining();
                long maxBinaryMessageSize = session.getMaxBinaryMessageSize();
                if (maxBinaryMessageSize > 0 && size > maxBinaryMessageSize)
                {
                    throw new MessageTooLargeException(String.format("Binary message too large: (actual) %,d > (configured max binary message size) %,d",
                        size, maxBinaryMessageSize));
                }

                if (out == null)
                    out = new ByteArrayOutputStream(BUFFER_SIZE);

                BufferUtil.writeTo(payload, out);
                payload.position(payload.limit()); // consume buffer
            }

            if (frame.isFin())
            {
                if (out != null)
                    methodHandle.invoke(ByteBuffer.wrap(out.toByteArray()));
                else
                    methodHandle.invoke(BufferUtil.EMPTY_BUFFER);
            }

            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
        finally
        {
            if (frame.isFin())
            {
                // reset
                out = null;
                size = 0;
            }
        }
    }
}
