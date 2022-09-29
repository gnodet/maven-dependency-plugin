package org.apache.maven.plugins.dependency.tree;

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

import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to <a href="https://en.wikipedia.org/wiki/DOT_language">DOT
 * format</a>
 *
 * @author <a href="mailto:pi.songs@gmail.com">Pi Song</a>
 * @since 2.1
 */
public class DOTDependencyNodeVisitor
        extends AbstractSerializingVisitor
        implements NodeVisitor
{

    private final Map<Node, Node> parents = new HashMap<>();

    /**
     * Constructor.
     *
     * @param writer the writer to write to.
     */
    public DOTDependencyNodeVisitor( Writer writer )
    {
        super( writer );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enter( Node node )
    {
        if ( parents.isEmpty() )
        {
            node.stream().forEach( p -> p.getChildren().forEach( c -> parents.put( c, p ) ) );
        }

        if ( getParent( node ) == null )
        {
            writer.write( "digraph \"" + node.getArtifact().toString() + "\" { " + System.lineSeparator() );
        }

        // Generate "currentNode -> Child" lines

        List<Node> children = node.getChildren();

        for ( Node child : children )
        {
            writer.write( "\t\"" + node.getArtifact().toString() + "\" -> \""
                    + child.getArtifact().toString() + "\" ; " + System.lineSeparator() );
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean leave( Node node )
    {
        if ( getParent( node ) == null )
        {
            writer.write( " } " );
        }
        return true;
    }

    private Node getParent( Node child )
    {
        return parents.get( child );
    }

}
