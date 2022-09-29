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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.codehaus.plexus.util.StringUtils;

/**
 * This is the common base class of ClassifierFilter and TypeFilter
 *
 * @param <T> the type of objects to filter
 */
public class IncludeExcludeFilter<T>
        implements Predicate<T>
{
    /**
     * The list of types or classifiers to include
     */
    private final List<String> includes;

    /**
     * The list of types or classifiers to exclude (ignored if includes != null)
     */
    private final List<String> excludes;

    private final Function<T, String> extractor;

    private final BiPredicate<String, String> comparator;

    /**
     * <p>Constructor for AbstractArtifactFeatureFilter.</p>
     *
     * @param include    comma separated list with includes.
     * @param exclude    comma separated list with excludes.
     * @param extractor
     * @param comparator
     */
    public IncludeExcludeFilter( String include, String exclude,
                                 Function<T, String> extractor,
                                 BiPredicate<String, String> comparator )
    {
        this.includes = StringUtils.isNotEmpty( include )
                ? Arrays.asList( StringUtils.split( include, "," ) )
                : Collections.emptyList();
        this.excludes = StringUtils.isNotEmpty( exclude )
                ? Arrays.asList( StringUtils.split( exclude, "," ) )
                : Collections.emptyList();
        this.extractor = extractor;
        this.comparator = comparator;
    }

    public IncludeExcludeFilter( String include, String exclude,
                                 Function<T, String> extractor )
    {
        this( include, exclude, extractor, Objects::equals );
    }

    @Override
    public boolean test( T t )
    {
        return ( includes.isEmpty()
                || includes.stream().anyMatch( include -> comparator.test( extractor.apply( t ), include ) ) )
                && ( excludes.isEmpty()
                || excludes.stream().noneMatch( exclude -> comparator.test( extractor.apply( t ), exclude ) ) );
    }

    /**
     * {@inheritDoc}
     * <p>
     * This function determines if filtering needs to be performed. Includes are processed before Excludes.
     */
    public Set<T> filter( Set<T> artifacts )
    {
        return artifacts.stream().filter( this ).collect( Collectors.toSet() );
    }

}
