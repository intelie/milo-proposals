/*
 * Copyright (c) 2018 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.eclipse.milo.opcua.sdk.server.nodes.factories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.ObjectTypeManager;
import org.eclipse.milo.opcua.sdk.server.UaNodeManager;
import org.eclipse.milo.opcua.sdk.server.VariableTypeManager;
import org.eclipse.milo.opcua.sdk.server.api.nodes.MethodNode;
import org.eclipse.milo.opcua.sdk.server.api.nodes.ObjectNode;
import org.eclipse.milo.opcua.sdk.server.api.nodes.ObjectTypeNode;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.util.Tree;
import org.jooq.lambda.tuple.Tuple3;

public class NodeFactory {

    private final UaNodeContext context;
    private final ObjectTypeManager objectTypeManager;
    private final VariableTypeManager variableTypeManager;

    public NodeFactory(
        UaNodeContext context,
        ObjectTypeManager objectTypeManager,
        VariableTypeManager variableTypeManager) {

        this.context = context;
        this.objectTypeManager = objectTypeManager;
        this.variableTypeManager = variableTypeManager;
    }

    public UaNode createNode(
        NodeId rootNodeId,
        NodeId typeDefinitionId,
        boolean includeOptionalNodes) throws UaException {

        Tree<UaNode> nodeTree = createNodeTree(
            rootNodeId,
            typeDefinitionId,
            includeOptionalNodes
        );

        return nodeTree.getValue();
    }

    public UaNode createNode(
        NodeId rootNodeId,
        NodeId typeDefinitionId,
        boolean includeOptionalNodes,
        InstanceListener instanceListener) throws UaException {

        Tree<UaNode> nodeTree = createNodeTree(
            rootNodeId,
            typeDefinitionId,
            includeOptionalNodes
        );

        notifyInstanceListener(nodeTree, instanceListener);

        return nodeTree.getValue();
    }

    public Tree<UaNode> createNodeTree(
        NodeId rootNodeId,
        NodeId typeDefinitionId,
        boolean includeOptionalNodes) throws UaException {

        UaNodeManager nodeManager = context.getNodeManager();

        if (!nodeManager.containsNode(typeDefinitionId)) {
            throw new UaException(
                StatusCodes.Bad_NodeIdUnknown,
                "unknown type definition: " + typeDefinitionId);
        }

        InstanceDeclarationHierarchy idh = InstanceDeclarationHierarchy
            .create(nodeManager, typeDefinitionId, includeOptionalNodes);

        NodeTable nodeTable = idh.getNodeTable();
        ReferenceTable referenceTable = idh.getReferenceTable();

        Map<BrowsePath, UaNode> nodes = new HashMap<>();

        for (Map.Entry<BrowsePath, NodeId> entry : nodeTable.nodes.entrySet()) {
            BrowsePath browsePath = entry.getKey();
            NodeId nodeId = entry.getValue();

            UaNode node = nodeManager.get(nodeId);

            if (browsePath.parent == null) {
                // Root Node of hierarchy will be the ObjectType or VariableType to be instantiated

                if (node instanceof ObjectTypeNode) {
                    UaNode instance = instanceFromTypeDefinition(rootNodeId, (ObjectTypeNode) node);

                    nodes.put(browsePath, instance);
                } else if (node instanceof VariableTypeNode) {
                    UaNode instance = instanceFromTypeDefinition(rootNodeId, (VariableTypeNode) node);

                    nodes.put(browsePath, instance);
                } else {
                    throw new UaException(StatusCodes.Bad_InternalError);
                }
            } else {
                // Non-root Nodes are all instance declarations
                NodeId instanceNodeId = instanceNodeId(rootNodeId, browsePath);

                if (node instanceof MethodNode) {
                    MethodNode declaration = (MethodNode) node;

                    UaMethodNode instance = new UaMethodNode(
                        context,
                        instanceNodeId,
                        declaration.getBrowseName(),
                        declaration.getDisplayName(),
                        declaration.getDescription(),
                        declaration.getWriteMask(),
                        declaration.getUserWriteMask(),
                        declaration.isExecutable(),
                        declaration.isUserExecutable()
                    );

                    nodes.put(browsePath, instance);
                } else if (node instanceof ObjectNode) {
                    ObjectNode declaration = (ObjectNode) node;

                    ExpandedNodeId instanceTypeDefinitionId =
                        getTypeDefinition(referenceTable, browsePath);

                    UaNode typeDefinitionNode = nodeManager.get(instanceTypeDefinitionId);

                    if (typeDefinitionNode instanceof ObjectTypeNode) {
                        UaObjectNode instance = instanceFromTypeDefinition(
                            instanceNodeId, (ObjectTypeNode) typeDefinitionNode);

                        instance.setBrowseName(declaration.getBrowseName());
                        instance.setDisplayName(declaration.getDisplayName());
                        instance.setDescription(declaration.getDescription());
                        instance.setWriteMask(declaration.getWriteMask());
                        instance.setUserWriteMask(declaration.getUserWriteMask());
                        instance.setEventNotifier(declaration.getEventNotifier());

                        nodes.put(browsePath, instance);
                    } else {
                        throw new UaException(
                            StatusCodes.Bad_InternalError,
                            "expected type definition for " + instanceTypeDefinitionId);
                    }
                } else if (node instanceof VariableNode) {
                    VariableNode declaration = (VariableNode) node;

                    ExpandedNodeId instanceTypeDefinitionId =
                        getTypeDefinition(referenceTable, browsePath);

                    UaNode typeDefinitionNode = nodeManager.get(instanceTypeDefinitionId);

                    if (typeDefinitionNode instanceof VariableTypeNode) {
                        UaVariableNode instance = instanceFromTypeDefinition(
                            instanceNodeId, (VariableTypeNode) typeDefinitionNode);

                        instance.setBrowseName(declaration.getBrowseName());
                        instance.setDisplayName(declaration.getDisplayName());
                        instance.setDescription(declaration.getDescription());
                        instance.setWriteMask(declaration.getWriteMask());
                        instance.setUserWriteMask(declaration.getUserWriteMask());
                        instance.setValue(declaration.getValue());
                        instance.setDataType(declaration.getDataType());
                        instance.setValueRank(declaration.getValueRank());
                        instance.setArrayDimensions(declaration.getArrayDimensions());

                        nodes.put(browsePath, instance);
                    } else {
                        throw new UaException(
                            StatusCodes.Bad_InternalError,
                            "expected type definition for " + instanceTypeDefinitionId);
                    }
                } else {
                    throw new UaException(
                        StatusCodes.Bad_InternalError,
                        "not an instance declaration: " + node);
                }
            }
        }

        nodes.forEach((browsePath, node) -> {
            List<Tuple3<BrowsePath, NodeId, ReferenceTable.Target>> references =
                referenceTable.getReferences(browsePath);

            references.forEach(t -> {
                NodeId referenceTypeId = t.v2;
                ReferenceTable.Target target = t.v3;

                if (!Identifiers.HasModellingRule.equals(referenceTypeId)) {
                    if (target.targetNodeId != null) {
                        NodeClass targetNodeClass = nodeManager
                            .getNode(target.targetNodeId)
                            .map(UaNode::getNodeClass)
                            .orElse(NodeClass.Unspecified);

                        node.addReference(new Reference(
                            node.getNodeId(),
                            referenceTypeId,
                            target.targetNodeId,
                            targetNodeClass,
                            true
                        ));
                    } else {
                        BrowsePath targetPath = target.targetPath;

                        UaNode targetNode = nodes.get(targetPath);

                        if (targetNode != null) {
                            node.addReference(new Reference(
                                node.getNodeId(),
                                referenceTypeId,
                                targetNode.getNodeId().expanded(),
                                targetNode.getNodeClass(),
                                true
                            ));
                        }
                    }
                }
            });

            nodeManager.addNode(node);
        });

        return nodeTable.getBrowsePathTree().map(nodes::get);
    }

    protected void notifyInstanceListener(Tree<UaNode> nodeTree, InstanceListener instanceListener) {
        nodeTree.traverse((node, parentNode) -> {
            if (parentNode instanceof UaObjectNode && node instanceof UaMethodNode) {
                UaMethodNode methodNode = (UaMethodNode) node;

                instanceListener.onMethodAdded((UaObjectNode) parentNode, methodNode);
            } else if (parentNode instanceof UaObjectNode && node instanceof UaObjectNode) {
                UaObjectNode objectNode = (UaObjectNode) node;
                ObjectTypeNode objectTypeNode = objectNode.getTypeDefinitionNode();

                instanceListener.onObjectAdded((UaObjectNode) parentNode, objectNode, objectTypeNode.getNodeId());
            } else if (node instanceof UaVariableNode) {
                UaVariableNode variableNode = (UaVariableNode) node;
                VariableTypeNode variableTypeNode = variableNode.getTypeDefinitionNode();

                instanceListener.onVariableAdded(parentNode, variableNode, variableTypeNode.getNodeId());
            }
        });
    }

    /**
     * Return an appropriate {@link NodeId} for the instance being created.
     *
     * @param rootNodeId the root {@link NodeId}.
     * @param browsePath the relative {@link BrowsePath} to the instance being created.
     * @return a {@link NodeId} for the instance being created.
     */
    protected NodeId instanceNodeId(NodeId rootNodeId, BrowsePath browsePath) {
        Object rootIdentifier = rootNodeId.getIdentifier();

        String instanceIdentifier = String.format("%s%s", rootIdentifier, browsePath.join());

        return new NodeId(rootNodeId.getNamespaceIndex(), instanceIdentifier);
    }

    protected UaObjectNode instanceFromTypeDefinition(
        NodeId nodeId,
        ObjectTypeNode typeDefinitionNode) {

        NodeId typeDefinitionId = typeDefinitionNode.getNodeId();

        // Use a specialized instance if one is registered, otherwise fallback to UaObjectNode.
        ObjectTypeManager.ObjectNodeConstructor ctor = objectTypeManager
            .getNodeFactory(typeDefinitionId)
            .orElse(UaObjectNode::new);

        return ctor.apply(
            context,
            nodeId,
            typeDefinitionNode.getBrowseName(),
            typeDefinitionNode.getDisplayName(),
            typeDefinitionNode.getDescription(),
            typeDefinitionNode.getWriteMask(),
            typeDefinitionNode.getUserWriteMask()
        );
    }

    protected UaVariableNode instanceFromTypeDefinition(
        NodeId nodeId,
        VariableTypeNode typeDefinitionNode) {

        NodeId typeDefinitionId = typeDefinitionNode.getNodeId();

        // Use a specialized instance if one is registered, otherwise fallback to UaVariableNode.
        VariableTypeManager.VariableNodeConstructor ctor = variableTypeManager
            .getNodeFactory(typeDefinitionId)
            .orElse(UaVariableNode::new);

        return ctor.apply(
            context,
            nodeId,
            typeDefinitionNode.getBrowseName(),
            typeDefinitionNode.getDisplayName(),
            typeDefinitionNode.getDescription(),
            typeDefinitionNode.getWriteMask(),
            typeDefinitionNode.getUserWriteMask()
        );
    }

    private static ExpandedNodeId getTypeDefinition(ReferenceTable referenceTable, BrowsePath browsePath) {
        return referenceTable
            .getReferences(browsePath)
            .stream()
            .filter(t -> t.v2.equals(Identifiers.HasTypeDefinition))
            .map(t -> t.v3.targetNodeId)
            .findFirst()
            .orElse(ExpandedNodeId.NULL_VALUE);
    }

    interface InstanceListener {

        /**
         * Called when a {@link UaMethodNode} has been added to a {@link UaObjectNode} somewhere in the instance
         * hierarchy.
         *
         * @param parent   the {@link UaObjectNode} the method was added to.
         * @param instance the {@link UaMethodNode} instance.
         */
        default void onMethodAdded(@Nullable UaObjectNode parent, UaMethodNode instance) {}

        /**
         * Called when a {@link UaObjectNode} has been added to a parent {@link UaObjectNode} by a hierarchical
         * reference somewhere in the instance hierarchy.
         *
         * @param parent           the parent {@link UaObjectNode}
         * @param instance         the {@link UaObjectNode} instance.
         * @param typeDefinitionId the {@link NodeId} of the ObjectTypeDefinition.
         */
        default void onObjectAdded(@Nullable UaObjectNode parent, UaObjectNode instance, NodeId typeDefinitionId) {}

        /**
         * Called when a {@link UaVariableNode} has been added to a parent {@link UaNode} by a hierarchical
         * reference somewhere in the instance hierarchy.
         *
         * @param parent           the parent {@link UaVariableNode}
         * @param instance         the {@link UaVariableNode} instance.
         * @param typeDefinitionId the {@link NodeId} of the VariableTypeDefinition.
         */
        default void onVariableAdded(@Nullable UaNode parent, UaVariableNode instance, NodeId typeDefinitionId) {}

    }

}
