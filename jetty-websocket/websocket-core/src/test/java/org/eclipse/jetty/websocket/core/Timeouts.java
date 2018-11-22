//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.core;

import java.util.concurrent.TimeUnit;

public final class Timeouts
{
    public static final long CONNECT_MS = TimeUnit.SECONDS.toMillis(10);
    public static final long OPEN_EVENT_MS = TimeUnit.SECONDS.toMillis(10);
    public static final long CLOSE_EVENT_MS = TimeUnit.SECONDS.toMillis(10);
}