/*
 * Copyright (c) 2016 Kevin Herron
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

package org.eclipse.milo.opcua.sdk.server.namespaces.loader;

import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;

public class UaNodeLoader {

    private final UaNodeContext context;

    public UaNodeLoader(UaNodeContext context) {
        this.context = context;
    }

    public void loadNodes() throws Exception {
        new UaDataTypeLoader(context).buildNodes();
        new UaMethodLoader(context).buildNodes();
        new UaObjectLoader(context).buildNodes();
        new UaObjectTypeLoader(context).buildNodes();
        new UaReferenceTypeLoader(context).buildNodes();
        new UaVariableLoader(context).buildNodes();
        new UaVariableTypeLoader(context).buildNodes();
        new UaViewLoader(context).buildNodes();
    }

}
