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
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.List;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.api.services.VersionParserException;

/**
 * Filter to include or exclude artifacts from a list of patterns. The artifact pattern syntax is of the form:
 *
 * <pre>[groupId]:[artifactId]:[type]:[version]</pre>
 *
 * <p>
 * Where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
 * segment is treated as an implicit wildcard.
 * </p>
 *
 * <p>
 * For example, <code>org.apache.*</code> would match all artifacts whose group id started with
 * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> would match all snapshot artifacts.
 * </p>
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class StrictPatternArtifactFilter implements Predicate<Artifact>
{
    // fields -----------------------------------------------------------------

    /**
     * The list of artifact patterns to match, as described above.
     */
    private final List<String> patterns;

    /**
     * Whether this filter should include or exclude artifacts that match the patterns.
     */
    private final boolean include;

    private final VersionParser versionParser;

    // constructors -----------------------------------------------------------

    /**
     * Creates a new filter that matches the specified artifact patterns and includes or excludes them according to the
     * specified flag.
     *
     * @param patterns the list of artifact patterns to match, as described above
     * @param include  <code>true</code> to include artifacts that match the patterns, or <code>false</code> to exclude
     *                 them
     */
    public StrictPatternArtifactFilter( List<String> patterns, boolean include, VersionParser versionParser )
    {
        this.patterns = patterns;
        this.include = include;
        this.versionParser = versionParser;
    }

    // ArtifactFilter methods -------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public boolean test( Artifact artifact )
    {
        boolean matched = false;

        for ( String pattern : patterns )
        {
            if ( include( artifact, pattern ) )
            {
                matched = true;
                break;
            }
        }

        return include ? matched : !matched;
    }

    // private methods --------------------------------------------------------

    /**
     * Gets whether the specified artifact matches the specified pattern.
     *
     * @param artifact the artifact to check
     * @param pattern  the pattern to match, as defined above
     * @return <code>true</code> if the specified artifact is matched by the specified pattern
     */
    private boolean include( Artifact artifact, String pattern )
    {
        String[] tokens = new String[] {
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getType().getName(),
                artifact.getVersion().asString()
        };

        String[] patternTokens = pattern.split( ":" );

        // fail immediately if pattern tokens outnumber tokens to match
        boolean matched = patternTokens.length <= tokens.length;

        for ( int i = 0; matched && i < patternTokens.length; i++ )
        {
            matched = matches( tokens[i], patternTokens[i] );
        }

        return matched;
    }

    /**
     * Gets whether the specified token matches the specified pattern segment.
     *
     * @param token   the token to check
     * @param pattern the pattern segment to match, as defined above
     * @return <code>true</code> if the specified token is matched by the specified pattern segment
     */
    private boolean matches( String token, String pattern )
    {
        boolean matches;

        // support full wildcard and implied wildcard
        if ( "*".equals( pattern ) || pattern.length() == 0 )
        {
            matches = true;
        }
        // support contains wildcard
        else if ( pattern.startsWith( "*" ) && pattern.endsWith( "*" ) )
        {
            String contains = pattern.substring( 1, pattern.length() - 1 );

            matches = token.contains( contains );
        }
        // support leading wildcard
        else if ( pattern.startsWith( "*" ) )
        {
            matches = token.endsWith( pattern.substring( 1 ) );
        }
        // support trailing wildcard
        else if ( pattern.endsWith( "*" ) )
        {
            String prefix = pattern.substring( 0, pattern.length() - 1 );

            matches = token.startsWith( prefix );
        }
        // support versions range 
        else if ( pattern.startsWith( "[" ) || pattern.startsWith( "(" ) )
        {
            matches = isVersionIncludedInRange( token, pattern );
        }
        // support exact match
        else
        {
            matches = token.equals( pattern );
        }

        return matches;
    }

    private boolean isVersionIncludedInRange( final String version, final String range )
    {
        try
        {
            return versionParser.parseVersionRange( range ).contains( versionParser.parseVersion( version ) );
        }
        catch ( VersionParserException e )
        {
            return false;
        }
    }

}
