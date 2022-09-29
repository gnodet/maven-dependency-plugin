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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Dependency;

/**
 * Utility class
 */
public class ArtifactUtils
{
    private static final String SNAPSHOT_VERSION = "SNAPSHOT";

    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile( "^(.*)-([0-9]{8}\\.[0-9]{6})-([0-9]+)$" );

    public static String key( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    public static String key( Dependency dependency )
    {
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion();
    }

    public static String key( Coordinate coordinate )
    {
        return coordinate.getGroupId() + ":" + coordinate.getArtifactId() + ":" + coordinate.getVersion();
    }

    public static String conflictId( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId()
                + ":" + artifact.getType().getName()
                + ( artifact.getClassifier().isEmpty() ? "" : ":" + artifact.getClassifier() );
    }

    public static String conflictId( org.apache.maven.api.model.Dependency dependency )
    {
        return dependency.getGroupId()
                + ":" + dependency.getArtifactId()
                + ":" + dependency.getType()
                + ( dependency.getClassifier().isEmpty() ? "" : ":" + dependency.getClassifier() );
    }

    public static String getIdWithDashes( Artifact artifact )
    {
        return artifact.getGroupId()
                + "-" + artifact.getArtifactId()
                + "-" + artifact.getType()
                + ( artifact.getClassifier().isEmpty() ? "" : "-" + artifact.getClassifier() )
                + "-" + artifact.getVersion().asString() + "-";
    }

    public static String toSnapshotVersion( String version )
    {
        int lastHyphen = version.lastIndexOf( '-' );
        if ( lastHyphen > 0 )
        {
            int prevHyphen = version.lastIndexOf( '-', lastHyphen - 1 );
            if ( prevHyphen > 0 )
            {
                Matcher m = VERSION_FILE_PATTERN.matcher( version );
                if ( m.matches() )
                {
                    return m.group( 1 ) + "-" + SNAPSHOT_VERSION;
                }
            }
        }
        return version;
    }
}
