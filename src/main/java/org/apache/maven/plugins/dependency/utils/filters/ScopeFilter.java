package org.apache.maven.plugins.dependency.utils.filters;

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

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Scope;
import org.apache.maven.api.plugin.MojoException;
import org.codehaus.plexus.util.StringUtils;

/**
 * <p>ScopeFilter class.</p>
 *
 * @param <T> the type of objects to filter
 */
public class ScopeFilter<T>
        implements Predicate<T>
{

    private final Set<Scope> includes;
    private final Set<Scope> excludes;
    private final Function<T, Scope> extractor;

    /**
     * <p>Constructor for ScopeFilter.</p>
     *
     * @param includeScope the scope to be included.
     * @param excludeScope the scope to be excluded.
     */
    public ScopeFilter( String includeScope, String excludeScope, Function<T, Scope> extractor )
    {
        this.includes = StringUtils.isNotEmpty( includeScope ) ? getScopes( includeScope ) : Collections.emptySet();
        this.excludes = StringUtils.isNotEmpty( excludeScope ) ? getScopes( excludeScope ) : Collections.emptySet();
        this.extractor = extractor;
    }

    private static Set<Scope> getScopes( String s )
    {
        s = s.trim();
        if ( StringUtils.isEmpty( s ) )
        {
            return Collections.emptySet();
        }
        Scope scope;
        try
        {
            scope = Scope.get( s );
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoException( "Invalid scope : " + s );
        }

        Set<Scope> scopes;
        switch ( scope )
        {
            case PROVIDED:
            case SYSTEM:
                scopes = Collections.singleton( scope );
                break;
            case COMPILE:
            case RUNTIME:
            case TEST:
                scopes = ResolutionScope.fromString( s ).scopes();
                break;
            default:
                throw new IllegalStateException();
        }
        return scopes;
    }

    @Override
    public boolean test( T t )
    {
        return ( includes.isEmpty() || includes.contains( extractor.apply( t ) ) )
                && ( excludes.isEmpty() || !excludes.contains( extractor.apply( t ) ) );
    }

}
