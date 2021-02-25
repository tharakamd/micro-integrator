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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.TemplateMediatorSerializer;
import org.apache.synapse.config.xml.endpoints.TemplateSerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.endpoints.Template;
import org.apache.synapse.mediators.template.TemplateMediator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents template resources defined in the synapse configuration.
 **/
public class TemplateResource extends APIResource {

    private static final Log LOG = LogFactory.getLog(TemplateResource.class);

    /* Name of the JSON list of endpoint templates */
    private static final String ENDPOINT_TEMPLATE_LIST = "endpointTemplateList";
    /* Name of the JSON list of sequence templates */
    private static final String SEQUENCE_TEMPLATE_LIST = "sequenceTemplateList";
    /* Template type parameter */
    private static final String TEMPLATE_TYPE_PARAM = "type";
    /* Template name parameter */
    private static final String TEMPLATE_NAME_PARAM = "name";
    /* Possible values for TEMPLATE_TYPE_PARAM  */
    private static final String ENDPOINT_TEMPLATE_TYPE = "endpoint";
    private static final String SEQUENCE_TEMPLATE_TYPE = "sequence";
    /* Name of the template parameter list */
    private static final String PARAMETERS = "Parameters";

    public TemplateResource(String urlTemplate) {
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_POST);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext msgCtx) {

        buildMessage(msgCtx);
        org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        String templateTypeParam = Utils.getQueryParameter(msgCtx, TEMPLATE_TYPE_PARAM);

        if (msgCtx.isDoingGET()) {
            if (Objects.nonNull(templateTypeParam)) {
                String templateNameParam = Utils.getQueryParameter(msgCtx, TEMPLATE_NAME_PARAM);
                if (Objects.nonNull(templateNameParam)) {
                    populateTemplateData(msgCtx, templateNameParam, templateTypeParam);
                } else {
                    populateTemplateListByType(msgCtx, templateTypeParam);
                }
            } else {
                populateFullTemplateList(axis2MsgCtx, msgCtx);
            }
        } else {
            JSONObject response;
            try {
                JsonObject payload = Utils.getJsonPayload(axis2MsgCtx);
                if (payload.has(TEMPLATE_TYPE_PARAM)) {
                    templateTypeParam = payload.get(TEMPLATE_TYPE_PARAM).getAsString();
                }
                if (payload.has(Constants.NAME) && SEQUENCE_TEMPLATE_TYPE.equals(templateTypeParam)) {
                    String seqTempName = payload.get(Constants.NAME).getAsString();
                    response = handleTracing(seqTempName, msgCtx, axis2MsgCtx);
                } else {
                    response = Utils.createJsonError("Unsupported operation", axis2MsgCtx, Constants.BAD_REQUEST);
                }
                Utils.setJsonPayLoad(axis2MsgCtx, response);
            } catch (IOException e) {
                LOG.error("Error when parsing JSON payload", e);
                Utils.setJsonPayLoad(axis2MsgCtx, Utils.createJsonErrorObject("Error when parsing JSON payload"));
            }
        }
        return true;
    }

    private JSONObject handleTracing(String seqTempName, MessageContext msgCtx,
                                     org.apache.axis2.context.MessageContext axisMsgCtx) {

        JSONObject response;
        SynapseConfiguration configuration = msgCtx.getConfiguration();
        TemplateMediator sequenceTemplate = configuration.getSequenceTemplate(seqTempName);
        if (sequenceTemplate != null) {
            response = Utils.handleTracing(sequenceTemplate.getAspectConfiguration(), seqTempName, axisMsgCtx);
        } else {
            response = Utils.createJsonError("Specified sequence template ('" + seqTempName + "') not found",
                    axisMsgCtx, Constants.BAD_REQUEST);
        }
        return response;
    }

    /**
     * Creates a response json with all templates available in the synapse configuration
     *
     * @param axis2MessageContext AXIS2 message context
     * @param messageContext      Synapse msg ctx
     */
    private void populateFullTemplateList(org.apache.axis2.context.MessageContext axis2MessageContext,
                                          MessageContext messageContext) {

        SynapseConfiguration synapseConfiguration = messageContext.getConfiguration();
        Map<String, Template> endpointTemplateMap = synapseConfiguration.getEndpointTemplates();
        Map<String, TemplateMediator> sequenceTemplateMap = synapseConfiguration.getSequenceTemplates();
        JSONObject jsonBody = new JSONObject();
        JSONArray endpointTemplateList = new JSONArray();
        JSONArray sequenceTemplateList = new JSONArray();
        jsonBody.put(ENDPOINT_TEMPLATE_LIST, endpointTemplateList);
        jsonBody.put(SEQUENCE_TEMPLATE_LIST, sequenceTemplateList);

        endpointTemplateMap.forEach((key, value) ->
                                            addToJsonList(jsonBody.getJSONArray(ENDPOINT_TEMPLATE_LIST),
                                                          value.getName()));
        sequenceTemplateMap.forEach((key, value) ->
                                            addToJsonList(jsonBody.getJSONArray(SEQUENCE_TEMPLATE_LIST),
                                                          value.getName()));
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    /**
     * Sets the list of templates of a given type to the Axis2MessageContext as a JSON object
     *
     * @param templateType   type of the template Ex: sequence, endpoint
     */
    private void populateTemplateListByType(MessageContext messageContext, String templateType) {
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        SynapseConfiguration synapseConfiguration = messageContext.getConfiguration();
        if (ENDPOINT_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Map<String, Template> endpointTemplateMap = synapseConfiguration.getEndpointTemplates();
            JSONObject jsonBody = Utils.createJSONList(endpointTemplateMap.size());
            endpointTemplateMap.forEach((key, value) ->
                    addToJsonList(jsonBody.getJSONArray(Constants.LIST), value.getName()));
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else if (SEQUENCE_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Map<String, TemplateMediator> sequenceTemplateMap = synapseConfiguration.getSequenceTemplates();
            JSONObject jsonBody = Utils.createJSONList(sequenceTemplateMap.size());
            sequenceTemplateMap.forEach((key, value) ->
                    addToJsonList(jsonBody.getJSONArray(Constants.LIST), value.getName()));
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            LOG.warn("Provided template type does not exist");
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonErrorObject("Received type " + templateType + " is not a valid template type"));
        }
    }

    /**
     * Sets the name of the template to the JSON Array
     *
     * @param jsonList body of the json object
     * @param name     template name
     */
    private void addToJsonList(JSONArray jsonList, String name) {
        //Create new JSONObject for the template resource
        JSONObject templateObject = new JSONObject();
        templateObject.put(Constants.NAME, name);
        //Add the template JSONObject into the JSON array on the parent object
        jsonList.put(templateObject);
    }

    /**
     * Set the information related to a specific template to the Axis2MessageContext as a JSON object
     *
     * @param templateName   name of the template
     * @param templateType   type of the template
     */
    private void populateTemplateData(MessageContext messageContext, String templateName, String templateType) {
        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        SynapseConfiguration synapseConfiguration = messageContext.getConfiguration();
        JSONObject templateObject = null;
        if (ENDPOINT_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            Template endpointTemplate = synapseConfiguration.getEndpointTemplate(templateName);
            if (Objects.nonNull(endpointTemplate)) {
                templateObject = getEndpointTemplateAsJson(endpointTemplate);
            }
        } else if (SEQUENCE_TEMPLATE_TYPE.equalsIgnoreCase(templateType)) {
            TemplateMediator sequenceTemplate = synapseConfiguration.getSequenceTemplate(templateName);
            if (Objects.nonNull(sequenceTemplate)) {
                templateObject = getSequenceTemplateAsJson(sequenceTemplate);
            }
        }
        if (Objects.nonNull(templateObject)) {
            Utils.setJsonPayLoad(axis2MessageContext, templateObject);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    /**
     * Returns the JSON representation of a endpoint template
     *
     * @param endpointTemplate
     */
    private JSONObject getEndpointTemplateAsJson(Template endpointTemplate) {
        JSONObject endpointTemplateJSONObject = new JSONObject();
        endpointTemplateJSONObject.put(Constants.NAME, endpointTemplate.getName());
        endpointTemplateJSONObject.put(PARAMETERS, endpointTemplate.getParameters());
        endpointTemplateJSONObject.put(Constants.SYNAPSE_CONFIGURATION, new TemplateSerializer().
                serializeEndpointTemplate(endpointTemplate, null));

        return endpointTemplateJSONObject;
    }

    /**
     * Returns the JSON representation of a sequence template
     *
     * @param sequenceTemplate
     */
    private JSONObject getSequenceTemplateAsJson(TemplateMediator sequenceTemplate) {
        JSONObject sequenceTemplateJSONObject = new JSONObject();
        sequenceTemplateJSONObject.put(Constants.NAME, sequenceTemplate.getName());
        sequenceTemplateJSONObject.put(PARAMETERS, sequenceTemplate.getParameters());
        sequenceTemplateJSONObject.put(Constants.SYNAPSE_CONFIGURATION, new TemplateMediatorSerializer().
                serializeMediator(null, sequenceTemplate));

        return sequenceTemplateJSONObject;
    }
}
