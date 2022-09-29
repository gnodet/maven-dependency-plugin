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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @param <T> the type of objects
 */
public class DependencyStatusSets<T>
{
    Set<T> resolvedDependencies = null;

    Set<T> unResolvedDependencies = null;

    Set<T> skippedDependencies = null;

    /**
     * Default ctor.
     */
    public DependencyStatusSets()
    {
    }

    /**
     * @param resolved   set of {@link T}
     * @param unResolved set of {@link T}
     * @param skipped    set of {@link T}
     */
    public DependencyStatusSets( Set<T> resolved, Set<T> unResolved, Set<T> skipped )
    {
        if ( resolved != null )
        {
            this.resolvedDependencies = new LinkedHashSet<>( resolved );
        }
        if ( unResolved != null )
        {
            this.unResolvedDependencies = new LinkedHashSet<>( unResolved );
        }
        if ( skipped != null )
        {
            this.skippedDependencies = new LinkedHashSet<>( skipped );
        }
    }

    /**
     * @return Returns the resolvedDependencies.
     */
    public Set<T> getResolvedDependencies()
    {
        return this.resolvedDependencies;
    }

    /**
     * @param resolvedDependencies The resolvedDependencies to set.
     */
    public void setResolvedDependencies( Set<T> resolvedDependencies )
    {
        if ( resolvedDependencies != null )
        {
            this.resolvedDependencies = new LinkedHashSet<>( resolvedDependencies );
        }
        else
        {
            this.resolvedDependencies = null;
        }
    }

    /**
     * @return Returns the skippedDependencies.
     */
    public Set<T> getSkippedDependencies()
    {
        return this.skippedDependencies;
    }

    /**
     * @param skippedDependencies The skippedDependencies to set.
     */
    public void setSkippedDependencies( Set<T> skippedDependencies )
    {
        if ( skippedDependencies != null )
        {
            this.skippedDependencies = new LinkedHashSet<>( skippedDependencies );
        }
        else
        {
            this.skippedDependencies = null;
        }
    }

    /**
     * @return Returns the unResolvedDependencies.
     */
    public Set<T> getUnResolvedDependencies()
    {
        return this.unResolvedDependencies;
    }

    /**
     * @param unResolvedDependencies The unResolvedDependencies to set.
     */
    public void setUnResolvedDependencies( Set<T> unResolvedDependencies )
    {
        if ( unResolvedDependencies != null )
        {
            this.unResolvedDependencies = new LinkedHashSet<>( unResolvedDependencies );
        }
        else
        {
            this.unResolvedDependencies = null;
        }
    }
}
