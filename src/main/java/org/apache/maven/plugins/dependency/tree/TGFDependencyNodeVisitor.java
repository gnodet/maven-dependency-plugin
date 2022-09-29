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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to a writer using the
 * <a href="https://en.wikipedia.org/wiki/Trivial_Graph_Format">TGF format</a>.
 *
 * @author <a href="mailto:jerome.creignou@gmail.com">Jerome Creignou</a>
 * @since 2.1
 */
public class TGFDependencyNodeVisitor
        extends AbstractSerializingVisitor
        implements NodeVisitor
{

    /**
     * List of edges.
     */
    private final List<EdgeAppender> edges = new ArrayList<>();
    /**
     * The parents
     */
    private final Map<Node, Node> parents = new HashMap<>();

    /**
     * Constructor.
     *
     * @param writer the writer to write to.
     */
    public TGFDependencyNodeVisitor( Writer writer )
    {
        super( writer );
    }

    /**
     * Generate a unique id from a DependencyNode.
     * <p>
     * Current implementation is rather simple and uses hashcode.
     * </p>
     *
     * @param node the DependencyNode to use.
     * @return the unique id.
     */
    private static String generateId( Node node )
    {
        return String.valueOf( node.hashCode() );
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
        // write node
        writer.write( generateId( node ) );
        writer.write( " " );
        writer.write( node.getArtifact().toString() );
        writer.write( System.lineSeparator() );
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean leave( Node node )
    {
        if ( parents.get( node ) == null )
        {
            // dump edges on last node endVisit
            writer.write( "#" );
            writer.write( System.lineSeparator() );
            for ( EdgeAppender edge : edges )
            {
                writer.write( edge.toString() );
                writer.write( System.lineSeparator() );
            }
        }
        else
        {
            Node p = parents.get( node );
            // using scope as edge label.
            edges.add( new EdgeAppender( p, node, node.getDependency().getScope().toString() ) );
        }
        return true;
    }

    /**
     * Utiity class to write an Edge.
     *
     * @author <a href="mailto:jerome.creignou@gmail.com">Jerome Creignou</a>
     */
    static final class EdgeAppender
    {
        /**
         * Edge start.
         */
        private final Node from;

        /**
         * Edge end.
         */
        private final Node to;

        /**
         * Edge label. (optional)
         */
        private final String label;

        /**
         * Build a new EdgeAppender.
         *
         * @param from  edge start.
         * @param to    edge end
         * @param label optional label.
         */
        EdgeAppender( Node from, Node to, String label )
        {
            super();
            this.from = from;
            this.to = to;
            this.label = label;
        }

        /**
         * build a string representing the edge.
         */
        @Override
        public String toString()
        {
            StringBuilder result = new StringBuilder( generateId( from ) );
            result.append( ' ' ).append( generateId( to ) );
            if ( label != null )
            {
                result.append( ' ' ).append( label );
            }
            return result.toString();
        }

    }
}
