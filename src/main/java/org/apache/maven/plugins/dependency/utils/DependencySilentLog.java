package org.apache.maven.plugins.dependency.utils;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import java.util.function.Supplier;

import org.apache.maven.api.plugin.Log;

/**
 * This logger implements both types of logs currently in use and turns off logs.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class DependencySilentLog
        implements Log
{
    @Override
    public boolean isDebugEnabled()
    {
        return false;
    }

    @Override
    public void debug( CharSequence charSequence )
    {

    }

    @Override
    public void debug( CharSequence charSequence, Throwable throwable )
    {

    }

    @Override
    public void debug( Throwable throwable )
    {

    }

    @Override
    public void debug( Supplier<String> supplier )
    {

    }

    @Override
    public void debug( Supplier<String> supplier, Throwable throwable )
    {

    }

    @Override
    public boolean isInfoEnabled()
    {
        return false;
    }

    @Override
    public void info( CharSequence charSequence )
    {

    }

    @Override
    public void info( CharSequence charSequence, Throwable throwable )
    {

    }

    @Override
    public void info( Throwable throwable )
    {

    }

    @Override
    public void info( Supplier<String> supplier )
    {

    }

    @Override
    public void info( Supplier<String> supplier, Throwable throwable )
    {

    }

    @Override
    public boolean isWarnEnabled()
    {
        return false;
    }

    @Override
    public void warn( CharSequence charSequence )
    {

    }

    @Override
    public void warn( CharSequence charSequence, Throwable throwable )
    {

    }

    @Override
    public void warn( Throwable throwable )
    {

    }

    @Override
    public void warn( Supplier<String> supplier )
    {

    }

    @Override
    public void warn( Supplier<String> supplier, Throwable throwable )
    {

    }

    @Override
    public boolean isErrorEnabled()
    {
        return false;
    }

    @Override
    public void error( CharSequence charSequence )
    {

    }

    @Override
    public void error( CharSequence charSequence, Throwable throwable )
    {

    }

    @Override
    public void error( Throwable throwable )
    {

    }

    @Override
    public void error( Supplier<String> supplier )
    {

    }

    @Override
    public void error( Supplier<String> supplier, Throwable throwable )
    {

    }
}
