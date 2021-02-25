/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.axis2.AxisFault;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.libraries.imports.SynapseImport;
import org.apache.synapse.libraries.model.Library;
import org.apache.synapse.libraries.model.SynapseLibrary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.integrator.initializer.ServiceBusUtils;
import org.wso2.micro.integrator.initializer.persistence.MediationPersistenceManager;
import org.wso2.micro.integrator.initializer.deployment.synapse.deployer.SynapseAppDeployer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.ITEM_TYPE_IMPORT;

/**
 * API Resource to manage connectors deployed
 **/
public class ConnectorResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(ConnectorResource.class);

    // Set of HTTP methods allowed by the resource
    Set<String> methods = null;
    // Attributes in json payload
    private static final String NAME_ATTRIBUTE = "name";
    private static final String STATUS_ATTRIBUTE = "status";
    private static final String PACKAGE_ATTRIBUTE = "package";
    private static final String DESCRIPTION_ATTRIBUTE = "description";

    public ConnectorResource() {

        methods = new HashSet<>();
        methods.add(Constants.HTTP_POST);
        methods.add(Constants.HTTP_GET);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        String connectorName = Utils.getQueryParameter(messageContext, "connectorName");
        if (messageContext.isDoingGET()) {
            if (Objects.nonNull(connectorName)) {
                populateConnectorData(axis2MessageContext, synapseConfiguration, connectorName);
            } else {
                populateConnectorList(axis2MessageContext, synapseConfiguration);
            }
            axis2MessageContext.removeProperty(Constants.NO_ENTITY_BODY);
        } else {

            try {
                if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
                    Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("POST method required json payload"));
                } else {
                    JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
                    if (payload.has(NAME_ATTRIBUTE) && payload.has(STATUS_ATTRIBUTE) && payload.has(PACKAGE_ATTRIBUTE)) {
                        changeConnectorState(axis2MessageContext, payload, synapseConfiguration);
                    } else {
                        Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Missing parameters in payload"));
                    }
                }
            } catch (AxisFault axisFault) {
                LOG.error("Error when updating connector status", axisFault);
                Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Error when updating connector status"));
            } catch (IOException e) {
                LOG.error("Error when parsing JSON payload", e);
                Utils.setJsonPayLoad(axis2MessageContext, Utils.createJsonErrorObject("Error when parsing JSON payload"));
            }
        }
        return true;
    }

    private void populateConnectorData(org.apache.axis2.context.MessageContext axis2MessageContext,
                                       SynapseConfiguration synapseConfiguration, String connectorName) {

        Map<String, Library> libraries = synapseConfiguration.getSynapseLibraries();

        for (Map.Entry<String, Library> entry : libraries.entrySet()) {
            if(((SynapseLibrary)entry.getValue()).getName().equals(connectorName)) {
                SynapseLibrary connector = (SynapseLibrary)entry.getValue();
                if (Objects.nonNull(connector)) {
                    Utils.setJsonPayLoad(axis2MessageContext, getConnectorAsJson(connector));
                }
            }
        }
    }

    private Object getConnectorAsJson(SynapseLibrary connector) {
        JSONObject connectorObject  = new JSONObject();
        connectorObject.put(NAME_ATTRIBUTE, connector.getName());
        connectorObject.put(PACKAGE_ATTRIBUTE, connector.getPackage());
        connectorObject.put(DESCRIPTION_ATTRIBUTE, connector.getDescription());
        connectorObject.put(STATUS_ATTRIBUTE, getConnectorState(connector.getLibStatus()));
        return connectorObject;
    }

    /**
     * Sets the list of all available connectors to the response as json
     *
     * @param axis2MessageContext AXIS2 message context
     * @param synapseConfiguration Synapse configuration object
     */
    private void populateConnectorList(org.apache.axis2.context.MessageContext axis2MessageContext,
                                       SynapseConfiguration synapseConfiguration) {

        Map<String, Library> libraryList = synapseConfiguration.getSynapseLibraries();
        JSONObject jsonBody = Utils.createJSONList(libraryList.size());
        libraryList.forEach((key, value) ->
                addToJsonList(jsonBody.getJSONArray(Constants.LIST), (SynapseLibrary) value));

        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * Adds the provided synapse library to a json list
     * @param jsonList JsonArray
     * @param library Connector
     */
    private void addToJsonList(JSONArray jsonList, SynapseLibrary library) {

        JSONObject connectorObject = new JSONObject();
        connectorObject.put(NAME_ATTRIBUTE, library.getName());
        connectorObject.put(PACKAGE_ATTRIBUTE, library.getPackage());
        connectorObject.put(DESCRIPTION_ATTRIBUTE, library.getDescription());
        connectorObject.put(STATUS_ATTRIBUTE, getConnectorState(library.getLibStatus()));
        jsonList.put(connectorObject);
    }

    /**
     * Returns the String representation of the connector state
     *
     * @param status Boolean synapse library status
     * @return status
     */
    private String getConnectorState(Boolean status) {

        if (status) {
            return "enabled";
        }
        return "disabled";
    }

    /**
     * Changes the state of the connector to the specified state in the json payload
     *
     * @param axis2MessageContext axis2 message context
     * @param payload             request json payload
     */
    private void changeConnectorState(org.apache.axis2.context.MessageContext axis2MessageContext,
                                      JsonObject payload, SynapseConfiguration synapseConfiguration) throws AxisFault {

        String connector = payload.get(NAME_ATTRIBUTE).getAsString();
        String packageName = payload.get(PACKAGE_ATTRIBUTE).getAsString();
        String state = payload.get(STATUS_ATTRIBUTE).getAsString();
        AxisConfiguration axisConfiguration = axis2MessageContext.getConfigurationContext().getAxisConfiguration();
        SynapseAppDeployer appDeployer = new SynapseAppDeployer();
        String qualifiedName = getQualifiedName(connector, packageName);
        Boolean updated = appDeployer.
                updateStatus(qualifiedName, connector, packageName, state, axisConfiguration);
        JSONObject jsonResponse = new JSONObject();
        if (updated) {
            persistStatus(axisConfiguration, synapseConfiguration, qualifiedName);
            jsonResponse.put("Message", "Status updated successfully");
        } else {
            jsonResponse.put("Message", "Status updated failed");
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonResponse);
    }

    /**
     * Return qualified name of the library
     *
     * @param libName     name of the synapse library
     * @param packageName package of the synapse library
     * @return qualified name
     */
    private String getQualifiedName(String libName, String packageName) {

        return ("{" + packageName.trim() + "}" + libName.trim()).toLowerCase();
    }

    /**
     * Persist the change state of the connector by writing to the import file
     *
     * @param axisConfiguration    axisconfiguration
     * @param synapseConfiguration synapseconfiguration
     * @param qualifiedName        fully qualified name of the library
     */
    private void persistStatus(AxisConfiguration axisConfiguration,
                               SynapseConfiguration synapseConfiguration, String qualifiedName) {
        SynapseImport synapseImport = synapseConfiguration.getSynapseImports().get(qualifiedName);
        MediationPersistenceManager mediationPersistenceManager = ServiceBusUtils.
                getMediationPersistenceManager(axisConfiguration);
        mediationPersistenceManager.saveItem(synapseImport.getName(), ITEM_TYPE_IMPORT);
    }
}
