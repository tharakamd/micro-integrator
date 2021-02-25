/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.initializer.serviceCatalog;

import org.wso2.carbon.securevault.SecretCallbackHandlerService;

import java.io.File;
import java.util.Map;

import static org.wso2.micro.integrator.initializer.utils.Constants.*;
import static org.wso2.micro.integrator.initializer.utils.ServiceCatalogUtils.*;

/**
 * This class will publish the metadata to the service catalog in a separate thread.
 */
public class ServiceCatalogExecutor implements Runnable {


    private static final String CAPP_UNZIP_DIR;

    private final String repoLocation;
    private final SecretCallbackHandlerService secretCallbackHandlerService;

    public ServiceCatalogExecutor(String repoLocation,
                                  SecretCallbackHandlerService secretCallbackHandlerService) {
        this.repoLocation = repoLocation;
        this.secretCallbackHandlerService = secretCallbackHandlerService;
    }

    static {
        String javaTmpDir = System.getProperty("java.io.tmpdir");
        CAPP_UNZIP_DIR = javaTmpDir.endsWith(File.separator) ? javaTmpDir + SERVICE_CATALOG :
                javaTmpDir + File.separator + SERVICE_CATALOG;
    }

    @Override
    public void run() {
        // check pre-conditions
        if (!checkPreConditions()) return;

        Map<String, String> apimConfigs = readConfiguration(secretCallbackHandlerService);

        // call service catalog and get all services
        Map<String, String> md5MapOfAllService = getAllServices(apimConfigs);
        if (md5MapOfAllService == null) return; // error occurred while getting all services

        // create temporary directory to hold metadata.
        if (!createTemporaryFolders(CAPP_UNZIP_DIR)) return;

        File tempDir = new File(CAPP_UNZIP_DIR, TEMP_FOLDER_NAME);

        // extract CAPPs and copy metadata to temp directory.
        if (!extractMetadataFromCAPPs(tempDir, repoLocation, md5MapOfAllService)) return;

        // create the payload.zip file with extracted metadata
        if (!archiveDir(CAPP_UNZIP_DIR + File.separator + ZIP_FOLDER_NAME, tempDir.getPath())) return;

        // publish to service catalog endpoint.
        publishToAPIM(apimConfigs, CAPP_UNZIP_DIR + File.separator + ZIP_FOLDER_NAME);
    }
}
