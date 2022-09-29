package org.apache.maven.plugins.dependency.fromConfiguration;

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

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;

/**
 * Goal that copies a list of artifacts from the repository to defined locations.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo( name = "copy", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = false )
public class CopyMojo
        extends AbstractFromConfigurationMojo
{

    /**
     * <i>not used in this goal</i>
     */
    @Parameter
    protected boolean useJvmChmod = true;
    /**
     * <i>not used in this goal</i>
     */
    @Parameter
    protected boolean ignorePermissions;
    /**
     * Strip artifact version during copy
     */
    @Parameter( property = "mdep.stripVersion", defaultValue = "false" )
    private boolean stripVersion = false;
    /**
     * Strip artifact classifier during copy
     */
    @Parameter( property = "mdep.stripClassifier", defaultValue = "false" )
    private boolean stripClassifier = false;
    /**
     * Prepend artifact groupId during copy
     *
     * @since 2.7
     */
    @Parameter( property = "mdep.prependGroupId", defaultValue = "false" )
    private boolean prependGroupId = false;
    /**
     * Use artifact baseVersion during copy
     *
     * @since 2.7
     */
    @Parameter( property = "mdep.useBaseVersion", defaultValue = "false" )
    private boolean useBaseVersion = false;
    /**
     * The artifact to copy from command line. A string of the form groupId:artifactId:version[:packaging[:classifier]].
     * Use {@link AbstractFromConfigurationMojo#artifactItems} within the POM configuration.
     */
    @SuppressWarnings( "unused" ) // marker-field, setArtifact(String) does the magic
    @Parameter( property = "artifact" )
    private String artifact;

    /**
     * Main entry into mojo. This method gets the ArtifactItems and iterates through each one passing it to
     * copyArtifact.
     *
     * @throws MojoException with a message if an error occurs.
     * @see ArtifactItem
     * @see #getArtifactItems
     * @see #copyArtifact(ArtifactItem)
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        verifyRequirements();

        List<ArtifactItem> theArtifactItems =
                getProcessedArtifactItems(
                        new ProcessArtifactItemsRequest( stripVersion, prependGroupId, useBaseVersion,
                                stripClassifier ) );
        for ( ArtifactItem artifactItem : theArtifactItems )
        {
            if ( artifactItem.isNeedsProcessing() )
            {
                copyArtifact( artifactItem );
            }
            else
            {
                this.getLog().info( artifactItem + " already exists in " + artifactItem.getOutputDirectory() );
            }
        }
    }

    /**
     * Resolves the artifact from the repository and copies it to the specified location.
     *
     * @param artifactItem containing the information about the Artifact to copy.
     * @throws MojoException with a message if an error occurs.
     * @see #copyFile(Path, Path)
     */
    protected void copyArtifact( ArtifactItem artifactItem )
            throws MojoException
    {
        Path destFile = artifactItem.getOutputDirectory().resolve( artifactItem.getDestFileName() );

        copyFile( artifactItem.getArtifact().getPath().get(), destFile );
    }

    @Override
    protected Predicate<ArtifactItem> getMarkedArtifactFilter( ArtifactItem item )
    {
        return new DestFileFilter( this.isOverWriteReleases(), this.isOverWriteSnapshots(), this.isOverWriteIfNewer(),
                false, false, false, false,
                this.stripVersion, prependGroupId, useBaseVersion,
                item.getOutputDirectory() );
    }

    /**
     * @return Returns the stripVersion.
     */
    public boolean isStripVersion()
    {
        return this.stripVersion;
    }

    /**
     * @param stripVersion The stripVersion to set.
     */
    public void setStripVersion( boolean stripVersion )
    {
        this.stripVersion = stripVersion;
    }

    /**
     * @return Returns the stripClassifier.
     */
    public boolean isStripClassifier()
    {
        return this.stripClassifier;
    }

    /**
     * @param stripClassifier The stripClassifier to set.
     */
    public void setStripClassifier( boolean stripClassifier )
    {
        this.stripClassifier = stripClassifier;
    }

    /**
     * @param useBaseVersion The useBaseVersion to set.
     */
    public void setUseBaseVersion( boolean useBaseVersion )
    {
        this.useBaseVersion = useBaseVersion;
    }
}
